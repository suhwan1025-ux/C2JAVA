import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { 
  CheckCircle, 
  XCircle, 
  Clock, 
  Loader,
  Eye,
  Activity
} from 'lucide-react';
import { api } from '../lib/api';

const statusConfig: Record<string, { label: string; color: string; icon: any }> = {
  PENDING: { label: '대기', color: 'bg-gray-100 text-gray-800', icon: Clock },
  ANALYZING: { label: '분석 중', color: 'bg-blue-100 text-blue-800', icon: Loader },
  CONVERTING: { label: '변환 중', color: 'bg-indigo-100 text-indigo-800', icon: Loader },
  COMPILING: { label: '컴파일 중', color: 'bg-purple-100 text-purple-800', icon: Loader },
  TESTING: { label: '테스트 중', color: 'bg-yellow-100 text-yellow-800', icon: Loader },
  REVIEWING: { label: '리뷰 중', color: 'bg-orange-100 text-orange-800', icon: Loader },
  COMPLETED: { label: '완료', color: 'bg-green-100 text-green-800', icon: CheckCircle },
  FAILED: { label: '실패', color: 'bg-red-100 text-red-800', icon: XCircle },
};

export default function Jobs() {
  const { data: jobs, isLoading } = useQuery({
    queryKey: ['jobs'],
    queryFn: () => api.getAllJobs(),
    refetchInterval: 5000,
  });

  if (isLoading) {
    return <div className="animate-pulse">로딩 중...</div>;
  }

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          작업 목록
        </h1>
        <Link
          to="/upload"
          className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700"
        >
          새 변환 시작
        </Link>
      </div>

      <div className="bg-white shadow overflow-hidden sm:rounded-lg">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                작업명
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                상태
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                진행률
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                대상 언어
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                생성일
              </th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                액션
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {jobs?.map((job: any) => {
              const status = statusConfig[job.status] || statusConfig.PENDING;
              const StatusIcon = status.icon;
              
              return (
                <tr key={job.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm font-medium text-gray-900">
                      {job.jobName}
                    </div>
                    <div className="text-sm text-gray-500">
                      {job.id.substring(0, 8)}...
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${status.color}`}>
                      <StatusIcon className={`mr-1 h-3 w-3 ${job.status.includes('ING') ? 'animate-spin' : ''}`} />
                      {status.label}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center gap-2">
                      <div className="w-24 bg-gray-200 rounded-full h-2">
                        <div
                          className={`h-2 rounded-full transition-all ${
                            job.status === 'FAILED' ? 'bg-red-500' : 'bg-indigo-600'
                          }`}
                          style={{ width: `${job.progress || 0}%` }}
                        />
                      </div>
                      <span className="text-xs text-gray-600">{job.progress || 0}%</span>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {job.targetLanguage || 'springboot-3.2.5'}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {new Date(job.createdAt).toLocaleString('ko-KR', { 
                      year: '2-digit',
                      month: '2-digit',
                      day: '2-digit',
                      hour: '2-digit',
                      minute: '2-digit'
                    })}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                    <div className="flex justify-end gap-2">
                      <Link
                        to={`/jobs/${job.id}/monitor`}
                        className="text-indigo-600 hover:text-indigo-900 inline-flex items-center"
                      >
                        <Activity className="h-4 w-4 mr-1" />
                        모니터링
                      </Link>
                      <Link
                        to={`/jobs/${job.id}`}
                        className="text-gray-600 hover:text-gray-900 inline-flex items-center"
                      >
                        <Eye className="h-4 w-4 mr-1" />
                        상세
                      </Link>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>

        {(!jobs || jobs.length === 0) && (
          <div className="text-center py-12">
            <p className="text-gray-500">변환 작업이 없습니다.</p>
            <Link
              to="/upload"
              className="mt-4 inline-flex items-center text-indigo-600 hover:text-indigo-800"
            >
              새 변환 시작하기
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}
