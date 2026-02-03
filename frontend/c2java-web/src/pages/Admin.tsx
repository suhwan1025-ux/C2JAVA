import { useState, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Settings, Users, Terminal, Save, RefreshCw, CheckCircle, AlertCircle, FileCode, Server, Wifi, WifiOff, HardDrive, Activity, BarChart3, Globe, Shield, Zap, BookOpen, Upload, Trash2, Plus, Edit3, Eye } from 'lucide-react';
import { api } from '../lib/api';

interface EnvFileInfo {
  path: string;
  exists: boolean;
}

interface UserStats {
  totalUsers: number;
  activeUsers: number;
  recentUsers: { username: string; displayName: string; lastLoginAt: string; role: string }[];
}

interface FileServerStatus {
  enabled: boolean;
  url: string;
  connected: boolean;
  localPath: string;
}

interface Language {
  id: string;
  name: string;
  displayName: string;
  lastModified: string;
  hasConversionRules: boolean;
  hasProjectStructure: boolean;
  conversionRulesSize?: number;
  projectStructureSize?: number;
  isComplete: boolean;
  conversionRules?: string;
  projectStructure?: string;
}

// LLM 환경변수 키 정의
const LLM_ENV_FIELDS = [
  { key: 'ACTIVE_LLM_PROVIDER', label: '활성 LLM 제공자', type: 'select', options: ['qwen3', 'gpt_oss'] },
  { key: 'QWEN3_API_URL', label: 'QWEN3 API URL', type: 'text', group: 'QWEN3' },
  { key: 'QWEN3_API_KEY', label: 'QWEN3 API Key', type: 'password', group: 'QWEN3' },
  { key: 'QWEN3_MODEL_NAME', label: 'QWEN3 모델명', type: 'text', group: 'QWEN3' },
  { key: 'QWEN3_MAX_TOKENS', label: 'QWEN3 최대 토큰', type: 'number', group: 'QWEN3' },
  { key: 'QWEN3_TEMPERATURE', label: 'QWEN3 Temperature', type: 'number', group: 'QWEN3' },
  { key: 'GPT_OSS_API_URL', label: 'GPT OSS API URL', type: 'text', group: 'GPT_OSS' },
  { key: 'GPT_OSS_API_KEY', label: 'GPT OSS API Key', type: 'password', group: 'GPT_OSS' },
  { key: 'GPT_OSS_MODEL_NAME', label: 'GPT OSS 모델명', type: 'text', group: 'GPT_OSS' },
  { key: 'GPT_OSS_MAX_TOKENS', label: 'GPT OSS 최대 토큰', type: 'number', group: 'GPT_OSS' },
  { key: 'GPT_OSS_TEMPERATURE', label: 'GPT OSS Temperature', type: 'number', group: 'GPT_OSS' },
];

// CLI 환경변수 키 정의
const CLI_ENV_FIELDS = [
  { key: 'ACTIVE_CLI_TOOL', label: '활성 CLI 도구', type: 'select', options: ['aider', 'fabric', 'cursor', 'openai'] },
  { key: 'AIDER_ENABLED', label: 'AIDER 활성화', type: 'boolean', group: 'AIDER' },
  { key: 'AIDER_AUTO_COMMITS', label: 'AIDER 자동 커밋', type: 'boolean', group: 'AIDER' },
  { key: 'FABRIC_ENABLED', label: 'Fabric 활성화', type: 'boolean', group: 'Fabric' },
  { key: 'FABRIC_DEFAULT_PATTERN', label: 'Fabric 기본 패턴', type: 'text', group: 'Fabric' },
  { key: 'CURSOR_CLI_ENABLED', label: 'Cursor CLI 활성화', type: 'boolean', group: 'Cursor' },
  { key: 'CURSOR_CLI_MODEL', label: 'Cursor CLI 모델', type: 'text', group: 'Cursor' },
  { key: 'OPENAI_CLI_ENABLED', label: 'OpenAI CLI 활성화', type: 'boolean', group: 'OpenAI' },
  { key: 'OPENAI_MODEL', label: 'OpenAI 모델', type: 'text', group: 'OpenAI' },
];

// 환경 프리셋 정의
interface EnvironmentPreset {
  id: string;
  name: string;
  description: string;
  icon: 'shield' | 'globe';
  color: string;
  settings: {
    llm: Record<string, string>;
    cli: Record<string, string>;
    worker: Record<string, string>;
  };
}

const ENVIRONMENT_PRESETS: EnvironmentPreset[] = [
  {
    id: 'internal',
    name: '폐쇄망 (Internal Network)',
    description: '외부 인터넷 연결 없이 내부 LLM 서버와 워커 서버를 사용합니다.',
    icon: 'shield',
    color: 'blue',
    settings: {
      llm: {
        ACTIVE_LLM_PROVIDER: 'qwen3',
        QWEN3_API_URL: 'http://내부망-llm-서버:8080/v1',
        QWEN3_MODEL_NAME: 'qwen3-vl-235b',
        QWEN3_MAX_TOKENS: '8192',
        QWEN3_TEMPERATURE: '0.1',
        GPT_OSS_API_URL: 'http://내부망-gpt-서버:8080/v1',
        GPT_OSS_MODEL_NAME: 'gpt-oss',
        GPT_OSS_MAX_TOKENS: '8192',
        GPT_OSS_TEMPERATURE: '0.1',
      },
      cli: {
        AIDER_ENABLED: 'true',
        AIDER_AUTO_COMMITS: 'false',
        FABRIC_ENABLED: 'true',
        FABRIC_DEFAULT_PATTERN: 'analyze_code',
      },
      worker: {
        WORKER_SERVER_URL: 'http://워커서버-IP:포트',
        FILE_SERVER_ENABLED: 'true',
        CLI_SERVICE_PORT: '8083',
        MCP_SERVICE_PORT: '8082',
        AIRFLOW_PORT: '8081',
        GRAFANA_PORT: '3001',
        FILE_SERVER_PORT: '8090',
      },
    },
  },
  {
    id: 'external',
    name: '외부망 (External Network)',
    description: '인터넷을 통해 외부 LLM API와 Cursor CLI를 사용합니다.',
    icon: 'globe',
    color: 'green',
    settings: {
      llm: {
        ACTIVE_LLM_PROVIDER: 'gpt_oss',
        QWEN3_API_URL: '',
        QWEN3_MODEL_NAME: '',
        QWEN3_MAX_TOKENS: '8192',
        QWEN3_TEMPERATURE: '0.1',
        GPT_OSS_API_URL: 'https://api.openai.com/v1',
        GPT_OSS_MODEL_NAME: 'gpt-4-turbo',
        GPT_OSS_MAX_TOKENS: '8192',
        GPT_OSS_TEMPERATURE: '0.1',
      },
      cli: {
        ACTIVE_CLI_TOOL: 'cursor',
        AIDER_ENABLED: 'false',
        AIDER_AUTO_COMMITS: 'false',
        FABRIC_ENABLED: 'false',
        FABRIC_DEFAULT_PATTERN: 'analyze_code',
        CURSOR_CLI_ENABLED: 'true',
        CURSOR_CLI_MODEL: 'gpt-4',
        OPENAI_CLI_ENABLED: 'true',
        OPENAI_MODEL: 'gpt-4',
      },
      worker: {
        WORKER_SERVER_URL: '',
        FILE_SERVER_ENABLED: 'false',
        CLI_SERVICE_PORT: '8083',
        MCP_SERVICE_PORT: '8082',
        AIRFLOW_PORT: '8081',
        GRAFANA_PORT: '3001',
        FILE_SERVER_PORT: '8090',
      },
    },
  },
];

export default function Admin() {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'environment' | 'rules' | 'llm' | 'cli' | 'fileserver' | 'users'>('environment');
  const [selectedPreset, setSelectedPreset] = useState<string | null>(null);
  const [applyingPreset, setApplyingPreset] = useState(false);
  
  // 언어/규칙 관리 상태
  const [selectedLanguage, setSelectedLanguage] = useState<Language | null>(null);
  const [editingConversionRules, setEditingConversionRules] = useState<string>('');
  const [editingProjectStructure, setEditingProjectStructure] = useState<string>('');
  const [activeRuleTab, setActiveRuleTab] = useState<'conversion' | 'structure'>('conversion');
  const [isEditing, setIsEditing] = useState(false);
  const [newLanguageName, setNewLanguageName] = useState('');
  const [showNewLanguageForm, setShowNewLanguageForm] = useState(false);
  const conversionFileRef = useRef<HTMLInputElement>(null);
  const structureFileRef = useRef<HTMLInputElement>(null);
  const [llmEnvValues, setLlmEnvValues] = useState<Record<string, string>>({});
  const [cliEnvValues, setCliEnvValues] = useState<Record<string, string>>({});
  const [hasLlmChanges, setHasLlmChanges] = useState(false);
  const [hasCliChanges, setHasCliChanges] = useState(false);
  const [saveMessage, setSaveMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [testingConnection, setTestingConnection] = useState(false);

  // 환경변수 파일 정보 조회
  const { data: envFileInfo } = useQuery<EnvFileInfo>({
    queryKey: ['envFileInfo'],
    queryFn: api.getEnvFileInfo,
  });

  // LLM 환경변수 조회
  const { data: llmEnvData, isLoading: llmLoading, refetch: refetchLlm } = useQuery({
    queryKey: ['llmEnvVariables'],
    queryFn: api.getLlmEnvVariables,
  });

  // CLI 환경변수 조회
  const { data: cliEnvData, isLoading: cliLoading, refetch: refetchCli } = useQuery({
    queryKey: ['cliEnvVariables'],
    queryFn: api.getCliEnvVariables,
  });

  // 사용자 통계 조회
  const { data: userStats, isLoading: userStatsLoading } = useQuery<UserStats>({
    queryKey: ['userStats'],
    queryFn: api.getUserStats,
  });

  // 파일 서버 상태 조회
  const { data: fileServerStatus, isLoading: fileServerLoading, refetch: refetchFileServer } = useQuery<FileServerStatus>({
    queryKey: ['fileServerStatus'],
    queryFn: api.getFileServerStatus,
  });

  // 언어 목록 조회
  const { data: languages, isLoading: languagesLoading, refetch: refetchLanguages } = useQuery<Language[]>({
    queryKey: ['languages'],
    queryFn: api.listLanguages,
  });

  // 언어 생성
  const createLanguageMutation = useMutation({
    mutationFn: (name: string) => api.createLanguage(name),
    onSuccess: (data) => {
      setSaveMessage({ type: 'success', text: '언어가 생성되었습니다.' });
      setShowNewLanguageForm(false);
      setNewLanguageName('');
      refetchLanguages();
      setSelectedLanguage(data);
      setEditingConversionRules(data.conversionRules || '');
      setEditingProjectStructure(data.projectStructure || '');
      setTimeout(() => setSaveMessage(null), 3000);
    },
    onError: () => {
      setSaveMessage({ type: 'error', text: '언어 생성에 실패했습니다. 이름 형식을 확인하세요.' });
      setTimeout(() => setSaveMessage(null), 3000);
    },
  });

  // 언어 삭제
  const deleteLanguageMutation = useMutation({
    mutationFn: (name: string) => api.deleteLanguage(name),
    onSuccess: () => {
      setSaveMessage({ type: 'success', text: '언어가 삭제되었습니다.' });
      setSelectedLanguage(null);
      refetchLanguages();
      setTimeout(() => setSaveMessage(null), 3000);
    },
    onError: () => {
      setSaveMessage({ type: 'error', text: '언어 삭제에 실패했습니다.' });
      setTimeout(() => setSaveMessage(null), 3000);
    },
  });

  // 변환 규칙 저장
  const saveConversionRulesMutation = useMutation({
    mutationFn: ({ languageName, content }: { languageName: string; content: string }) =>
      api.saveConversionRules(languageName, content),
    onSuccess: (data) => {
      setSaveMessage({ type: 'success', text: '변환 규칙이 저장되었습니다.' });
      setIsEditing(false);
      setSelectedLanguage(data);
      setEditingConversionRules(data.conversionRules || '');
      refetchLanguages();
      setTimeout(() => setSaveMessage(null), 3000);
    },
    onError: () => {
      setSaveMessage({ type: 'error', text: '변환 규칙 저장에 실패했습니다.' });
      setTimeout(() => setSaveMessage(null), 3000);
    },
  });

  // 프로젝트 구조 저장
  const saveProjectStructureMutation = useMutation({
    mutationFn: ({ languageName, content }: { languageName: string; content: string }) =>
      api.saveProjectStructure(languageName, content),
    onSuccess: (data) => {
      setSaveMessage({ type: 'success', text: '프로젝트 구조가 저장되었습니다.' });
      setIsEditing(false);
      setSelectedLanguage(data);
      setEditingProjectStructure(data.projectStructure || '');
      refetchLanguages();
      setTimeout(() => setSaveMessage(null), 3000);
    },
    onError: () => {
      setSaveMessage({ type: 'error', text: '프로젝트 구조 저장에 실패했습니다.' });
      setTimeout(() => setSaveMessage(null), 3000);
    },
  });

  // 변환 규칙 파일 업로드
  const uploadConversionRulesMutation = useMutation({
    mutationFn: ({ languageName, file }: { languageName: string; file: File }) =>
      api.uploadConversionRules(languageName, file),
    onSuccess: (data) => {
      setSaveMessage({ type: 'success', text: '변환 규칙 파일이 업로드되었습니다.' });
      setSelectedLanguage(data);
      setEditingConversionRules(data.conversionRules || '');
      refetchLanguages();
      setTimeout(() => setSaveMessage(null), 3000);
    },
    onError: () => {
      setSaveMessage({ type: 'error', text: '변환 규칙 파일 업로드에 실패했습니다.' });
      setTimeout(() => setSaveMessage(null), 3000);
    },
  });

  // 프로젝트 구조 파일 업로드
  const uploadProjectStructureMutation = useMutation({
    mutationFn: ({ languageName, file }: { languageName: string; file: File }) =>
      api.uploadProjectStructure(languageName, file),
    onSuccess: (data) => {
      setSaveMessage({ type: 'success', text: '프로젝트 구조 파일이 업로드되었습니다.' });
      setSelectedLanguage(data);
      setEditingProjectStructure(data.projectStructure || '');
      refetchLanguages();
      setTimeout(() => setSaveMessage(null), 3000);
    },
    onError: () => {
      setSaveMessage({ type: 'error', text: '프로젝트 구조 파일 업로드에 실패했습니다.' });
      setTimeout(() => setSaveMessage(null), 3000);
    },
  });

  // LLM 환경변수 저장
  const saveLlmMutation = useMutation({
    mutationFn: api.saveLlmEnvVariables,
    onSuccess: () => {
      setSaveMessage({ type: 'success', text: 'LLM 설정이 환경변수 파일에 저장되었습니다.' });
      setHasLlmChanges(false);
      queryClient.invalidateQueries({ queryKey: ['llmEnvVariables'] });
      setTimeout(() => setSaveMessage(null), 3000);
    },
    onError: () => {
      setSaveMessage({ type: 'error', text: 'LLM 설정 저장에 실패했습니다.' });
      setTimeout(() => setSaveMessage(null), 3000);
    },
  });

  // CLI 환경변수 저장
  const saveCliMutation = useMutation({
    mutationFn: api.saveCliEnvVariables,
    onSuccess: () => {
      setSaveMessage({ type: 'success', text: 'CLI 설정이 환경변수 파일에 저장되었습니다.' });
      setHasCliChanges(false);
      queryClient.invalidateQueries({ queryKey: ['cliEnvVariables'] });
      setTimeout(() => setSaveMessage(null), 3000);
    },
    onError: () => {
      setSaveMessage({ type: 'error', text: 'CLI 설정 저장에 실패했습니다.' });
      setTimeout(() => setSaveMessage(null), 3000);
    },
  });

  // LLM 환경변수 로드
  useEffect(() => {
    if (llmEnvData) {
      setLlmEnvValues(llmEnvData);
      setHasLlmChanges(false);
    }
  }, [llmEnvData]);

  // CLI 환경변수 로드
  useEffect(() => {
    if (cliEnvData) {
      setCliEnvValues(cliEnvData);
      setHasCliChanges(false);
    }
  }, [cliEnvData]);

  const handleLlmChange = (key: string, value: string) => {
    setLlmEnvValues(prev => ({ ...prev, [key]: value }));
    setHasLlmChanges(true);
  };

  const handleCliChange = (key: string, value: string) => {
    setCliEnvValues(prev => ({ ...prev, [key]: value }));
    setHasCliChanges(true);
  };

  const handleSaveLlm = () => {
    saveLlmMutation.mutate(llmEnvValues);
  };

  const handleSaveCli = () => {
    saveCliMutation.mutate(cliEnvValues);
  };

  const handleTestConnection = async () => {
    setTestingConnection(true);
    try {
      const result = await api.testFileServerConnection();
      setSaveMessage({
        type: result.connected ? 'success' : 'error',
        text: result.message
      });
      refetchFileServer();
    } catch {
      setSaveMessage({ type: 'error', text: '연결 테스트 실패' });
    } finally {
      setTestingConnection(false);
      setTimeout(() => setSaveMessage(null), 3000);
    }
  };

  // 프리셋 적용 함수
  const handleApplyPreset = async (preset: EnvironmentPreset) => {
    setApplyingPreset(true);
    setSelectedPreset(preset.id);
    
    try {
      // LLM 설정 적용
      await api.saveLlmEnvVariables(preset.settings.llm);
      setLlmEnvValues(preset.settings.llm);
      
      // CLI 설정 적용
      await api.saveCliEnvVariables(preset.settings.cli);
      setCliEnvValues(preset.settings.cli);
      
      // 쿼리 무효화
      queryClient.invalidateQueries({ queryKey: ['llmEnvVariables'] });
      queryClient.invalidateQueries({ queryKey: ['cliEnvVariables'] });
      queryClient.invalidateQueries({ queryKey: ['fileServerStatus'] });
      
      setSaveMessage({ 
        type: 'success', 
        text: `${preset.name} 환경 설정이 적용되었습니다. 워커 서버 URL은 직접 입력해주세요.` 
      });
      setTimeout(() => setSaveMessage(null), 5000);
    } catch {
      setSaveMessage({ type: 'error', text: '환경 설정 적용에 실패했습니다.' });
      setTimeout(() => setSaveMessage(null), 3000);
    } finally {
      setApplyingPreset(false);
    }
  };

  // 언어 선택 핸들러
  const handleSelectLanguage = async (lang: Language) => {
    try {
      const data = await api.getLanguageDetail(lang.id);
      setSelectedLanguage(data);
      setEditingConversionRules(data.conversionRules || '');
      setEditingProjectStructure(data.projectStructure || '');
      setIsEditing(false);
    } catch {
      setSaveMessage({ type: 'error', text: '언어 정보를 불러오는데 실패했습니다.' });
      setTimeout(() => setSaveMessage(null), 3000);
    }
  };

  // 변환 규칙 파일 업로드 핸들러
  const handleConversionFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && selectedLanguage) {
      uploadConversionRulesMutation.mutate({ languageName: selectedLanguage.id, file });
    }
    e.target.value = '';
  };

  // 프로젝트 구조 파일 업로드 핸들러
  const handleStructureFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && selectedLanguage) {
      uploadProjectStructureMutation.mutate({ languageName: selectedLanguage.id, file });
    }
    e.target.value = '';
  };

  const tabs = [
    { id: 'environment', label: '환경 설정', icon: Zap },
    { id: 'rules', label: '변환 규칙', icon: BookOpen },
    { id: 'llm', label: 'LLM 모델', icon: Settings },
    { id: 'cli', label: 'CLI 도구', icon: Terminal },
    { id: 'fileserver', label: '워커 서버', icon: Server },
    { id: 'users', label: '사용자', icon: Users },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">관리자 설정</h1>
        {envFileInfo && (
          <div className="flex items-center gap-2 text-sm text-gray-500">
            <FileCode className="h-4 w-4" />
            <span className="font-mono text-xs">{envFileInfo.path}</span>
            {envFileInfo.exists ? (
              <CheckCircle className="h-4 w-4 text-green-500" />
            ) : (
              <AlertCircle className="h-4 w-4 text-red-500" />
            )}
          </div>
        )}
      </div>

      {/* 저장 메시지 */}
      {saveMessage && (
        <div className={`p-4 rounded-lg flex items-center gap-2 ${
          saveMessage.type === 'success' ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'
        }`}>
          {saveMessage.type === 'success' ? (
            <CheckCircle className="h-5 w-5" />
          ) : (
            <AlertCircle className="h-5 w-5" />
          )}
          {saveMessage.text}
        </div>
      )}

      {/* 탭 */}
      <div className="border-b border-gray-200">
        <nav className="flex space-x-8 overflow-x-auto">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={`flex items-center gap-2 py-4 px-1 border-b-2 font-medium text-sm whitespace-nowrap ${
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

      {/* 환경 설정 (프리셋) */}
      {activeTab === 'environment' && (
        <div className="space-y-6">
          {/* 안내 */}
          <div className="bg-gradient-to-r from-indigo-500 to-purple-600 rounded-lg p-6 text-white">
            <h2 className="text-xl font-bold mb-2">환경 설정 마법사</h2>
            <p className="text-indigo-100">
              사용 환경에 맞는 프리셋을 선택하면 LLM, CLI, 워커 서버 설정이 자동으로 구성됩니다.
            </p>
          </div>

          {/* 프리셋 카드 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {ENVIRONMENT_PRESETS.map((preset) => (
              <div
                key={preset.id}
                className={`bg-white shadow-lg rounded-xl p-6 border-2 transition-all cursor-pointer hover:shadow-xl ${
                  selectedPreset === preset.id
                    ? preset.color === 'blue'
                      ? 'border-blue-500 ring-2 ring-blue-200'
                      : 'border-green-500 ring-2 ring-green-200'
                    : 'border-transparent hover:border-gray-200'
                }`}
                onClick={() => setSelectedPreset(preset.id)}
              >
                <div className="flex items-start gap-4">
                  <div className={`p-3 rounded-xl ${
                    preset.color === 'blue' ? 'bg-blue-100' : 'bg-green-100'
                  }`}>
                    {preset.icon === 'shield' ? (
                      <Shield className={`h-8 w-8 ${preset.color === 'blue' ? 'text-blue-600' : 'text-green-600'}`} />
                    ) : (
                      <Globe className={`h-8 w-8 ${preset.color === 'blue' ? 'text-blue-600' : 'text-green-600'}`} />
                    )}
                  </div>
                  <div className="flex-1">
                    <h3 className="text-lg font-semibold text-gray-900 mb-1">{preset.name}</h3>
                    <p className="text-sm text-gray-500 mb-4">{preset.description}</p>
                    
                    {/* 주요 설정 미리보기 */}
                    <div className="space-y-2 text-xs">
                      <div className="flex items-center gap-2">
                        <span className="text-gray-400">LLM:</span>
                        <span className="font-mono bg-gray-100 px-2 py-0.5 rounded">
                          {preset.settings.llm.ACTIVE_LLM_PROVIDER === 'qwen3' ? 'QWEN3 (내부)' : 'GPT (외부)'}
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="text-gray-400">파일저장:</span>
                        <span className="font-mono bg-gray-100 px-2 py-0.5 rounded">
                          {preset.settings.worker.FILE_SERVER_ENABLED === 'true' ? '워커 서버' : '로컬'}
                        </span>
                      </div>
                    </div>
                  </div>
                  
                  {/* 선택 표시 */}
                  {selectedPreset === preset.id && (
                    <CheckCircle className={`h-6 w-6 ${
                      preset.color === 'blue' ? 'text-blue-500' : 'text-green-500'
                    }`} />
                  )}
                </div>

                {/* 적용 버튼 */}
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    handleApplyPreset(preset);
                  }}
                  disabled={applyingPreset}
                  className={`w-full mt-4 py-2 px-4 rounded-lg font-medium transition-colors ${
                    preset.color === 'blue'
                      ? 'bg-blue-600 hover:bg-blue-700 text-white'
                      : 'bg-green-600 hover:bg-green-700 text-white'
                  } disabled:opacity-50`}
                >
                  {applyingPreset && selectedPreset === preset.id ? (
                    <span className="flex items-center justify-center gap-2">
                      <RefreshCw className="h-4 w-4 animate-spin" />
                      적용 중...
                    </span>
                  ) : (
                    '이 환경으로 설정'
                  )}
                </button>
              </div>
            ))}
          </div>

          {/* 상세 설정 안내 */}
          <div className="bg-white shadow rounded-lg p-6">
            <h3 className="font-semibold text-gray-900 mb-4">프리셋 적용 후 추가 설정</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="p-4 bg-blue-50 rounded-lg">
                <h4 className="font-medium text-blue-900 mb-2 flex items-center gap-2">
                  <Shield className="h-5 w-5" />
                  폐쇄망 환경
                </h4>
                <ul className="text-sm text-blue-800 space-y-1">
                  <li>1. LLM 모델 탭에서 내부 LLM 서버 URL 입력</li>
                  <li>2. 워커 서버 탭에서 워커 서버 IP 입력</li>
                  <li>3. API Key가 필요한 경우 입력</li>
                </ul>
              </div>
              <div className="p-4 bg-green-50 rounded-lg">
                <h4 className="font-medium text-green-900 mb-2 flex items-center gap-2">
                  <Globe className="h-5 w-5" />
                  외부망 환경
                </h4>
                <ul className="text-sm text-green-800 space-y-1">
                  <li>1. LLM 모델 탭에서 OpenAI API Key 입력</li>
                  <li>2. 필요시 다른 외부 LLM API URL 설정</li>
                  <li>3. 로컬 저장소로 파일 저장</li>
                </ul>
              </div>
            </div>
          </div>

          {/* 현재 환경 상태 */}
          <div className="bg-white shadow rounded-lg p-6">
            <h3 className="font-semibold text-gray-900 mb-4">현재 환경 상태</h3>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div className="text-center p-4 bg-gray-50 rounded-lg">
                <p className="text-xs text-gray-500 mb-1">활성 LLM</p>
                <p className="font-semibold text-gray-900">
                  {llmEnvValues['ACTIVE_LLM_PROVIDER'] === 'qwen3' ? 'QWEN3' : 'GPT OSS'}
                </p>
              </div>
              <div className="text-center p-4 bg-gray-50 rounded-lg">
                <p className="text-xs text-gray-500 mb-1">AIDER</p>
                <p className="font-semibold text-gray-900">
                  {cliEnvValues['AIDER_ENABLED'] === 'true' ? '활성화' : '비활성화'}
                </p>
              </div>
              <div className="text-center p-4 bg-gray-50 rounded-lg">
                <p className="text-xs text-gray-500 mb-1">Fabric</p>
                <p className="font-semibold text-gray-900">
                  {cliEnvValues['FABRIC_ENABLED'] === 'true' ? '활성화' : '비활성화'}
                </p>
              </div>
              <div className="text-center p-4 bg-gray-50 rounded-lg">
                <p className="text-xs text-gray-500 mb-1">파일 저장</p>
                <p className="font-semibold text-gray-900">
                  {fileServerStatus?.enabled ? '워커 서버' : '로컬'}
                </p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 변환 규칙 관리 - 언어별 */}
      {activeTab === 'rules' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* 왼쪽: 언어 목록 */}
          <div className="bg-white shadow rounded-lg p-4">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-gray-900">변환 대상 언어</h3>
              <div className="flex gap-2">
                <button
                  onClick={() => refetchLanguages()}
                  className="p-2 text-gray-500 hover:bg-gray-100 rounded"
                >
                  <RefreshCw className="h-4 w-4" />
                </button>
                <button
                  onClick={() => setShowNewLanguageForm(true)}
                  className="p-2 text-indigo-600 hover:bg-indigo-50 rounded"
                  title="새 언어 추가"
                >
                  <Plus className="h-4 w-4" />
                </button>
              </div>
            </div>

            {/* 새 언어 추가 폼 */}
            {showNewLanguageForm && (
              <div className="mb-4 p-3 bg-indigo-50 rounded-lg">
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  언어명 (예: springboot-3.2.5)
                </label>
                <input
                  type="text"
                  value={newLanguageName}
                  onChange={(e) => setNewLanguageName(e.target.value)}
                  placeholder="언어명 입력"
                  className="w-full px-3 py-2 border rounded text-sm mb-2"
                />
                <div className="flex gap-2">
                  <button
                    onClick={() => {
                      if (newLanguageName.trim()) {
                        createLanguageMutation.mutate(newLanguageName.trim());
                      }
                    }}
                    disabled={!newLanguageName.trim() || createLanguageMutation.isPending}
                    className="flex-1 px-3 py-1.5 text-sm bg-indigo-600 text-white rounded hover:bg-indigo-700 disabled:opacity-50"
                  >
                    생성
                  </button>
                  <button
                    onClick={() => {
                      setShowNewLanguageForm(false);
                      setNewLanguageName('');
                    }}
                    className="px-3 py-1.5 text-sm text-gray-600 border rounded hover:bg-gray-50"
                  >
                    취소
                  </button>
                </div>
              </div>
            )}

            {/* 숨겨진 파일 업로드 input */}
            <input
              type="file"
              ref={conversionFileRef}
              onChange={handleConversionFileUpload}
              accept=".yaml,.yml,.json"
              className="hidden"
            />
            <input
              type="file"
              ref={structureFileRef}
              onChange={handleStructureFileUpload}
              accept=".yaml,.yml,.json"
              className="hidden"
            />

            {languagesLoading ? (
              <div className="text-center py-8 text-gray-500">로딩 중...</div>
            ) : languages && languages.length > 0 ? (
              <div className="space-y-2">
                {languages.map((lang) => (
                  <div
                    key={lang.id}
                    onClick={() => handleSelectLanguage(lang)}
                    className={`p-3 rounded-lg cursor-pointer transition-colors ${
                      selectedLanguage?.id === lang.id
                        ? 'bg-indigo-50 border border-indigo-200'
                        : 'bg-gray-50 hover:bg-gray-100'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <BookOpen className="h-4 w-4 text-indigo-500" />
                        <span className="font-medium text-sm">{lang.displayName}</span>
                      </div>
                      {lang.isComplete ? (
                        <CheckCircle className="h-4 w-4 text-green-500" />
                      ) : (
                        <AlertCircle className="h-4 w-4 text-yellow-500" />
                      )}
                    </div>
                    <div className="flex items-center gap-2 mt-2 text-xs">
                      <span className={`px-2 py-0.5 rounded ${lang.hasConversionRules ? 'bg-blue-100 text-blue-700' : 'bg-gray-200 text-gray-500'}`}>
                        변환규칙
                      </span>
                      <span className={`px-2 py-0.5 rounded ${lang.hasProjectStructure ? 'bg-green-100 text-green-700' : 'bg-gray-200 text-gray-500'}`}>
                        구조
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-8 text-gray-500">
                <BookOpen className="h-12 w-12 mx-auto mb-2 opacity-50" />
                <p className="mb-2">등록된 언어가 없습니다.</p>
                <p className="text-xs">위의 + 버튼을 클릭하여 언어를 추가하세요.</p>
              </div>
            )}
          </div>

          {/* 오른쪽: 규칙 파일 내용 */}
          <div className="lg:col-span-2 bg-white shadow rounded-lg p-4">
            {selectedLanguage ? (
              <>
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h3 className="font-semibold text-gray-900">{selectedLanguage.displayName}</h3>
                    <p className="text-xs text-gray-500">
                      마지막 수정: {selectedLanguage.lastModified}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => {
                        if (confirm(`"${selectedLanguage.displayName}" 언어를 삭제하시겠습니까?`)) {
                          deleteLanguageMutation.mutate(selectedLanguage.id);
                        }
                      }}
                      className="px-3 py-1.5 text-sm text-red-600 border border-red-200 rounded hover:bg-red-50 flex items-center gap-1"
                    >
                      <Trash2 className="h-4 w-4" />
                      언어 삭제
                    </button>
                  </div>
                </div>

                {/* 규칙 파일 탭 */}
                <div className="flex border-b mb-4">
                  <button
                    onClick={() => setActiveRuleTab('conversion')}
                    className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
                      activeRuleTab === 'conversion'
                        ? 'border-indigo-500 text-indigo-600'
                        : 'border-transparent text-gray-500 hover:text-gray-700'
                    }`}
                  >
                    변환 규칙
                  </button>
                  <button
                    onClick={() => setActiveRuleTab('structure')}
                    className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
                      activeRuleTab === 'structure'
                        ? 'border-indigo-500 text-indigo-600'
                        : 'border-transparent text-gray-500 hover:text-gray-700'
                    }`}
                  >
                    프로젝트 구조
                  </button>
                </div>

                {/* 변환 규칙 탭 */}
                {activeRuleTab === 'conversion' && (
                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm text-gray-600">conversion-rules.yaml</span>
                      <div className="flex gap-2">
                        <button
                          onClick={() => conversionFileRef.current?.click()}
                          className="px-3 py-1.5 text-sm text-gray-600 border rounded hover:bg-gray-50 flex items-center gap-1"
                        >
                          <Upload className="h-4 w-4" />
                          업로드
                        </button>
                        {isEditing ? (
                          <>
                            <button
                              onClick={() => {
                                setIsEditing(false);
                                setEditingConversionRules(selectedLanguage.conversionRules || '');
                              }}
                              className="px-3 py-1.5 text-sm text-gray-600 border rounded hover:bg-gray-50"
                            >
                              취소
                            </button>
                            <button
                              onClick={() => saveConversionRulesMutation.mutate({
                                languageName: selectedLanguage.id,
                                content: editingConversionRules
                              })}
                              disabled={saveConversionRulesMutation.isPending}
                              className="px-3 py-1.5 text-sm bg-indigo-600 text-white rounded hover:bg-indigo-700 flex items-center gap-1 disabled:opacity-50"
                            >
                              <Save className="h-4 w-4" />
                              저장
                            </button>
                          </>
                        ) : (
                          <button
                            onClick={() => setIsEditing(true)}
                            className="px-3 py-1.5 text-sm text-indigo-600 border border-indigo-200 rounded hover:bg-indigo-50 flex items-center gap-1"
                          >
                            <Edit3 className="h-4 w-4" />
                            편집
                          </button>
                        )}
                      </div>
                    </div>
                    {isEditing ? (
                      <textarea
                        value={editingConversionRules}
                        onChange={(e) => setEditingConversionRules(e.target.value)}
                        className="w-full h-[500px] font-mono text-sm p-4 border rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                        spellCheck={false}
                      />
                    ) : (
                      <pre className="w-full h-[500px] overflow-auto bg-gray-900 text-gray-100 p-4 rounded-lg text-sm">
                        <code>{selectedLanguage.conversionRules || '// 변환 규칙이 없습니다.'}</code>
                      </pre>
                    )}
                  </div>
                )}

                {/* 프로젝트 구조 탭 */}
                {activeRuleTab === 'structure' && (
                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm text-gray-600">project-structure.yaml</span>
                      <div className="flex gap-2">
                        <button
                          onClick={() => structureFileRef.current?.click()}
                          className="px-3 py-1.5 text-sm text-gray-600 border rounded hover:bg-gray-50 flex items-center gap-1"
                        >
                          <Upload className="h-4 w-4" />
                          업로드
                        </button>
                        {isEditing ? (
                          <>
                            <button
                              onClick={() => {
                                setIsEditing(false);
                                setEditingProjectStructure(selectedLanguage.projectStructure || '');
                              }}
                              className="px-3 py-1.5 text-sm text-gray-600 border rounded hover:bg-gray-50"
                            >
                              취소
                            </button>
                            <button
                              onClick={() => saveProjectStructureMutation.mutate({
                                languageName: selectedLanguage.id,
                                content: editingProjectStructure
                              })}
                              disabled={saveProjectStructureMutation.isPending}
                              className="px-3 py-1.5 text-sm bg-indigo-600 text-white rounded hover:bg-indigo-700 flex items-center gap-1 disabled:opacity-50"
                            >
                              <Save className="h-4 w-4" />
                              저장
                            </button>
                          </>
                        ) : (
                          <button
                            onClick={() => setIsEditing(true)}
                            className="px-3 py-1.5 text-sm text-indigo-600 border border-indigo-200 rounded hover:bg-indigo-50 flex items-center gap-1"
                          >
                            <Edit3 className="h-4 w-4" />
                            편집
                          </button>
                        )}
                      </div>
                    </div>
                    {isEditing ? (
                      <textarea
                        value={editingProjectStructure}
                        onChange={(e) => setEditingProjectStructure(e.target.value)}
                        className="w-full h-[500px] font-mono text-sm p-4 border rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                        spellCheck={false}
                      />
                    ) : (
                      <pre className="w-full h-[500px] overflow-auto bg-gray-900 text-gray-100 p-4 rounded-lg text-sm">
                        <code>{selectedLanguage.projectStructure || '// 프로젝트 구조가 없습니다.'}</code>
                      </pre>
                    )}
                  </div>
                )}
              </>
            ) : (
              <div className="flex flex-col items-center justify-center h-[600px] text-gray-400">
                <Eye className="h-16 w-16 mb-4" />
                <p>왼쪽에서 언어를 선택하세요</p>
                <p className="text-sm mt-2">또는 새 언어를 추가하여 시작하세요</p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* LLM 설정 */}
      {activeTab === 'llm' && (
        <div className="bg-white shadow rounded-lg p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-lg font-semibold">LLM 모델 설정</h2>
              <p className="text-sm text-gray-500">
                환경변수 파일(.env.internal)과 동기화됩니다.
              </p>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => refetchLlm()}
                className="px-3 py-2 text-gray-600 border rounded-md hover:bg-gray-50"
              >
                <RefreshCw className="h-4 w-4" />
              </button>
              <button
                onClick={handleSaveLlm}
                disabled={!hasLlmChanges || saveLlmMutation.isPending}
                className="px-4 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
              >
                <Save className="h-4 w-4" />
                {saveLlmMutation.isPending ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>

          {llmLoading ? (
            <div className="text-center py-8 text-gray-500">로딩 중...</div>
          ) : (
            <div className="space-y-6">
              {/* 활성 제공자 선택 */}
              <div className="p-4 bg-indigo-50 rounded-lg">
                <label className="block text-sm font-medium text-indigo-900 mb-2">
                  활성 LLM 제공자
                </label>
                <select
                  value={llmEnvValues['ACTIVE_LLM_PROVIDER'] || 'qwen3'}
                  onChange={(e) => handleLlmChange('ACTIVE_LLM_PROVIDER', e.target.value)}
                  className="w-full rounded-md border-indigo-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                >
                  <option value="qwen3">QWEN3 VL (235B)</option>
                  <option value="gpt_oss">GPT OSS</option>
                </select>
              </div>

              {/* QWEN3 설정 */}
              <div className="border rounded-lg p-4">
                <h3 className="font-medium text-gray-900 mb-4">QWEN3 설정</h3>
                <div className="grid grid-cols-2 gap-4">
                  {LLM_ENV_FIELDS.filter(f => f.group === 'QWEN3').map((field) => (
                    <div key={field.key}>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        {field.label}
                      </label>
                      <input
                        type={field.type}
                        value={llmEnvValues[field.key] || ''}
                        onChange={(e) => handleLlmChange(field.key, e.target.value)}
                        className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                        step={field.type === 'number' ? '0.1' : undefined}
                      />
                    </div>
                  ))}
                </div>
              </div>

              {/* GPT OSS 설정 */}
              <div className="border rounded-lg p-4">
                <h3 className="font-medium text-gray-900 mb-4">GPT OSS 설정</h3>
                <div className="grid grid-cols-2 gap-4">
                  {LLM_ENV_FIELDS.filter(f => f.group === 'GPT_OSS').map((field) => (
                    <div key={field.key}>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        {field.label}
                      </label>
                      <input
                        type={field.type}
                        value={llmEnvValues[field.key] || ''}
                        onChange={(e) => handleLlmChange(field.key, e.target.value)}
                        className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                        step={field.type === 'number' ? '0.1' : undefined}
                      />
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* CLI 설정 */}
      {activeTab === 'cli' && (
        <div className="bg-white shadow rounded-lg p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-lg font-semibold">CLI 도구 설정</h2>
              <p className="text-sm text-gray-500">
                AIDER 및 Fabric CLI 도구 설정 (환경변수 파일과 동기화)
              </p>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => refetchCli()}
                className="px-3 py-2 text-gray-600 border rounded-md hover:bg-gray-50"
              >
                <RefreshCw className="h-4 w-4" />
              </button>
              <button
                onClick={handleSaveCli}
                disabled={!hasCliChanges || saveCliMutation.isPending}
                className="px-4 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
              >
                <Save className="h-4 w-4" />
                {saveCliMutation.isPending ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>

          {cliLoading ? (
            <div className="text-center py-8 text-gray-500">로딩 중...</div>
          ) : (
            <div className="space-y-4">
              {CLI_ENV_FIELDS.map((field) => (
                <div key={field.key} className="flex items-center gap-4 p-4 bg-gray-50 rounded-lg">
                  <div className="flex-1">
                    <label className="block text-sm font-medium text-gray-700">
                      {field.label}
                    </label>
                    <p className="text-xs text-gray-400 font-mono">{field.key}</p>
                  </div>
                  <div className="flex-1">
                    {field.type === 'boolean' ? (
                      <select
                        value={cliEnvValues[field.key] || 'false'}
                        onChange={(e) => handleCliChange(field.key, e.target.value)}
                        className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                      >
                        <option value="true">활성화</option>
                        <option value="false">비활성화</option>
                      </select>
                    ) : (
                      <input
                        type="text"
                        value={cliEnvValues[field.key] || ''}
                        onChange={(e) => handleCliChange(field.key, e.target.value)}
                        className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                      />
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* 워커 서버 설정 */}
      {activeTab === 'fileserver' && (
        <div className="space-y-6">
          {/* 헤더 */}
          <div className="bg-white shadow rounded-lg p-6">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-lg font-semibold">워커 서버 (Worker Server)</h2>
                <p className="text-sm text-gray-500">
                  CLI, MCP, Airflow, Grafana, 파일 저장소가 설치되는 서버입니다.
                </p>
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => refetchFileServer()}
                  className="px-3 py-2 text-gray-600 border rounded-md hover:bg-gray-50"
                >
                  <RefreshCw className="h-4 w-4" />
                </button>
                <button
                  onClick={handleTestConnection}
                  disabled={testingConnection || !fileServerStatus?.enabled}
                  className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                >
                  {testingConnection ? (
                    <>
                      <RefreshCw className="h-4 w-4 animate-spin" />
                      테스트 중...
                    </>
                  ) : (
                    <>
                      <Wifi className="h-4 w-4" />
                      연결 테스트
                    </>
                  )}
                </button>
              </div>
            </div>

            {/* 연결 상태 */}
            <div className={`p-4 rounded-lg border-2 ${
              fileServerStatus?.enabled 
                ? fileServerStatus?.connected 
                  ? 'border-green-200 bg-green-50' 
                  : 'border-yellow-200 bg-yellow-50'
                : 'border-gray-200 bg-gray-50'
            }`}>
              <div className="flex items-center gap-4">
                {fileServerStatus?.enabled ? (
                  fileServerStatus?.connected ? (
                    <Wifi className="h-8 w-8 text-green-600" />
                  ) : (
                    <WifiOff className="h-8 w-8 text-yellow-600" />
                  )
                ) : (
                  <Server className="h-8 w-8 text-gray-400" />
                )}
                <div>
                  <h3 className="font-semibold text-gray-900">
                    {fileServerStatus?.enabled 
                      ? fileServerStatus?.connected 
                        ? '워커 서버 연결됨' 
                        : '워커 서버 연결 안됨'
                      : '로컬 모드 (단일 서버)'}
                  </h3>
                  <p className="text-sm text-gray-600">
                    {fileServerStatus?.enabled 
                      ? `서버: ${fileServerStatus?.url || '설정 안됨'}`
                      : '모든 서비스가 로컬에서 실행됩니다.'}
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* 서비스 상태 카드 */}
          <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
            {/* CLI 서비스 */}
            <div className="bg-white shadow rounded-lg p-4">
              <div className="flex items-center gap-3 mb-3">
                <div className="bg-purple-100 rounded-lg p-2">
                  <Terminal className="h-5 w-5 text-purple-600" />
                </div>
                <div>
                  <h4 className="font-medium text-gray-900">CLI 서비스</h4>
                  <p className="text-xs text-gray-500">AIDER, Fabric</p>
                </div>
              </div>
              <div className="text-sm">
                <p className="text-gray-600">포트: <span className="font-mono">8083</span></p>
                <p className="text-xs text-gray-400 mt-1">CLI_SERVICE_URL</p>
              </div>
            </div>

            {/* MCP 서버 */}
            <div className="bg-white shadow rounded-lg p-4">
              <div className="flex items-center gap-3 mb-3">
                <div className="bg-blue-100 rounded-lg p-2">
                  <Activity className="h-5 w-5 text-blue-600" />
                </div>
                <div>
                  <h4 className="font-medium text-gray-900">MCP 서버</h4>
                  <p className="text-xs text-gray-500">Model Context Protocol</p>
                </div>
              </div>
              <div className="text-sm">
                <p className="text-gray-600">포트: <span className="font-mono">8082</span></p>
                <p className="text-xs text-gray-400 mt-1">MCP_SERVICE_URL</p>
              </div>
            </div>

            {/* Airflow */}
            <div className="bg-white shadow rounded-lg p-4">
              <div className="flex items-center gap-3 mb-3">
                <div className="bg-teal-100 rounded-lg p-2">
                  <Settings className="h-5 w-5 text-teal-600" />
                </div>
                <div>
                  <h4 className="font-medium text-gray-900">Airflow</h4>
                  <p className="text-xs text-gray-500">워크플로우 오케스트레이션</p>
                </div>
              </div>
              <div className="text-sm">
                <p className="text-gray-600">포트: <span className="font-mono">8081</span></p>
                <p className="text-xs text-gray-400 mt-1">AIRFLOW_URL</p>
              </div>
            </div>

            {/* Grafana */}
            <div className="bg-white shadow rounded-lg p-4">
              <div className="flex items-center gap-3 mb-3">
                <div className="bg-orange-100 rounded-lg p-2">
                  <BarChart3 className="h-5 w-5 text-orange-600" />
                </div>
                <div>
                  <h4 className="font-medium text-gray-900">Grafana</h4>
                  <p className="text-xs text-gray-500">모니터링 대시보드</p>
                </div>
              </div>
              <div className="text-sm">
                <p className="text-gray-600">포트: <span className="font-mono">3001</span></p>
                <p className="text-xs text-gray-400 mt-1">GRAFANA_URL</p>
              </div>
            </div>

            {/* 파일 저장소 */}
            <div className="bg-white shadow rounded-lg p-4">
              <div className="flex items-center gap-3 mb-3">
                <div className="bg-green-100 rounded-lg p-2">
                  <HardDrive className="h-5 w-5 text-green-600" />
                </div>
                <div>
                  <h4 className="font-medium text-gray-900">파일 저장소</h4>
                  <p className="text-xs text-gray-500">업로드 파일 저장</p>
                </div>
              </div>
              <div className="text-sm">
                <p className="text-gray-600">
                  {fileServerStatus?.enabled ? (
                    <>포트: <span className="font-mono">8090</span></>
                  ) : (
                    <>경로: <span className="font-mono text-xs">{fileServerStatus?.localPath || '/app/workspace'}</span></>
                  )}
                </p>
                <p className="text-xs text-gray-400 mt-1">FILE_SERVER_URL</p>
              </div>
            </div>
          </div>

          {/* 설정 방법 안내 */}
          <div className="bg-white shadow rounded-lg p-6">
            <h4 className="font-medium text-gray-900 mb-4">워커 서버 설정 방법</h4>
            <div className="bg-gray-50 p-4 rounded-lg">
              <p className="text-sm text-gray-600 mb-3">
                환경변수 파일(<code className="bg-gray-200 px-1 rounded">.env.internal</code>)에서 워커 서버 URL을 설정하세요:
              </p>
              <pre className="bg-gray-800 text-green-400 p-4 rounded text-xs font-mono overflow-x-auto">
{`# 워커 서버 기본 URL 설정
WORKER_SERVER_URL=http://192.168.1.100

# 파일 저장소 활성화 (워커 서버에 파일 저장)
FILE_SERVER_ENABLED=true

# 각 서비스 포트 (기본값)
CLI_SERVICE_PORT=8083
MCP_SERVICE_PORT=8082
AIRFLOW_PORT=8081
GRAFANA_PORT=3001
FILE_SERVER_PORT=8090`}
              </pre>
            </div>
          </div>
        </div>
      )}

      {/* 사용자 현황 */}
      {activeTab === 'users' && (
        <div className="space-y-6">
          {/* 통계 카드 */}
          <div className="grid grid-cols-2 gap-6">
            <div className="bg-white shadow rounded-lg p-6">
              <div className="flex items-center">
                <div className="bg-blue-500 rounded-md p-3">
                  <Users className="h-6 w-6 text-white" />
                </div>
                <div className="ml-5">
                  <p className="text-sm text-gray-500">전체 사용자</p>
                  <p className="text-2xl font-semibold">
                    {userStatsLoading ? '-' : userStats?.totalUsers || 0}
                  </p>
                </div>
              </div>
            </div>
            <div className="bg-white shadow rounded-lg p-6">
              <div className="flex items-center">
                <div className="bg-green-500 rounded-md p-3">
                  <CheckCircle className="h-6 w-6 text-white" />
                </div>
                <div className="ml-5">
                  <p className="text-sm text-gray-500">활성 사용자</p>
                  <p className="text-2xl font-semibold">
                    {userStatsLoading ? '-' : userStats?.activeUsers || 0}
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* 최근 로그인 사용자 목록 */}
          <div className="bg-white shadow rounded-lg overflow-hidden">
            <div className="px-6 py-4 border-b border-gray-200">
              <h3 className="text-lg font-semibold">최근 로그인 사용자</h3>
            </div>
            {userStatsLoading ? (
              <div className="text-center py-8 text-gray-500">로딩 중...</div>
            ) : (
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">사용자</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">역할</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">마지막 로그인</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {userStats?.recentUsers && userStats.recentUsers.length > 0 ? (
                    userStats.recentUsers.map((user, idx) => (
                      <tr key={idx}>
                        <td className="px-6 py-4">
                          <div>
                            <div className="font-medium text-gray-900">{user.displayName || '-'}</div>
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
                        <td className="px-6 py-4 text-sm text-gray-500">
                          {user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString('ko-KR') : '-'}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={3} className="px-6 py-8 text-center text-gray-500">
                        최근 로그인한 사용자가 없습니다.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
