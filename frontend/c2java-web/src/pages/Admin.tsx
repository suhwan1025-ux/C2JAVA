import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Settings, Users, Terminal, Save, RefreshCw } from 'lucide-react';
import { api } from '../lib/api';

interface Config {
  key: string;
  value: string;
  category: string;
  description: string;
  isSecret: boolean;
  isEditable: boolean;
}

interface UserStats {
  totalUsers: number;
  activeUsers: number;
  todayLogins: number;
  recentUsers: { username: string; displayName: string; lastLoginAt: string; role: string }[];
}

export default function Admin() {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'llm' | 'users' | 'cli'>('llm');
  const [editedConfigs, setEditedConfigs] = useState<Record<string, string>>({});

  // 설정 조회
  const { data: configs, isLoading } = useQuery({
    queryKey: ['configs'],
    queryFn: api.getAllConfigs,
  });

  // 사용자 통계 (임시 데이터)
  const userStats: UserStats = {
    totalUsers: 15,
    activeUsers: 8,
    todayLogins: 5,
    recentUsers: [
      { username: 'admin2', displayName: '관리자2', lastLoginAt: '2026-02-02 16:28', role: 'USER' },
      { username: 'test', displayName: '테스트', lastLoginAt: '2026-02-02 16:28', role: 'USER' },
    ]
  };

  // 설정 업데이트
  const updateMutation = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) => 
      api.updateConfig(key, value),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['configs'] });
    },
  });

  const handleSave = (key: string) => {
    if (editedConfigs[key] !== undefined) {
      updateMutation.mutate({ key, value: editedConfigs[key] });
      setEditedConfigs(prev => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
    }
  };

  const handleChange = (key: string, value: string) => {
    setEditedConfigs(prev => ({ ...prev, [key]: value }));
  };

  // 카테고리별 필터링
  const llmConfigs = configs?.filter((c: Config) => c.category === 'LLM') || [];
  const cliConfigs = configs?.filter((c: Config) => c.category === 'CLI') || [];

  const tabs = [
    { id: 'llm', label: 'LLM 모델 설정', icon: Settings },
    { id: 'users', label: '사용자 현황', icon: Users },
    { id: 'cli', label: 'CLI 설정', icon: Terminal },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">관리자 설정</h1>
      </div>

      {/* 탭 */}
      <div className="border-b border-gray-200">
        <nav className="flex space-x-8">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={`flex items-center gap-2 py-4 px-1 border-b-2 font-medium text-sm ${
                activeTab === tab.id
                  ? 'border-indigo-500 text-indigo-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <tab.icon className="h-5 w-5" />
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      {/* LLM 설정 */}
      {activeTab === 'llm' && (
        <div className="bg-white shadow rounded-lg p-6">
          <h2 className="text-lg font-semibold mb-4">LLM 모델 설정</h2>
          <p className="text-sm text-gray-500 mb-6">
            사용할 LLM 모델과 API 연결 정보를 설정합니다.
          </p>

          {isLoading ? (
            <div className="text-center py-8 text-gray-500">로딩 중...</div>
          ) : (
            <div className="space-y-4">
              {llmConfigs.map((config: Config) => (
                <div key={config.key} className="flex items-center gap-4 p-4 bg-gray-50 rounded-lg">
                  <div className="flex-1">
                    <label className="block text-sm font-medium text-gray-700">
                      {config.description || config.key}
                    </label>
                    <p className="text-xs text-gray-400 font-mono">{config.key}</p>
                  </div>
                  <div className="flex-1">
                    {config.key === 'llm.active_provider' ? (
                      <select
                        value={editedConfigs[config.key] ?? config.value}
                        onChange={(e) => handleChange(config.key, e.target.value)}
                        className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                      >
                        <option value="qwen3">QWEN3 VL (235B)</option>
                        <option value="gpt_oss">GPT OSS</option>
                      </select>
                    ) : (
                      <input
                        type={config.isSecret ? 'password' : 'text'}
                        value={editedConfigs[config.key] ?? config.value}
                        onChange={(e) => handleChange(config.key, e.target.value)}
                        className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                        placeholder={config.isSecret ? '********' : ''}
                      />
                    )}
                  </div>
                  <button
                    onClick={() => handleSave(config.key)}
                    disabled={editedConfigs[config.key] === undefined}
                    className="px-3 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <Save className="h-4 w-4" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* 사용자 현황 */}
      {activeTab === 'users' && (
        <div className="space-y-6">
          {/* 통계 카드 */}
          <div className="grid grid-cols-3 gap-6">
            <div className="bg-white shadow rounded-lg p-6">
              <div className="flex items-center">
                <div className="bg-blue-500 rounded-md p-3">
                  <Users className="h-6 w-6 text-white" />
                </div>
                <div className="ml-5">
                  <p className="text-sm text-gray-500">전체 사용자</p>
                  <p className="text-2xl font-semibold">{userStats.totalUsers}</p>
                </div>
              </div>
            </div>
            <div className="bg-white shadow rounded-lg p-6">
              <div className="flex items-center">
                <div className="bg-green-500 rounded-md p-3">
                  <Users className="h-6 w-6 text-white" />
                </div>
                <div className="ml-5">
                  <p className="text-sm text-gray-500">활성 사용자</p>
                  <p className="text-2xl font-semibold">{userStats.activeUsers}</p>
                </div>
              </div>
            </div>
            <div className="bg-white shadow rounded-lg p-6">
              <div className="flex items-center">
                <div className="bg-purple-500 rounded-md p-3">
                  <RefreshCw className="h-6 w-6 text-white" />
                </div>
                <div className="ml-5">
                  <p className="text-sm text-gray-500">오늘 로그인</p>
                  <p className="text-2xl font-semibold">{userStats.todayLogins}</p>
                </div>
              </div>
            </div>
          </div>

          {/* 최근 사용자 목록 */}
          <div className="bg-white shadow rounded-lg overflow-hidden">
            <div className="px-6 py-4 border-b border-gray-200">
              <h3 className="text-lg font-semibold">최근 로그인 사용자</h3>
            </div>
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">사용자</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">역할</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">마지막 로그인</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {userStats.recentUsers.map((user, idx) => (
                  <tr key={idx}>
                    <td className="px-6 py-4">
                      <div>
                        <div className="font-medium text-gray-900">{user.displayName}</div>
                        <div className="text-sm text-gray-500">@{user.username}</div>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span className={`px-2 py-1 text-xs rounded-full ${
                        user.role === 'ADMIN' ? 'bg-red-100 text-red-800' :
                        user.role === 'MANAGER' ? 'bg-yellow-100 text-yellow-800' :
                        'bg-gray-100 text-gray-800'
                      }`}>
                        {user.role}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-500">{user.lastLoginAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* CLI 설정 */}
      {activeTab === 'cli' && (
        <div className="bg-white shadow rounded-lg p-6">
          <h2 className="text-lg font-semibold mb-4">CLI 도구 설정</h2>
          <p className="text-sm text-gray-500 mb-6">
            AIDER 및 Fabric CLI 도구 설정을 관리합니다.
          </p>

          {isLoading ? (
            <div className="text-center py-8 text-gray-500">로딩 중...</div>
          ) : (
            <div className="space-y-4">
              {cliConfigs.map((config: Config) => (
                <div key={config.key} className="flex items-center gap-4 p-4 bg-gray-50 rounded-lg">
                  <div className="flex-1">
                    <label className="block text-sm font-medium text-gray-700">
                      {config.description || config.key}
                    </label>
                    <p className="text-xs text-gray-400 font-mono">{config.key}</p>
                  </div>
                  <div className="flex-1">
                    {config.key.includes('enabled') ? (
                      <select
                        value={editedConfigs[config.key] ?? config.value}
                        onChange={(e) => handleChange(config.key, e.target.value)}
                        className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                      >
                        <option value="true">활성화</option>
                        <option value="false">비활성화</option>
                      </select>
                    ) : (
                      <input
                        type="text"
                        value={editedConfigs[config.key] ?? config.value}
                        onChange={(e) => handleChange(config.key, e.target.value)}
                        className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                      />
                    )}
                  </div>
                  <button
                    onClick={() => handleSave(config.key)}
                    disabled={editedConfigs[config.key] === undefined}
                    className="px-3 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <Save className="h-4 w-4" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
