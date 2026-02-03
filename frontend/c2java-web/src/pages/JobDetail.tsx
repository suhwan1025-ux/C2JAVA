import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { 
  CheckCircle, 
  XCircle, 
  Clock, 
  Loader,
  FileCode,
  Download,
  StopCircle,
  Terminal
} from 'lucide-react';
import { api } from '../lib/api';
import { useState, useEffect, useRef } from 'react';

const statusSteps = [
  { key: 'PENDING', label: '대기' },
  { key: 'ANALYZING', label: '분석' },
  { key: 'CONVERTING', label: '변환' },
  { key: 'COMPILING', label: '컴파일' },
  { key: 'TESTING', label: '테스트' },
  { key: 'REVIEWING', label: '리뷰' },
  { key: 'COMPLETED', label: '완료' },
];

export default function JobDetail() {
  const { jobId } = useParams<{ jobId: string }>();
  const queryClient = useQueryClient();
  const [logs, setLogs] = useState<string>('');
  const [isStreaming, setIsStreaming] = useState(false);
  const eventSourceRef = useRef<EventSource | null>(null);
  const logsEndRef = useRef<HTMLDivElement>(null);
  
  const { data: job, isLoading } = useQuery({
    queryKey: ['job', jobId],
    queryFn: () => api.getJobStatus(jobId!),
    refetchInterval: (query) => 
      query.state.data?.status === 'COMPLETED' || query.state.data?.status === 'FAILED' || query.state.data?.status === 'CANCELLED' ? false : 3000,
  });
  
  // 작업 취소 뮤테이션
  const cancelMutation = useMutation({
    mutationFn: () => api.cancelJob(jobId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['job', jobId] });
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
    },
  });
  
  // 실시간 로그 스트리밍
  useEffect(() => {
    if (!jobId || !job) return;
    
    // 완료/실패/취소된 작업은 스트리밍 중단
    if (job.status === 'COMPLETED' || job.status === 'FAILED' || job.status === 'CANCELLED') {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      setIsStreaming(false);
      return;
    }
    
    // 이미 스트리밍 중이면 중복 방지
    if (eventSourceRef.current) return;
    
    const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
    const authData = localStorage.getItem('c2java-auth');
    let token = '';
    
    if (authData) {
      try {
        const { state } = JSON.parse(authData);
        token = state?.token || '';
      } catch (e) {
        console.error('Failed to parse auth data', e);
      }
    }
    
    const url = `${API_BASE_URL}/v1/conversions/${jobId}/logs/stream`;
    const eventSource = new EventSource(url);
    eventSourceRef.current = eventSource;
    setIsStreaming(true);
    
    eventSource.addEventListener('log', (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.log) {
          setLogs(data.log);
        }
      } catch (e) {
        console.error('Failed to parse log event', e);
      }
    });
    
    eventSource.addEventListener('done', (event) => {
      console.log('Log stream completed', event.data);
      eventSource.close();
      eventSourceRef.current = null;
      setIsStreaming(false);
    });
    
    eventSource.addEventListener('error', (event) => {
      console.error('Log stream error', event);
      eventSource.close();
      eventSourceRef.current = null;
      setIsStreaming(false);
    });
    
    eventSource.onerror = (error) => {
      console.error('EventSource error', error);
      eventSource.close();
      eventSourceRef.current = null;
      setIsStreaming(false);
    };
    
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
    };
  }, [jobId, job?.status]);
  
  // 로그 자동 스크롤
  useEffect(() => {
    if (logsEndRef.current) {
      logsEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs]);
  
  const handleCancel = () => {
    if (window.confirm('정말 이 작업을 취소하시겠습니까?')) {
      cancelMutation.mutate();
    }
  };

  if (isLoading) {
    return <div className="animate-pulse">로딩 중...</div>;
  }

  if (!job) {
    return <div>작업을 찾을 수 없습니다.</div>;
  }

  const currentStepIndex = statusSteps.findIndex(s => s.key === job.status);
  const isFailed = job.status === 'FAILED';
  const isInProgress = ['PENDING', 'ANALYZING', 'CONVERTING', 'COMPILING', 'TESTING', 'REVIEWING'].includes(job.status);
  const isCancelled = job.status === 'CANCELLED';

  return (
    <div>
      <div className="flex justify-between items-start mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {job.jobName}
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            ID: {job.id}
          </p>
        </div>
        
        <div className="flex gap-2">
          {job.status === 'COMPLETED' && (
            <button
              className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-green-600 hover:bg-green-700"
            >
              <Download className="h-4 w-4 mr-2" />
              결과 다운로드
            </button>
          )}
          {isInProgress && (
            <button
              onClick={handleCancel}
              disabled={cancelMutation.isPending}
              className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-red-600 hover:bg-red-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
            >
              <StopCircle className="h-4 w-4 mr-2" />
              {cancelMutation.isPending ? '취소 중...' : '작업 취소'}
            </button>
          )}
        </div>
      </div>

      {/* 진행 상태 */}
      <div className="bg-white shadow rounded-lg p-6 mb-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">
          변환 진행 상태
        </h2>
        
        <div className="flex items-center justify-between">
          {statusSteps.map((step, index) => {
            const isComplete = index < currentStepIndex || 
              (index === currentStepIndex && job.status === 'COMPLETED');
            const isCurrent = index === currentStepIndex && job.status !== 'COMPLETED';
            const isError = isCurrent && isFailed;
            
            return (
              <div key={step.key} className="flex flex-col items-center flex-1">
                <div className="relative">
                  {/* 연결선 */}
                  {index > 0 && (
                    <div 
                      className={`absolute right-1/2 top-1/2 h-0.5 w-full -translate-y-1/2 ${
                        isComplete ? 'bg-green-500' : 'bg-gray-200'
                      }`}
                      style={{ width: '100px', right: '50%' }}
                    />
                  )}
                  
                  {/* 아이콘 */}
                  <div className={`
                    relative z-10 w-10 h-10 rounded-full flex items-center justify-center
                    ${isError ? 'bg-red-500' : isComplete ? 'bg-green-500' : isCurrent ? 'bg-indigo-500' : 'bg-gray-200'}
                  `}>
                    {isError ? (
                      <XCircle className="h-6 w-6 text-white" />
                    ) : isComplete ? (
                      <CheckCircle className="h-6 w-6 text-white" />
                    ) : isCurrent ? (
                      <Loader className="h-6 w-6 text-white animate-spin" />
                    ) : (
                      <Clock className="h-6 w-6 text-gray-400" />
                    )}
                  </div>
                </div>
                <span className={`mt-2 text-xs ${
                  isCurrent ? 'text-indigo-600 font-medium' : 'text-gray-500'
                }`}>
                  {step.label}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* 상세 정보 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 기본 정보 */}
        <div className="bg-white shadow rounded-lg p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">
            기본 정보
          </h2>
          <dl className="space-y-3">
            <div className="flex justify-between">
              <dt className="text-sm text-gray-500">LLM 제공자</dt>
              <dd className="text-sm font-medium text-gray-900">
                {job.llmProvider || 'qwen3'}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-sm text-gray-500">재시도 횟수</dt>
              <dd className="text-sm font-medium text-gray-900">
                {job.retryCount}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-sm text-gray-500">생성 시간</dt>
              <dd className="text-sm font-medium text-gray-900">
                {new Date(job.createdAt).toLocaleString('ko-KR')}
              </dd>
            </div>
            {job.startedAt && (
              <div className="flex justify-between">
                <dt className="text-sm text-gray-500">시작 시간</dt>
                <dd className="text-sm font-medium text-gray-900">
                  {new Date(job.startedAt).toLocaleString('ko-KR')}
                </dd>
              </div>
            )}
            {job.completedAt && (
              <div className="flex justify-between">
                <dt className="text-sm text-gray-500">완료 시간</dt>
                <dd className="text-sm font-medium text-gray-900">
                  {new Date(job.completedAt).toLocaleString('ko-KR')}
                </dd>
              </div>
            )}
          </dl>
        </div>

        {/* 파일 정보 */}
        <div className="bg-white shadow rounded-lg p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">
            파일 정보
          </h2>
          <dl className="space-y-3">
            <div>
              <dt className="text-sm text-gray-500">원본 파일</dt>
              <dd className="text-sm font-medium text-gray-900 flex items-center mt-1">
                <FileCode className="h-4 w-4 mr-2 text-gray-400" />
                {job.sourceFilePath?.split('/').pop()}
              </dd>
            </div>
            {job.outputFilePath && (
              <div>
                <dt className="text-sm text-gray-500">출력 경로</dt>
                <dd className="text-sm font-medium text-gray-900 flex items-center mt-1">
                  <FileCode className="h-4 w-4 mr-2 text-gray-400" />
                  {job.outputFilePath}
                </dd>
              </div>
            )}
          </dl>
        </div>
      </div>

      {/* 실시간 로그 */}
      {(isInProgress || logs) && (
        <div className="mt-6 bg-gray-900 rounded-lg p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-white flex items-center">
              <Terminal className="h-5 w-5 mr-2" />
              실행 로그
            </h2>
            {isStreaming && (
              <span className="inline-flex items-center text-xs text-green-400">
                <span className="animate-pulse mr-2">●</span>
                실시간 스트리밍 중
              </span>
            )}
          </div>
          <div className="bg-black rounded p-4 h-96 overflow-y-auto font-mono text-sm text-green-400">
            {logs || job.executionLog || '로그를 불러오는 중...'}
            <div ref={logsEndRef} />
          </div>
        </div>
      )}

      {/* 에러 메시지 */}
      {job.errorMessage && (
        <div className="mt-6 bg-red-50 border border-red-200 rounded-lg p-6">
          <h2 className="text-lg font-semibold text-red-800 mb-2">
            오류 정보
          </h2>
          <pre className="text-sm text-red-700 whitespace-pre-wrap overflow-x-auto">
            {job.errorMessage}
          </pre>
        </div>
      )}
      
      {/* 취소 메시지 */}
      {isCancelled && (
        <div className="mt-6 bg-yellow-50 border border-yellow-200 rounded-lg p-6">
          <h2 className="text-lg font-semibold text-yellow-800 mb-2">
            작업 취소됨
          </h2>
          <p className="text-sm text-yellow-700">
            이 작업은 사용자에 의해 취소되었습니다.
          </p>
        </div>
      )}
    </div>
  );
}
