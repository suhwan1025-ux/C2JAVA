import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { 
  ArrowLeft, Activity, CheckCircle, XCircle, Clock, AlertCircle,
  FileCode, Cog, Hammer, TestTube, CheckSquare, RefreshCw
} from 'lucide-react';
import { api } from '../lib/api';

interface JobStatus {
  jobId: string;
  jobName: string;
  status: string;
  currentStage: string;
  progress: number;
  targetLanguage: string;
  functionCount?: number;
  structCount?: number;
  sqlCount?: number;
  generatedFileCount?: number;
  compileSuccess?: boolean;
  testSuccess?: boolean;
  airflowStatus?: {
    state: string;
    start_date: string;
    end_date?: string;
  };
  tasks?: Array<{
    task_id: string;
    state: string;
    start_date: string;
    end_date?: string;
    duration?: number;
  }>;
}

const PIPELINE_STAGES = [
  { id: 'UPLOAD', label: '파일 업로드', icon: FileCode, color: 'blue' },
  { id: 'ANALYZE', label: '구조 분석', icon: Activity, color: 'purple' },
  { id: 'CONVERT', label: 'Java 변환', icon: Cog, color: 'indigo' },
  { id: 'COMPILE', label: '컴파일', icon: Hammer, color: 'orange' },
  { id: 'TEST', label: '테스트', icon: TestTube, color: 'pink' },
  { id: 'COMPLETE', label: '완료', icon: CheckSquare, color: 'green' },
];

const STATE_STYLES = {
  success: 'bg-green-100 text-green-800 border-green-300',
  running: 'bg-blue-100 text-blue-800 border-blue-300 animate-pulse',
  failed: 'bg-red-100 text-red-800 border-red-300',
  pending: 'bg-gray-100 text-gray-600 border-gray-300',
  skipped: 'bg-yellow-100 text-yellow-800 border-yellow-300',
};

export default function JobMonitor() {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const [autoRefresh, setAutoRefresh] = useState(true);

  // 작업 상태 조회 (3초마다 자동 갱신)
  const { data: jobStatus, isLoading, refetch } = useQuery<JobStatus>({
    queryKey: ['jobStatus', jobId],
    queryFn: () => api.getDetailedJobStatus(jobId!),
    refetchInterval: autoRefresh ? 3000 : false,
    enabled: !!jobId,
  });

  // 완료되면 자동 갱신 중지
  useEffect(() => {
    if (jobStatus?.status === 'COMPLETED' || jobStatus?.status === 'FAILED') {
      setAutoRefresh(false);
    }
  }, [jobStatus?.status]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <RefreshCw className="h-8 w-8 animate-spin text-indigo-500" />
      </div>
    );
  }

  if (!jobStatus) {
    return (
      <div className="text-center py-12">
        <AlertCircle className="h-16 w-16 text-gray-400 mx-auto mb-4" />
        <p className="text-gray-600">작업을 찾을 수 없습니다.</p>
      </div>
    );
  }

  const getCurrentStageIndex = () => {
    return PIPELINE_STAGES.findIndex(s => s.id === jobStatus.currentStage);
  };

  const getStageState = (stageIndex: number) => {
    const currentIndex = getCurrentStageIndex();
    if (stageIndex < currentIndex) return 'success';
    if (stageIndex === currentIndex) {
      if (jobStatus.status === 'FAILED') return 'failed';
      return 'running';
    }
    return 'pending';
  };

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/jobs')}
            className="p-2 hover:bg-gray-100 rounded-lg"
          >
            <ArrowLeft className="h-5 w-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{jobStatus.jobName}</h1>
            <p className="text-sm text-gray-500">작업 ID: {jobStatus.jobId}</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
              className="rounded"
            />
            자동 새로고침
          </label>
          <button
            onClick={() => refetch()}
            className="px-4 py-2 border rounded-lg hover:bg-gray-50 flex items-center gap-2"
          >
            <RefreshCw className="h-4 w-4" />
            새로고침
          </button>
        </div>
      </div>

      {/* 진행 상태 바 */}
      <div className="bg-white shadow rounded-lg p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold">변환 파이프라인</h2>
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold text-indigo-600">{jobStatus.progress}%</span>
            {jobStatus.status === 'COMPLETED' && (
              <CheckCircle className="h-6 w-6 text-green-500" />
            )}
            {jobStatus.status === 'FAILED' && (
              <XCircle className="h-6 w-6 text-red-500" />
            )}
          </div>
        </div>

        {/* 진행률 바 */}
        <div className="w-full bg-gray-200 rounded-full h-3 mb-6">
          <div
            className={`h-3 rounded-full transition-all duration-500 ${
              jobStatus.status === 'FAILED' ? 'bg-red-500' : 'bg-indigo-600'
            }`}
            style={{ width: `${jobStatus.progress}%` }}
          />
        </div>

        {/* 단계별 상태 */}
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
          {PIPELINE_STAGES.map((stage, index) => {
            const state = getStageState(index);
            const Icon = stage.icon;
            const isActive = stage.id === jobStatus.currentStage;

            return (
              <div
                key={stage.id}
                className={`p-4 rounded-lg border-2 transition-all ${
                  STATE_STYLES[state as keyof typeof STATE_STYLES]
                } ${isActive ? 'ring-2 ring-offset-2 ring-indigo-500' : ''}`}
              >
                <Icon className={`h-6 w-6 mb-2 ${isActive ? 'animate-bounce' : ''}`} />
                <p className="text-xs font-medium">{stage.label}</p>
                {isActive && (
                  <p className="text-xs mt-1 opacity-75">진행중...</p>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* Airflow 배치 상태 */}
      {jobStatus.airflowStatus && (
        <div className="bg-white shadow rounded-lg p-6">
          <h2 className="text-lg font-semibold mb-4">Airflow 배치 상태</h2>
          
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
            <div className="p-4 bg-gray-50 rounded-lg">
              <p className="text-xs text-gray-500">DAG 상태</p>
              <p className={`text-lg font-semibold ${
                jobStatus.airflowStatus.state === 'success' ? 'text-green-600' :
                jobStatus.airflowStatus.state === 'running' ? 'text-blue-600' :
                jobStatus.airflowStatus.state === 'failed' ? 'text-red-600' :
                'text-gray-600'
              }`}>
                {jobStatus.airflowStatus.state?.toUpperCase() || 'UNKNOWN'}
              </p>
            </div>
            <div className="p-4 bg-gray-50 rounded-lg">
              <p className="text-xs text-gray-500">시작 시간</p>
              <p className="text-sm font-medium">
                {new Date(jobStatus.airflowStatus.start_date).toLocaleString('ko-KR')}
              </p>
            </div>
            {jobStatus.airflowStatus.end_date && (
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-xs text-gray-500">종료 시간</p>
                <p className="text-sm font-medium">
                  {new Date(jobStatus.airflowStatus.end_date).toLocaleString('ko-KR')}
                </p>
              </div>
            )}
          </div>

          {/* Task 상태 */}
          {jobStatus.tasks && jobStatus.tasks.length > 0 && (
            <div>
              <h3 className="font-medium mb-3">Task 실행 상태</h3>
              <div className="space-y-2">
                {jobStatus.tasks.map((task: any) => (
                  <div
                    key={task.task_id}
                    className="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
                  >
                    <div className="flex items-center gap-3">
                      {task.state === 'success' && <CheckCircle className="h-5 w-5 text-green-500" />}
                      {task.state === 'running' && <Clock className="h-5 w-5 text-blue-500 animate-spin" />}
                      {task.state === 'failed' && <XCircle className="h-5 w-5 text-red-500" />}
                      {task.state === 'queued' && <Clock className="h-5 w-5 text-gray-400" />}
                      
                      <div>
                        <p className="font-medium text-sm">{task.task_id}</p>
                        {task.start_date && (
                          <p className="text-xs text-gray-500">
                            {new Date(task.start_date).toLocaleTimeString('ko-KR')}
                          </p>
                        )}
                      </div>
                    </div>
                    <div className="text-right">
                      <span className={`text-xs px-2 py-1 rounded ${
                        task.state === 'success' ? 'bg-green-100 text-green-700' :
                        task.state === 'running' ? 'bg-blue-100 text-blue-700' :
                        task.state === 'failed' ? 'bg-red-100 text-red-700' :
                        'bg-gray-200 text-gray-600'
                      }`}>
                        {task.state}
                      </span>
                      {task.duration && (
                        <p className="text-xs text-gray-500 mt-1">{task.duration}s</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* 분석 결과 */}
      {(jobStatus.functionCount || jobStatus.structCount || jobStatus.sqlCount) && (
        <div className="bg-white shadow rounded-lg p-6">
          <h2 className="text-lg font-semibold mb-4">분석 결과</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="p-4 bg-blue-50 rounded-lg">
              <p className="text-xs text-blue-600 mb-1">함수</p>
              <p className="text-2xl font-bold text-blue-700">{jobStatus.functionCount || 0}</p>
            </div>
            <div className="p-4 bg-purple-50 rounded-lg">
              <p className="text-xs text-purple-600 mb-1">구조체</p>
              <p className="text-2xl font-bold text-purple-700">{jobStatus.structCount || 0}</p>
            </div>
            <div className="p-4 bg-green-50 rounded-lg">
              <p className="text-xs text-green-600 mb-1">SQL</p>
              <p className="text-2xl font-bold text-green-700">{jobStatus.sqlCount || 0}</p>
            </div>
            <div className="p-4 bg-indigo-50 rounded-lg">
              <p className="text-xs text-indigo-600 mb-1">생성 파일</p>
              <p className="text-2xl font-bold text-indigo-700">{jobStatus.generatedFileCount || 0}</p>
            </div>
          </div>
        </div>
      )}

      {/* 검증 결과 */}
      {(jobStatus.compileSuccess !== undefined || jobStatus.testSuccess !== undefined) && (
        <div className="bg-white shadow rounded-lg p-6">
          <h2 className="text-lg font-semibold mb-4">검증 결과</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {jobStatus.compileSuccess !== undefined && (
              <div className={`p-4 rounded-lg border-2 ${
                jobStatus.compileSuccess 
                  ? 'bg-green-50 border-green-200' 
                  : 'bg-red-50 border-red-200'
              }`}>
                <div className="flex items-center gap-2 mb-2">
                  {jobStatus.compileSuccess ? (
                    <CheckCircle className="h-5 w-5 text-green-600" />
                  ) : (
                    <XCircle className="h-5 w-5 text-red-600" />
                  )}
                  <span className="font-medium">컴파일</span>
                </div>
                <p className={`text-sm ${
                  jobStatus.compileSuccess ? 'text-green-700' : 'text-red-700'
                }`}>
                  {jobStatus.compileSuccess ? '성공' : '실패'}
                </p>
              </div>
            )}

            {jobStatus.testSuccess !== undefined && (
              <div className={`p-4 rounded-lg border-2 ${
                jobStatus.testSuccess 
                  ? 'bg-green-50 border-green-200' 
                  : 'bg-red-50 border-red-200'
              }`}>
                <div className="flex items-center gap-2 mb-2">
                  {jobStatus.testSuccess ? (
                    <CheckCircle className="h-5 w-5 text-green-600" />
                  ) : (
                    <XCircle className="h-5 w-5 text-red-600" />
                  )}
                  <span className="font-medium">테스트</span>
                </div>
                <p className={`text-sm ${
                  jobStatus.testSuccess ? 'text-green-700' : 'text-red-700'
                }`}>
                  {jobStatus.testSuccess ? '통과' : '실패'}
                </p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 작업 정보 */}
      <div className="bg-white shadow rounded-lg p-6">
        <h2 className="text-lg font-semibold mb-4">작업 정보</h2>
        <dl className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <dt className="text-sm text-gray-500">대상 언어</dt>
            <dd className="font-medium">{jobStatus.targetLanguage}</dd>
          </div>
          <div>
            <dt className="text-sm text-gray-500">상태</dt>
            <dd>
              <span className={`px-3 py-1 rounded-full text-sm font-medium ${
                jobStatus.status === 'COMPLETED' ? 'bg-green-100 text-green-800' :
                jobStatus.status === 'FAILED' ? 'bg-red-100 text-red-800' :
                'bg-blue-100 text-blue-800'
              }`}>
                {jobStatus.status}
              </span>
            </dd>
          </div>
        </dl>
      </div>
    </div>
  );
}
