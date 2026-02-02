"""
C2JAVA 변환 배치 처리 DAG
Apache Airflow를 사용한 배치 변환 작업 관리
"""

from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.bash import BashOperator
from airflow.providers.http.operators.http import SimpleHttpOperator
from airflow.providers.postgres.operators.postgres import PostgresOperator
from airflow.providers.postgres.hooks.postgres import PostgresHook
from airflow.utils.dates import days_ago
import json
import requests

# DAG 기본 설정
default_args = {
    'owner': 'c2java',
    'depends_on_past': False,
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 3,
    'retry_delay': timedelta(minutes=5),
}

# ============================================
# 배치 변환 DAG
# ============================================
with DAG(
    'c2java_batch_conversion',
    default_args=default_args,
    description='C to Java 배치 변환 작업',
    schedule_interval=timedelta(hours=1),  # 매시간 실행
    start_date=days_ago(1),
    catchup=False,
    tags=['c2java', 'conversion', 'batch'],
) as batch_dag:

    def get_pending_jobs(**context):
        """대기 중인 변환 작업 조회"""
        hook = PostgresHook(postgres_conn_id='c2java_postgres')
        sql = """
            SELECT id, job_name, source_file_path, llm_provider
            FROM conversion_jobs
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT 10
        """
        records = hook.get_records(sql)
        context['ti'].xcom_push(key='pending_jobs', value=records)
        return len(records)

    def trigger_conversion(**context):
        """변환 작업 트리거"""
        ti = context['ti']
        pending_jobs = ti.xcom_pull(key='pending_jobs', task_ids='get_pending_jobs')
        
        api_url = "http://c2java-backend:8080/api/v1/conversions"
        
        for job in pending_jobs:
            job_id, job_name, source_path, llm_provider = job
            try:
                # 변환 시작 API 호출
                response = requests.post(
                    f"{api_url}/{job_id}/start",
                    timeout=30
                )
                print(f"Started conversion for job {job_id}: {response.status_code}")
            except Exception as e:
                print(f"Failed to start conversion for job {job_id}: {e}")

    def update_statistics(**context):
        """통계 업데이트"""
        hook = PostgresHook(postgres_conn_id='c2java_postgres')
        
        # 일별 통계 계산 및 저장
        sql = """
            INSERT INTO daily_statistics (
                stat_date,
                total_jobs,
                completed_jobs,
                failed_jobs,
                avg_conversion_time_ms
            )
            SELECT 
                CURRENT_DATE,
                COUNT(*),
                COUNT(*) FILTER (WHERE status = 'COMPLETED'),
                COUNT(*) FILTER (WHERE status = 'FAILED'),
                AVG(EXTRACT(EPOCH FROM (completed_at - started_at)) * 1000)
                    FILTER (WHERE status = 'COMPLETED')
            FROM conversion_jobs
            WHERE DATE(created_at) = CURRENT_DATE
            ON CONFLICT (stat_date) DO UPDATE SET
                total_jobs = EXCLUDED.total_jobs,
                completed_jobs = EXCLUDED.completed_jobs,
                failed_jobs = EXCLUDED.failed_jobs,
                avg_conversion_time_ms = EXCLUDED.avg_conversion_time_ms
        """
        hook.run(sql)

    # 태스크 정의
    get_pending = PythonOperator(
        task_id='get_pending_jobs',
        python_callable=get_pending_jobs,
    )

    trigger = PythonOperator(
        task_id='trigger_conversion',
        python_callable=trigger_conversion,
    )

    update_stats = PythonOperator(
        task_id='update_statistics',
        python_callable=update_statistics,
    )

    # 의존성 설정
    get_pending >> trigger >> update_stats


# ============================================
# 모니터링 DAG
# ============================================
with DAG(
    'c2java_monitoring',
    default_args=default_args,
    description='C2JAVA 시스템 모니터링',
    schedule_interval=timedelta(minutes=5),  # 5분마다 실행
    start_date=days_ago(1),
    catchup=False,
    tags=['c2java', 'monitoring'],
) as monitoring_dag:

    def check_stuck_jobs(**context):
        """stuck 상태 작업 확인"""
        hook = PostgresHook(postgres_conn_id='c2java_postgres')
        
        # 30분 이상 진행 중인 작업 확인
        sql = """
            SELECT id, job_name, status, started_at
            FROM conversion_jobs
            WHERE status IN ('ANALYZING', 'CONVERTING', 'COMPILING', 'TESTING')
            AND started_at < NOW() - INTERVAL '30 minutes'
        """
        stuck_jobs = hook.get_records(sql)
        
        if stuck_jobs:
            print(f"Found {len(stuck_jobs)} stuck jobs")
            # 알림 로직 추가 가능
            context['ti'].xcom_push(key='stuck_jobs', value=stuck_jobs)
        
        return len(stuck_jobs)

    def check_system_health(**context):
        """시스템 상태 확인"""
        try:
            response = requests.get(
                "http://c2java-backend:8080/api/v1/admin/status",
                timeout=10
            )
            if response.status_code == 200:
                status = response.json()
                print(f"System status: {status}")
                return True
            else:
                print(f"System unhealthy: {response.status_code}")
                return False
        except Exception as e:
            print(f"Health check failed: {e}")
            return False

    def collect_metrics(**context):
        """메트릭 수집"""
        hook = PostgresHook(postgres_conn_id='c2java_postgres')
        
        # 현재 상태별 작업 수
        sql = """
            SELECT status, COUNT(*)
            FROM conversion_jobs
            GROUP BY status
        """
        metrics = hook.get_records(sql)
        
        # Prometheus 포맷으로 메트릭 기록
        for status, count in metrics:
            print(f"c2java_jobs_total{{status=\"{status}\"}} {count}")
        
        return metrics

    # 태스크 정의
    check_stuck = PythonOperator(
        task_id='check_stuck_jobs',
        python_callable=check_stuck_jobs,
    )

    health_check = PythonOperator(
        task_id='check_system_health',
        python_callable=check_system_health,
    )

    metrics = PythonOperator(
        task_id='collect_metrics',
        python_callable=collect_metrics,
    )

    # 병렬 실행
    [check_stuck, health_check, metrics]


# ============================================
# 정리 DAG
# ============================================
with DAG(
    'c2java_cleanup',
    default_args=default_args,
    description='오래된 데이터 정리',
    schedule_interval='0 2 * * *',  # 매일 새벽 2시
    start_date=days_ago(1),
    catchup=False,
    tags=['c2java', 'cleanup', 'maintenance'],
) as cleanup_dag:

    def cleanup_old_files(**context):
        """오래된 파일 정리"""
        import os
        import shutil
        from datetime import datetime, timedelta
        
        workspace_dir = os.environ.get('CONVERSION_WORKSPACE', '/app/workspace')
        output_dir = os.environ.get('OUTPUT_DIR', '/app/output')
        retention_days = 30
        
        cutoff_date = datetime.now() - timedelta(days=retention_days)
        
        for directory in [workspace_dir, output_dir]:
            if not os.path.exists(directory):
                continue
                
            for item in os.listdir(directory):
                item_path = os.path.join(directory, item)
                if os.path.isdir(item_path):
                    mod_time = datetime.fromtimestamp(os.path.getmtime(item_path))
                    if mod_time < cutoff_date:
                        print(f"Removing old directory: {item_path}")
                        shutil.rmtree(item_path)

    cleanup_old_logs = PostgresOperator(
        task_id='cleanup_old_logs',
        postgres_conn_id='c2java_postgres',
        sql="""
            DELETE FROM conversion_logs
            WHERE created_at < NOW() - INTERVAL '90 days';
            
            DELETE FROM compile_results
            WHERE created_at < NOW() - INTERVAL '90 days';
            
            DELETE FROM runtime_results
            WHERE created_at < NOW() - INTERVAL '90 days';
        """,
    )

    cleanup_files = PythonOperator(
        task_id='cleanup_old_files',
        python_callable=cleanup_old_files,
    )

    vacuum_db = PostgresOperator(
        task_id='vacuum_database',
        postgres_conn_id='c2java_postgres',
        sql="VACUUM ANALYZE;",
    )

    # 순차 실행
    cleanup_old_logs >> cleanup_files >> vacuum_db
