package com.c2java.service;

import com.c2java.domain.ConversionJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Airflow DAG 생성 서비스
 * 변환 작업을 Airflow 워크플로우로 정의
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AirflowDagService {

    @Value("${airflow.dags-dir:../airflow/dags}")
    private String dagsDir;
    
    @Value("${conversion.batch-size:10}")
    private int batchSize;
    
    @Value("${conversion.max-parallel-batches:3}")
    private int maxParallelBatches;

    /**
     * 변환 작업용 DAG 생성
     */
    public String createConversionDag(ConversionJob job) throws IOException {
        return createConversionDag(job, 0);
    }
    
    /**
     * 변환 작업용 DAG 생성 (파일 개수 지정)
     */
    public String createConversionDag(ConversionJob job, int fileCount) throws IOException {
        String dagId = "c2java_" + job.getJobId().replace("-", "_");
        
        // 배치 수 계산
        int totalBatches = fileCount > 0 ? (int) Math.ceil((double) fileCount / batchSize) : 1;
        
        log.info("Creating DAG with {} files, {} batches (size: {})", 
                 fileCount, totalBatches, batchSize);
        
        String dagContent = generateDagContent(job, dagId, totalBatches);
        
        // DAG 파일 저장
        Path dagsPath = Paths.get(dagsDir);
        if (!Files.exists(dagsPath)) {
            Files.createDirectories(dagsPath);
        }
        
        Path dagFile = dagsPath.resolve(dagId + ".py");
        Files.writeString(dagFile, dagContent);
        
        log.info("Created Airflow DAG: {} with {} batches", dagId, totalBatches);
        return dagId;
    }

    /**
     * DAG Python 코드 생성
     */
    private String generateDagContent(ConversionJob job, String dagId, int totalBatches) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        // 배치별 변환 태스크 생성
        StringBuilder batchTasks = new StringBuilder();
        StringBuilder batchDependencies = new StringBuilder();
        
        if (totalBatches > 1) {
            // 다중 배치 처리
            for (int i = 0; i < totalBatches; i++) {
                batchTasks.append(String.format("""
                        
                        t2_convert_batch_%d = PythonOperator(
                            task_id='convert_batch_%d',
                            python_callable=lambda **ctx: convert_code_batch(%d, **ctx),
                            dag=dag,
                        )
                        """, i, i, i));
            }
            
            // 병렬 실행 설정: analyze -> [batch_0, batch_1, ...] -> compile
            batchDependencies.append("t1_analyze >> [");
            for (int i = 0; i < totalBatches; i++) {
                if (i > 0) batchDependencies.append(", ");
                batchDependencies.append("t2_convert_batch_").append(i);
            }
            batchDependencies.append("] >> t3_compile >> t4_test >> t5_finalize");
        } else {
            // 단일 배치 또는 소량 파일 (기존 방식)
            batchDependencies.append("t1_analyze >> t2_convert >> t3_compile >> t4_test >> t5_finalize");
        }
        
        return String.format("""
                '''
                C2JAVA 변환 워크플로우 DAG
                생성일: %s
                작업 ID: %s
                대상 언어: %s
                '''
                from airflow import DAG
                from airflow.operators.python import PythonOperator
                from airflow.operators.bash import BashOperator
                from airflow.utils.dates import days_ago
                from datetime import timedelta
                import requests
                import json
                
                # 백엔드 API URL
                BACKEND_API = "http://localhost:8080/api"
                JOB_ID = "%s"
                
                # DAG 기본 설정
                default_args = {
                    'owner': 'c2java',
                    'depends_on_past': False,
                    'start_date': days_ago(0),
                    'email_on_failure': False,
                    'email_on_retry': False,
                    'retries': 1,
                    'retry_delay': timedelta(minutes=1),
                }
                
                dag = DAG(
                    '%s',
                    default_args=default_args,
                    description='C to Java 변환 파이프라인',
                    schedule_interval=None,  # 수동 트리거
                    catchup=False,
                    tags=['c2java', 'conversion'],
                )
                
                def update_job_status(stage, status, progress):
                    '''작업 상태 업데이트'''
                    try:
                        response = requests.put(
                            f"{BACKEND_API}/v1/conversions/{JOB_ID}/status",
                            json={
                                "stage": stage,
                                "status": status,
                                "progress": progress
                            }
                        )
                        print(f"Status updated: {stage} - {status} ({progress}%%)")
                    except Exception as e:
                        print(f"Failed to update status: {e}")
                
                def analyze_files(**context):
                    '''1단계: C 파일 분석'''
                    print("=" * 50)
                    print("1단계: C 파일 구조 분석 중...")
                    print("=" * 50)
                    update_job_status("ANALYZE", "ANALYZING", 10)
                    
                    # 백엔드 API 호출
                    response = requests.post(f"{BACKEND_API}/v1/conversions/{JOB_ID}/analyze")
                    result = response.json()
                    
                    print(f"분석 완료: 함수 {result.get('function_count')}개, "
                          f"구조체 {result.get('struct_count')}개, "
                          f"SQL {result.get('sql_count')}개")
                    
                    update_job_status("ANALYZE", "COMPLETED", 25)
                    return result
                
                def convert_code_batch(batch_index, **context):
                    '''2단계: Java 코드 변환 (배치 단위)'''
                    print("=" * 50)
                    print(f"배치 {batch_index} 변환 중...")
                    print("=" * 50)
                    
                    # 전체 진행률: 30 (분석 완료) + (batch_index * 30 / total_batches)
                    progress = 30 + int((batch_index / %d) * 30)
                    update_job_status("CONVERT", "CONVERTING", progress)
                    
                    # CLI 설정 확인
                    import os
                    cli_tool = os.environ.get('ACTIVE_CLI_TOOL', 'aider')
                    cursor_enabled = os.environ.get('CURSOR_CLI_ENABLED', 'false').lower() == 'true'
                    
                    print(f"활성 CLI 도구: {cli_tool}")
                    if cli_tool == 'cursor' and cursor_enabled:
                        print("Cursor CLI를 사용하여 변환합니다...")
                    else:
                        print(f"{cli_tool.upper()}를 사용하여 변환합니다...")
                    
                    # 백엔드 API 호출 (배치 인덱스 전달)
                    response = requests.post(
                        f"{BACKEND_API}/v1/conversions/{JOB_ID}/convert",
                        json={"batch_index": batch_index}
                    )
                    result = response.json()
                    
                    print(f"배치 {batch_index} 변환 완료: {result.get('generated_files', 0)}개 파일 생성")
                    print(f"사용된 CLI: {cli_tool}")
                    
                    # 마지막 배치인 경우 상태 업데이트
                    if batch_index == %d - 1:
                        update_job_status("CONVERT", "COMPLETED", 60)
                    
                    return result
                
                def convert_code(**context):
                    '''2단계: Java 코드 변환 (단일 배치 - 하위 호환성)'''
                    return convert_code_batch(0, **context)
                
                def compile_code(**context):
                    '''3단계: 컴파일 검증'''
                    print("=" * 50)
                    print("3단계: 컴파일 검증 중...")
                    print("=" * 50)
                    update_job_status("COMPILE", "COMPILING", 65)
                    
                    response = requests.post(f"{BACKEND_API}/v1/conversions/{JOB_ID}/compile")
                    result = response.json()
                    
                    if result.get('success'):
                        print("컴파일 성공!")
                        update_job_status("COMPILE", "COMPLETED", 80)
                    else:
                        print(f"컴파일 실패: {result.get('errors')}")
                        raise Exception("Compile failed")
                    
                    return result
                
                def run_tests(**context):
                    '''4단계: 런타임 테스트'''
                    print("=" * 50)
                    print("4단계: 테스트 실행 중...")
                    print("=" * 50)
                    update_job_status("TEST", "TESTING", 85)
                    
                    response = requests.post(f"{BACKEND_API}/v1/conversions/{JOB_ID}/test")
                    result = response.json()
                    
                    print(f"테스트 결과: {result.get('passed')}/{result.get('total')} 통과")
                    update_job_status("TEST", "COMPLETED", 95)
                    return result
                
                def finalize_job(**context):
                    '''5단계: 작업 완료 처리'''
                    print("=" * 50)
                    print("변환 파이프라인 완료!")
                    print("=" * 50)
                    update_job_status("COMPLETE", "COMPLETED", 100)
                
                # Task 정의
                t1_analyze = PythonOperator(
                    task_id='analyze_c_files',
                    python_callable=analyze_files,
                    dag=dag,
                )
                
                %s
                
                t2_convert = PythonOperator(
                    task_id='convert_to_java',
                    python_callable=convert_code,
                    dag=dag,
                )
                
                t3_compile = PythonOperator(
                    task_id='compile_java',
                    python_callable=compile_code,
                    dag=dag,
                )
                
                t4_test = PythonOperator(
                    task_id='run_tests',
                    python_callable=run_tests,
                    dag=dag,
                )
                
                t5_finalize = PythonOperator(
                    task_id='finalize',
                    python_callable=finalize_job,
                    dag=dag,
                )
                
                # 의존성 설정
                %s
                """,
                timestamp,
                job.getJobId(),
                job.getTargetLanguage(),
                job.getJobId(),
                dagId,
                totalBatches,  // convert_code_batch 함수의 진행률 계산
                totalBatches,  // convert_code_batch 함수의 마지막 배치 확인
                batchTasks.toString(),  // 배치별 태스크 정의
                batchDependencies.toString());  // 의존성 설정
    }
}
