import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { 
  CheckCircle, 
  XCircle, 
  Clock, 
  FileCode, 
  Play,
  AlertTriangle,
  TrendingUp,
  List
} from 'lucide-react';
import { api } from '../lib/api';
import { useAuthStore } from '../store/authStore';
import { Link } from 'react-router-dom';

interface JobStats {
  total: number;
  pending: number;
  converting: number;
  compileSuccess: number;
  compileFailed: number;
  runtimePass: number;
  runtimeFailed: number;
  completed: number;
  failed: number;
}

interface RecentJob {
  id: string;
  jobName: string;
  status: string;
  createdAt: string;
  completedAt?: string;
  compileAttempts: number;
}

export default function Dashboard() {
  const { user } = useAuthStore();

  // 작업 목록 조회
  const { data: jobs = [], isLoading } = useQuery({
    queryKey: ['jobs'],
    queryFn: api.getJobs,
  });

  // 통계 계산
  const stats: JobStats = {
    total: jobs.length,
    pending: jobs.filter((j: any) => j.status === 'PENDING').length,
    converting: jobs.filter((j: any) => ['ANALYZING', 'CONVERTING'].includes(j.status)).length,
    compileSuccess: jobs.filter((j: any) => ['TESTING', 'REVIEWING', 'COMPLETED'].includes(j.status)).length,
    compileFailed: jobs.filter((j: any) => j.status === 'FAILED' && j.compileAttempts > 0).length,
    runtimePass: jobs.filter((j: any) => j.status === 'COMPLETED').length,
    runtimeFailed: jobs.filter((j: any) => j.status === 'FAILED').length,
    completed: jobs.filter((j: any) => j.status === 'COMPLETED').length,
    failed: jobs.filter((j: any) => j.status === 'FAILED').length,
  };

  const successRate = stats.total > 0 
    ? ((stats.completed / stats.total) * 100).toFixed(1) 
    : '0.0';

  const recentJobs = jobs.slice(0, 5);

  const getStatusBadge = (status: string) => {
    const styles: Record<string, string> = {
      PENDING: 'bg-gray-100 text-gray-800',
      ANALYZING: 'bg-blue-100 text-blue-800',
      CONVERTING: 'bg-indigo-100 text-indigo-800',
      COMPILING: 'bg-yellow-100 text-yellow-800',
      TESTING: 'bg-purple-100 text-purple-800',
      REVIEWING: 'bg-cyan-100 text-cyan-800',
      COMPLETED: 'bg-green-100 text-green-800',
      FAILED: 'bg-red-100 text-red-800',
    };
    const labels: Record<string, string> = {
      PENDING: '대기중',
      ANALYZING: '분석중',
      CONVERTING: '변환중',
      COMPILING: '컴파일중',
      TESTING: '테스트중',
      REVIEWING: '리뷰중',
      COMPLETED: '완료',
      FAILED: '실패',
    };
    return (
      <span className={`px-2 py-1 text-xs rounded-full ${styles[status] || 'bg-gray-100'}`}>
        {labels[status] || status}
      </span>
    );
  };

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">변환 현황</h1>
          <p className="text-sm text-gray-500 mt-1">
            안녕하세요, {user?.displayName || user?.username}님!
          </p>
        </div>
        <Link
          to="/upload"
          className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700"
        >
          <FileCode className="h-5 w-5" />
          새 변환
        </Link>
      </div>

      {/* 통계 카드 */}
      <div className="grid grid-cols-4 gap-4">
        <div className="bg-white rounded-lg shadow p-5">
          <div className="flex items-center">
            <div className="bg-gray-500 rounded-md p-3">
              <Clock className="h-6 w-6 text-white" />
            </div>
            <div className="ml-4">
              <p className="text-sm text-gray-500">변환 대기</p>
              <p className="text-2xl font-semibold">{stats.pending}</p>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-lg shadow p-5">
          <div className="flex items-center">
            <div className="bg-blue-500 rounded-md p-3">
              <Play className="h-6 w-6 text-white" />
            </div>
            <div className="ml-4">
              <p className="text-sm text-gray-500">변환 진행중</p>
              <p className="text-2xl font-semibold">{stats.converting}</p>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-lg shadow p-5">
          <div className="flex items-center">
            <div className="bg-green-500 rounded-md p-3">
              <CheckCircle className="h-6 w-6 text-white" />
            </div>
            <div className="ml-4">
              <p className="text-sm text-gray-500">변환 완료</p>
              <p className="text-2xl font-semibold">{stats.completed}</p>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-lg shadow p-5">
          <div className="flex items-center">
            <div className="bg-red-500 rounded-md p-3">
              <XCircle className="h-6 w-6 text-white" />
            </div>
            <div className="ml-4">
              <p className="text-sm text-gray-500">변환 실패</p>
              <p className="text-2xl font-semibold">{stats.failed}</p>
            </div>
          </div>
        </div>
      </div>

      {/* 상세 통계 */}
      <div className="grid grid-cols-2 gap-6">
        {/* 컴파일/런타임 현황 */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
            <TrendingUp className="h-5 w-5 text-indigo-600" />
            테스트 현황
          </h2>
          <div className="space-y-4">
            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
              <div className="flex items-center gap-3">
                <CheckCircle className="h-5 w-5 text-green-500" />
                <span className="text-sm font-medium">컴파일 성공</span>
              </div>
              <span className="text-lg font-semibold text-green-600">{stats.compileSuccess}</span>
            </div>
            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
              <div className="flex items-center gap-3">
                <XCircle className="h-5 w-5 text-red-500" />
                <span className="text-sm font-medium">컴파일 실패</span>
              </div>
              <span className="text-lg font-semibold text-red-600">{stats.compileFailed}</span>
            </div>
            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
              <div className="flex items-center gap-3">
                <CheckCircle className="h-5 w-5 text-blue-500" />
                <span className="text-sm font-medium">런타임 통과</span>
              </div>
              <span className="text-lg font-semibold text-blue-600">{stats.runtimePass}</span>
            </div>
            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
              <div className="flex items-center gap-3">
                <AlertTriangle className="h-5 w-5 text-orange-500" />
                <span className="text-sm font-medium">런타임 실패</span>
              </div>
              <span className="text-lg font-semibold text-orange-600">{stats.runtimeFailed}</span>
            </div>
          </div>
        </div>

        {/* 변환 요약 */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
            <List className="h-5 w-5 text-indigo-600" />
            변환 요약
          </h2>
          <div className="space-y-4">
            <div className="text-center py-4 bg-indigo-50 rounded-lg">
              <p className="text-sm text-indigo-600 font-medium">전체 변환 성공률</p>
              <p className="text-4xl font-bold text-indigo-700 mt-2">{successRate}%</p>
            </div>
            <div className="grid grid-cols-2 gap-4 text-center">
              <div className="p-3 bg-gray-50 rounded-lg">
                <p className="text-2xl font-semibold text-gray-900">{stats.total}</p>
                <p className="text-sm text-gray-500">전체 작업</p>
              </div>
              <div className="p-3 bg-gray-50 rounded-lg">
                <p className="text-2xl font-semibold text-gray-900">{stats.pending + stats.converting}</p>
                <p className="text-sm text-gray-500">진행중 작업</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 최근 변환 내역 */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
          <h2 className="text-lg font-semibold">최근 변환 내역</h2>
          <Link to="/jobs" className="text-sm text-indigo-600 hover:text-indigo-800">
            전체 보기 →
          </Link>
        </div>
        {isLoading ? (
          <div className="text-center py-8 text-gray-500">로딩 중...</div>
        ) : recentJobs.length === 0 ? (
          <div className="text-center py-8 text-gray-500">
            <FileCode className="h-12 w-12 mx-auto text-gray-300 mb-2" />
            <p>변환 작업이 없습니다.</p>
            <Link to="/upload" className="text-indigo-600 hover:underline mt-2 inline-block">
              첫 변환 시작하기
            </Link>
          </div>
        ) : (
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">파일명</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">상태</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">컴파일 시도</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">생성일시</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {recentJobs.map((job: any) => (
                <tr key={job.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4">
                    <Link to={`/jobs/${job.id}`} className="text-indigo-600 hover:underline font-medium">
                      {job.jobName}
                    </Link>
                  </td>
                  <td className="px-6 py-4">{getStatusBadge(job.status)}</td>
                  <td className="px-6 py-4 text-sm text-gray-500">{job.compileAttempts || 0}회</td>
                  <td className="px-6 py-4 text-sm text-gray-500">
                    {new Date(job.createdAt).toLocaleString('ko-KR')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
