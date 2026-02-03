import { useState, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Settings, Users, Terminal, Save, RefreshCw, CheckCircle, AlertCircle, FileCode, Server, Wifi, WifiOff, HardDrive, Activity, BarChart3, Globe, Shield, Zap, BookOpen, Upload, Trash2, Plus, Edit3, Eye, EyeOff, X, StopCircle } from 'lucide-react';
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

// 모델 상수 정의
const CURSOR_MODELS = [
  { value: 'gpt-4', label: 'GPT-4 (Default)' },
  { value: 'gpt-4o', label: 'GPT-4o' },
  { value: 'gpt-3.5-turbo', label: 'GPT-3.5 Turbo' },
  { value: 'claude-3.5-sonnet', label: 'Claude 3.5 Sonnet' },
  { value: 'claude-3-opus', label: 'Claude 3 Opus' },
];

const CLAUDE_MODELS = [
  { value: 'claude-3-5-sonnet-20240620', label: 'Claude 3.5 Sonnet (Default)' },
  { value: 'claude-3-opus-20240229', label: 'Claude 3 Opus' },
  { value: 'claude-3-sonnet-20240229', label: 'Claude 3 Sonnet' },
  { value: 'claude-3-haiku-20240307', label: 'Claude 3 Haiku' },
];

// CLI 환경변수 키 정의
const CLI_ENV_FIELDS = [
  { key: 'ACTIVE_CLI_TOOL', label: '활성 CLI 도구', type: 'select', options: ['aider', 'fabric', 'cursor', 'claude'] },
  { key: 'WORKSPACE_PATH', label: '워크스페이스 경로', type: 'text', group: 'General' },
  { key: 'AIDER_ENABLED', label: 'AIDER 활성화', type: 'boolean', group: 'AIDER' },
  { key: 'AIDER_AUTO_COMMITS', label: 'AIDER 자동 커밋', type: 'boolean', group: 'AIDER' },
  { key: 'FABRIC_ENABLED', label: 'Fabric 활성화', type: 'boolean', group: 'Fabric' },
  { key: 'FABRIC_DEFAULT_PATTERN', label: 'Fabric 기본 패턴', type: 'text', group: 'Fabric' },
  { key: 'CURSOR_CLI_ENABLED', label: 'Cursor CLI 활성화', type: 'boolean', group: 'Cursor' },
  { key: 'CURSOR_CLI_AUTH_TOKEN', label: 'Cursor CLI 인증 토큰', type: 'password', group: 'Cursor' },
  { key: 'CURSOR_CLI_MODEL', label: 'Cursor CLI 모델', type: 'text', group: 'Cursor' },
  { key: 'CLAUDE_CLI_ENABLED', label: 'Claude CLI 활성화', type: 'boolean', group: 'Claude' },
  { key: 'ANTHROPIC_API_KEY', label: 'Anthropic API Key', type: 'password', group: 'Claude' },
  { key: 'CLAUDE_CLI_MODEL', label: 'Claude CLI 모델', type: 'text', group: 'Claude' },
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
  const [activeTab, setActiveTab] = useState<'environment' | 'rules' | 'llm' | 'cli' | 'fileserver' | 'users' | 'localserver'>('environment');
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
  const [workerEnvValues, setWorkerEnvValues] = useState<Record<string, string>>({});
  const [hasLlmChanges, setHasLlmChanges] = useState(false);
  const [hasCliChanges, setHasCliChanges] = useState(false);
  const [hasWorkerChanges, setHasWorkerChanges] = useState(false);
  const [saveMessage, setSaveMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [testingConnection, setTestingConnection] = useState(false);
  
  // 모달 상태
  const [showClosedNetworkModal, setShowClosedNetworkModal] = useState(false);
  const [showExternalNetworkModal, setShowExternalNetworkModal] = useState(false);
  
  // 토큰 표시 상태
  const [showCursorToken, setShowCursorToken] = useState(false);
  const [showClaudeApiKey, setShowClaudeApiKey] = useState(false);

  // 로컬 서버 관리 상태
  const [selectedService, setSelectedService] = useState<string | null>(null);
  const [serviceLogs, setServiceLogs] = useState<Record<string, string[]>>({});

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

  // 워커 서버 환경변수 조회
  const { data: workerEnvData, isLoading: workerLoading, refetch: refetchWorker } = useQuery({
    queryKey: ['workerEnvVariables'],
    queryFn: api.getWorkerServerEnvVariables,
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

  // 로컬 서버 상태 조회
  const { data: servicesStatus, refetch: refetchServicesStatus } = useQuery({
    queryKey: ['servicesStatus'],
    queryFn: api.getAllServicesStatus,
    refetchInterval: activeTab === 'localserver' ? 5000 : false, // 로컬 서버 탭에서만 5초마다 갱신
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
      setSaveMessage({ type: 'success', text: 'CLI 및 워크스페이스 설정이 저장되었습니다.' });
      setHasCliChanges(false);
      queryClient.invalidateQueries({ queryKey: ['cliEnvVariables'] });
      setTimeout(() => setSaveMessage(null), 3000);
    },
    onError: () => {
      setSaveMessage({ type: 'error', text: '설정 저장에 실패했습니다.' });
      setTimeout(() => setSaveMessage(null), 3000);
    },
  });

  // 워커 서버 환경변수 저장
  const saveWorkerMutation = useMutation({
    mutationFn: api.saveWorkerServerEnvVariables,
    onSuccess: () => {
      setSaveMessage({ type: 'success', text: '워커 서버 설정이 저장되었습니다.' });
      setHasWorkerChanges(false);
      queryClient.invalidateQueries({ queryKey: ['workerEnvVariables'] });
      setTimeout(() => setSaveMessage(null), 3000);
    },
    onError: () => {
      setSaveMessage({ type: 'error', text: '워커 서버 설정 저장에 실패했습니다.' });
      setTimeout(() => setSaveMessage(null), 3000);
    },
  });

  // CLI 연결 테스트
  const testCliConnectionMutation = useMutation({
    mutationFn: api.testCliConnection,
    onSuccess: (data) => {
      const messageText = data.details 
        ? `${data.message}\n${data.details}` 
        : data.message;
      setSaveMessage({ 
        type: data.success ? 'success' : 'error', 
        text: messageText 
      });
      setTimeout(() => setSaveMessage(null), 5000);
    },
    onError: (error: any) => {
      const errorMsg = error.response?.data?.message || '연결 테스트 요청 실패';
      setSaveMessage({ type: 'error', text: errorMsg });
      setTimeout(() => setSaveMessage(null), 5000);
    },
  });

  // 워크스페이스 열기
  const openWorkspaceMutation = useMutation({
    mutationFn: api.openWorkspace,
    onSuccess: (data) => {
      setSaveMessage({ 
        type: data.success ? 'success' : 'error', 
        text: data.message 
      });
      setTimeout(() => setSaveMessage(null), 3000);
    },
    onError: () => {
      setSaveMessage({ type: 'error', text: '워크스페이스 열기 요청 실패' });
      setTimeout(() => setSaveMessage(null), 3000);
    },
  });

  // 서비스 시작
  const startServiceMutation = useMutation({
    mutationFn: api.startService,
    onSuccess: (data) => {
      setSaveMessage({ 
        type: data.success ? 'success' : 'error', 
        text: data.message 
      });
      setTimeout(() => setSaveMessage(null), 5000);
      refetchServicesStatus();
    },
    onError: (error: any) => {
      setSaveMessage({ type: 'error', text: error.response?.data?.message || '서비스 시작 실패' });
      setTimeout(() => setSaveMessage(null), 5000);
    },
  });

  // 서비스 중지
  const stopServiceMutation = useMutation({
    mutationFn: api.stopService,
    onSuccess: (data) => {
      setSaveMessage({ 
        type: data.success ? 'success' : 'error', 
        text: data.message 
      });
      setTimeout(() => setSaveMessage(null), 5000);
      refetchServicesStatus();
    },
    onError: (error: any) => {
      setSaveMessage({ type: 'error', text: error.response?.data?.message || '서비스 중지 실패' });
      setTimeout(() => setSaveMessage(null), 5000);
    },
  });

  // CLI 데이터 처리 헬퍼 함수
  const processCliData = (data: Record<string, string>) => {
    let activeTool = 'cursor';
    
    if (data['AIDER_ENABLED'] === 'true') {
      activeTool = 'aider';
    } else if (data['FABRIC_ENABLED'] === 'true') {
      activeTool = 'fabric';
    } else if (data['CURSOR_CLI_ENABLED'] === 'true') {
      activeTool = 'cursor';
    } else if (data['CLAUDE_CLI_ENABLED'] === 'true') {
      activeTool = 'claude';
    }

    return {
      ...data,
      'ACTIVE_CLI_TOOL': activeTool
    };
  };

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
      setCliEnvValues(processCliData(cliEnvData));
      setHasCliChanges(false);
    }
  }, [cliEnvData]);

  // 워커 서버 환경변수 로드
  useEffect(() => {
    if (workerEnvData) {
      setWorkerEnvValues(workerEnvData);
      setHasWorkerChanges(false);
    }
  }, [workerEnvData]);

  // 모달 열릴 때 데이터 최신화 (외부망)
  useEffect(() => {
    if (showExternalNetworkModal) {
      // CLI 설정 최신화 및 강제 적용
      refetchCli().then(({ data }) => {
        if (data) {
          setCliEnvValues(processCliData(data));
        }
      });
      // LLM 설정 최신화 및 강제 적용
      refetchLlm().then(({ data }) => {
        if (data) {
          setLlmEnvValues(data);
        }
      });
    }
  }, [showExternalNetworkModal, refetchCli, refetchLlm]);

  // 모달 열릴 때 데이터 최신화 (폐쇄망)
  useEffect(() => {
    if (showClosedNetworkModal) {
      refetchWorker().then(({ data }) => {
        if (data) {
          setWorkerEnvValues(data);
        }
      });
      refetchLlm().then(({ data }) => {
        if (data) {
          setLlmEnvValues(data);
        }
      });
      refetchCli().then(({ data }) => {
        if (data) {
          setCliEnvValues(processCliData(data));
        }
      });
    }
  }, [showClosedNetworkModal, refetchWorker, refetchLlm, refetchCli]);

  const handleLlmChange = (key: string, value: string) => {
    setLlmEnvValues(prev => ({ ...prev, [key]: value }));
    setHasLlmChanges(true);
  };

  const handleCliChange = (key: string, value: string) => {
    setCliEnvValues(prev => ({ ...prev, [key]: value }));
    setHasCliChanges(true);
  };

  const handleWorkerChange = (key: string, value: string) => {
    setWorkerEnvValues(prev => ({ ...prev, [key]: value }));
    setHasWorkerChanges(true);
  };

  const handleSaveLlm = () => {
    saveLlmMutation.mutate(llmEnvValues);
  };

  const handleSaveCli = () => {
    saveCliMutation.mutate(cliEnvValues);
    setShowExternalNetworkModal(false);
  };

  const handleSaveClosedNetwork = async () => {
    try {
      // 1. 워커 서버 설정 저장
      await saveWorkerMutation.mutateAsync(workerEnvValues);

      // 2. LLM 설정 저장
      await saveLlmMutation.mutateAsync(llmEnvValues);

      // 3. CLI 설정 저장 (선택된 도구만 활성화)
      const selectedCli = cliEnvValues['ACTIVE_CLI_TOOL'];
      const updatedCliEnv = {
        ...cliEnvValues,
        'AIDER_ENABLED': selectedCli === 'aider' ? 'true' : 'false',
        'FABRIC_ENABLED': selectedCli === 'fabric' ? 'true' : 'false',
        'CURSOR_CLI_ENABLED': 'false', // 폐쇄망에서는 Cursor CLI 미사용
      };
      await saveCliMutation.mutateAsync(updatedCliEnv);
      setCliEnvValues(updatedCliEnv);

      setSaveMessage({ type: 'success', text: '폐쇄망 환경 설정이 저장되었습니다.' });
      setShowClosedNetworkModal(false);
    } catch (error) {
      setSaveMessage({ type: 'error', text: '설정 저장 중 오류가 발생했습니다.' });
    }
  };

  const handleSaveExternalNetwork = async () => {
    try {
      // 1. LLM 설정 저장 (OpenAI 기본값 적용 - 백엔드 호환성 유지용)
      const updatedLlmEnv: Record<string, string> = {
        ...llmEnvValues,
        'ACTIVE_LLM_PROVIDER': 'gpt_oss',
      };
      
      // URL이 설정되어 있지 않으면 OpenAI 기본값으로 설정
      if (!updatedLlmEnv['GPT_OSS_API_URL'] || updatedLlmEnv['GPT_OSS_API_URL'].includes('192.168.') || updatedLlmEnv['GPT_OSS_API_URL'].includes('localhost')) {
         updatedLlmEnv['GPT_OSS_API_URL'] = 'https://api.openai.com/v1';
         updatedLlmEnv['GPT_OSS_MODEL_NAME'] = 'gpt-4-turbo';
      }
      
      await saveLlmMutation.mutateAsync(updatedLlmEnv);
      setLlmEnvValues(updatedLlmEnv);

      // 2. CLI 설정 저장 (선택된 도구만 활성화)
      const selectedTool = cliEnvValues['ACTIVE_CLI_TOOL'] && ['cursor', 'claude'].includes(cliEnvValues['ACTIVE_CLI_TOOL']) 
        ? cliEnvValues['ACTIVE_CLI_TOOL'] 
        : 'cursor';

      const updatedCliEnv: Record<string, string> = {
        ...cliEnvValues,
        'ACTIVE_CLI_TOOL': selectedTool,
        'CURSOR_CLI_ENABLED': selectedTool === 'cursor' ? 'true' : 'false',
        'CLAUDE_CLI_ENABLED': selectedTool === 'claude' ? 'true' : 'false',
        // 기타 도구 비활성화
        'AIDER_ENABLED': 'false',
        'FABRIC_ENABLED': 'false',
      };
      
      console.log('[DEBUG] Saving CLI env variables:', updatedCliEnv);
      console.log('[DEBUG] WORKSPACE_PATH:', updatedCliEnv['WORKSPACE_PATH']);
      
      await saveCliMutation.mutateAsync(updatedCliEnv);
      setCliEnvValues(updatedCliEnv);

      setSaveMessage({ type: 'success', text: '외부망 환경 설정이 저장되었습니다.' });
      setShowExternalNetworkModal(false);
    } catch (error) {
      setSaveMessage({ type: 'error', text: '설정 저장 중 오류가 발생했습니다.' });
    }
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
  const handleTestCliConnection = () => {
    const tool = cliEnvValues['ACTIVE_CLI_TOOL'] || 'cursor';
    const token = cliEnvValues['CURSOR_CLI_AUTH_TOKEN'];
    const apiKey = cliEnvValues['ANTHROPIC_API_KEY'];
    
    testCliConnectionMutation.mutate({ tool, token, apiKey });
  };

  const handleOpenWorkspace = () => {
    const path = cliEnvValues['WORKSPACE_PATH'];
    if (path) {
      openWorkspaceMutation.mutate(path);
    }
  };

  // 선택된 서비스 로그 자동 로드
  useEffect(() => {
    if (selectedService) {
      const loadLogs = async () => {
        try {
          const logs = await api.getServiceLogs(selectedService, 100);
          setServiceLogs(prev => ({ ...prev, [selectedService]: logs }));
        } catch (error) {
          console.error('Failed to load service logs:', error);
        }
      };
      
      loadLogs();
      
      // 5초마다 로그 갱신
      const interval = setInterval(loadLogs, 5000);
      return () => clearInterval(interval);
    }
  }, [selectedService]);

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

  // 환경 유형 판별
  const getEnvironmentType = () => {
    const isWorkerServerSet = !!workerEnvValues['WORKER_SERVER_URL'];
    const activeCli = cliEnvValues['ACTIVE_CLI_TOOL'];
    
    if (isWorkerServerSet && (activeCli === 'aider' || activeCli === 'fabric')) {
      return { type: 'internal', label: '폐쇄망 (Internal)', color: 'blue', icon: Shield };
    }
    
    if (!isWorkerServerSet && (activeCli === 'cursor' || activeCli === 'claude')) {
      return { type: 'external', label: '외부망 (External)', color: 'green', icon: Globe };
    }
    
    return { type: 'custom', label: '사용자 정의 (Custom)', color: 'gray', icon: Settings };
  };

  const currentEnv = getEnvironmentType();

  // LLM 표시 이름 계산
  const getLlmDisplayName = () => {
    const provider = llmEnvValues['ACTIVE_LLM_PROVIDER'];
    const url = llmEnvValues['GPT_OSS_API_URL'];
    
    if (provider === 'qwen3') return 'QWEN3 (내부)';
    if (provider === 'gpt_oss') {
      if (url && (url.includes('openai.com') || url.includes('api.openai'))) return 'OpenAI (외부)';
      return 'GPT 호환 (내부/외부)';
    }
    return '알 수 없음';
  };

  const tabs = [
    { id: 'environment', label: '환경 설정', icon: Zap },
    { id: 'localserver', label: '로컬 서버', icon: Activity },
    { id: 'rules', label: '변환 규칙', icon: BookOpen },
    { id: 'llm', label: 'LLM 모델', icon: Settings },
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
        <div className={`p-4 rounded-lg flex items-start gap-2 ${
          saveMessage.type === 'success' ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'
        }`}>
          {saveMessage.type === 'success' ? (
            <CheckCircle className="h-5 w-5 mt-0.5 flex-shrink-0" />
          ) : (
            <AlertCircle className="h-5 w-5 mt-0.5 flex-shrink-0" />
          )}
          <span className="whitespace-pre-wrap">{saveMessage.text}</span>
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

      {/* 환경 설정 (폐쇄망/외부망) */}
      {activeTab === 'environment' && (
        <div className="space-y-6">
          {/* 현재 환경 배지 */}
          <div className={`flex items-center gap-3 p-4 rounded-lg border ${
            currentEnv.type === 'internal' ? 'bg-blue-50 border-blue-200 text-blue-800' :
            currentEnv.type === 'external' ? 'bg-green-50 border-green-200 text-green-800' :
            'bg-gray-50 border-gray-200 text-gray-800'
          }`}>
            <div className={`p-2 rounded-full ${
              currentEnv.type === 'internal' ? 'bg-blue-100' :
              currentEnv.type === 'external' ? 'bg-green-100' :
              'bg-gray-200'
            }`}>
              <currentEnv.icon className="h-6 w-6" />
            </div>
            <div>
              <p className="text-xs font-medium opacity-70">현재 감지된 환경</p>
              <h3 className="text-lg font-bold flex items-center gap-2">
                {currentEnv.label}
                {currentEnv.type === 'custom' && (
                  <span className="text-xs font-normal px-2 py-0.5 bg-gray-200 rounded-full">
                    설정 확인 필요
                  </span>
                )}
              </h3>
            </div>
          </div>

          {/* 현재 환경 상태 */}
          <div className="bg-white shadow rounded-lg p-6">
            <h3 className="font-semibold text-gray-900 mb-4">현재 환경 설정 상태</h3>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div className="text-center p-4 bg-gray-50 rounded-lg">
                <p className="text-xs text-gray-500 mb-1">활성 LLM</p>
                <p className="font-semibold text-gray-900">
                  {getLlmDisplayName()}
                </p>
              </div>
              <div className="text-center p-4 bg-gray-50 rounded-lg">
                <p className="text-xs text-gray-500 mb-1">CLI 도구</p>
                <p className="font-semibold text-gray-900">
                  {cliEnvValues['AIDER_ENABLED'] === 'true' ? 'AIDER 활성' : '비활성'}
                </p>
              </div>
              <div className="text-center p-4 bg-gray-50 rounded-lg">
                <p className="text-xs text-gray-500 mb-1">워크스페이스</p>
                <p className="font-semibold text-gray-900 truncate" title={cliEnvValues['WORKSPACE_PATH'] || '설정 안됨'}>
                  {cliEnvValues['WORKSPACE_PATH'] ? '설정됨' : '설정 안됨'}
                </p>
              </div>
              <div className="text-center p-4 bg-gray-50 rounded-lg">
                <p className="text-xs text-gray-500 mb-1">워커 서버</p>
                <p className="font-semibold text-gray-900 truncate" title={workerEnvValues['WORKER_SERVER_URL'] || '로컬'}>
                  {workerEnvValues['WORKER_SERVER_URL'] ? '외부 서버' : '로컬'}
                </p>
              </div>
            </div>
          </div>

          {/* 설정 변경 버튼 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* 폐쇄망 설정 */}
            <div className="bg-white shadow-lg rounded-xl p-6 border-2 border-transparent hover:border-blue-500 transition-all">
              <div className="flex items-start gap-4">
                <div className="p-3 rounded-xl bg-blue-100">
                  <Shield className="h-8 w-8 text-blue-600" />
                </div>
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-gray-900 mb-1">폐쇄망 환경 설정</h3>
                  <p className="text-sm text-gray-500 mb-4">
                    내부망에서 운영되는 LLM 워커 서버와 파일 서버를 설정합니다. 외부 인터넷 연결이 없는 환경입니다.
                  </p>
                  <button
                    onClick={() => setShowClosedNetworkModal(true)}
                    className="w-full py-2 px-4 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors"
                  >
                    워커 서버 설정하기
                  </button>
                </div>
              </div>
            </div>

            {/* 외부망 설정 */}
            <div className="bg-white shadow-lg rounded-xl p-6 border-2 border-transparent hover:border-green-500 transition-all">
              <div className="flex items-start gap-4">
                <div className="p-3 rounded-xl bg-green-100">
                  <Globe className="h-8 w-8 text-green-600" />
                </div>
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-gray-900 mb-1">외부망 환경 설정</h3>
                  <p className="text-sm text-gray-500 mb-4">
                    외부 LLM API와 연동하고 CLI 도구 및 로컬 워크스페이스 경로를 설정합니다.
                  </p>
                  <button
                    onClick={() => setShowExternalNetworkModal(true)}
                    className="w-full py-2 px-4 bg-green-600 text-white rounded-lg font-medium hover:bg-green-700 transition-colors"
                  >
                    CLI 및 워크스페이스 설정하기
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 로컬 서버 관리 */}
      {activeTab === 'localserver' && (
        <div className="space-y-6">
          <div className="bg-white shadow rounded-lg p-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <Activity className="h-5 w-5" />
              로컬 서버 관리
            </h3>
            <p className="text-sm text-gray-600 mb-6">
              로컬에서 실행되는 Airflow와 CLI Service를 관리합니다.
            </p>

            {/* 서비스 목록 */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Airflow */}
              <div className="border border-gray-200 rounded-lg p-6">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <div className={`p-2 rounded-lg ${
                      servicesStatus?.airflow?.running 
                        ? 'bg-green-100 text-green-600' 
                        : 'bg-gray-100 text-gray-400'
                    }`}>
                      <Server className="h-6 w-6" />
                    </div>
                    <div>
                      <h4 className="font-semibold text-gray-900">Airflow</h4>
                      <p className="text-xs text-gray-500">워크플로우 오케스트레이션</p>
                    </div>
                  </div>
                  <div className={`px-2 py-1 rounded text-xs font-medium ${
                    servicesStatus?.airflow?.running
                      ? 'bg-green-100 text-green-700'
                      : 'bg-gray-100 text-gray-600'
                  }`}>
                    {servicesStatus?.airflow?.running ? 'Running' : 'Stopped'}
                  </div>
                </div>

                <div className="space-y-2 text-sm mb-4">
                  <div className="flex justify-between">
                    <span className="text-gray-600">타입:</span>
                    <span className="font-mono text-gray-900">
                      {servicesStatus?.airflow?.type || 'docker'}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">URL:</span>
                    <a 
                      href={servicesStatus?.airflow?.url || 'http://localhost:8081'}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="font-mono text-indigo-600 hover:underline"
                    >
                      {servicesStatus?.airflow?.url || 'http://localhost:8081'}
                    </a>
                  </div>
                  {servicesStatus?.airflow?.containers && servicesStatus.airflow.containers.length > 0 && (
                    <div className="mt-2 pt-2 border-t">
                      <span className="text-xs text-gray-500">컨테이너:</span>
                      {servicesStatus.airflow.containers.map((container: string, idx: number) => (
                        <div key={idx} className="text-xs font-mono text-gray-700 mt-1">
                          {container}
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="flex gap-2">
                  <button
                    onClick={() => startServiceMutation.mutate('airflow')}
                    disabled={servicesStatus?.airflow?.running || startServiceMutation.isPending}
                    className="flex-1 px-3 py-2 text-sm font-medium text-white bg-green-600 rounded-md hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                  >
                    {startServiceMutation.isPending ? (
                      <>
                        <RefreshCw className="h-4 w-4 animate-spin" />
                        시작 중...
                      </>
                    ) : (
                      <>
                        <Zap className="h-4 w-4" />
                        시작
                      </>
                    )}
                  </button>
                  <button
                    onClick={() => stopServiceMutation.mutate('airflow')}
                    disabled={!servicesStatus?.airflow?.running || stopServiceMutation.isPending}
                    className="flex-1 px-3 py-2 text-sm font-medium text-white bg-red-600 rounded-md hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                  >
                    {stopServiceMutation.isPending ? (
                      <>
                        <RefreshCw className="h-4 w-4 animate-spin" />
                        중지 중...
                      </>
                    ) : (
                      <>
                        <StopCircle className="h-4 w-4" />
                        중지
                      </>
                    )}
                  </button>
                  <button
                    onClick={() => setSelectedService('airflow')}
                    className="px-3 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 flex items-center gap-2"
                  >
                    <Terminal className="h-4 w-4" />
                    로그
                  </button>
                </div>
              </div>

              {/* CLI Service */}
              <div className="border border-gray-200 rounded-lg p-6">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <div className={`p-2 rounded-lg ${
                      servicesStatus?.['cli-service']?.running 
                        ? 'bg-green-100 text-green-600' 
                        : 'bg-gray-100 text-gray-400'
                    }`}>
                      <Terminal className="h-6 w-6" />
                    </div>
                    <div>
                      <h4 className="font-semibold text-gray-900">CLI Service</h4>
                      <p className="text-xs text-gray-500">Aider/Fabric CLI 래퍼</p>
                    </div>
                  </div>
                  <div className={`px-2 py-1 rounded text-xs font-medium ${
                    servicesStatus?.['cli-service']?.running
                      ? 'bg-green-100 text-green-700'
                      : 'bg-gray-100 text-gray-600'
                  }`}>
                    {servicesStatus?.['cli-service']?.running ? 'Running' : 'Stopped'}
                  </div>
                </div>

                <div className="space-y-2 text-sm mb-4">
                  <div className="flex justify-between">
                    <span className="text-gray-600">타입:</span>
                    <span className="font-mono text-gray-900">
                      {servicesStatus?.['cli-service']?.type || 'python'}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">URL:</span>
                    <a 
                      href={servicesStatus?.['cli-service']?.url || 'http://localhost:8000'}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="font-mono text-indigo-600 hover:underline"
                    >
                      {servicesStatus?.['cli-service']?.url || 'http://localhost:8000'}
                    </a>
                  </div>
                  {servicesStatus?.['cli-service']?.pids && servicesStatus['cli-service'].pids.length > 0 && (
                    <div className="mt-2 pt-2 border-t">
                      <span className="text-xs text-gray-500">PID:</span>
                      <span className="text-xs font-mono text-gray-700 ml-2">
                        {servicesStatus['cli-service'].pids.join(', ')}
                      </span>
                    </div>
                  )}
                </div>

                <div className="flex gap-2">
                  <button
                    onClick={() => startServiceMutation.mutate('cli-service')}
                    disabled={servicesStatus?.['cli-service']?.running || startServiceMutation.isPending}
                    className="flex-1 px-3 py-2 text-sm font-medium text-white bg-green-600 rounded-md hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                  >
                    {startServiceMutation.isPending ? (
                      <>
                        <RefreshCw className="h-4 w-4 animate-spin" />
                        시작 중...
                      </>
                    ) : (
                      <>
                        <Zap className="h-4 w-4" />
                        시작
                      </>
                    )}
                  </button>
                  <button
                    onClick={() => stopServiceMutation.mutate('cli-service')}
                    disabled={!servicesStatus?.['cli-service']?.running || stopServiceMutation.isPending}
                    className="flex-1 px-3 py-2 text-sm font-medium text-white bg-red-600 rounded-md hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                  >
                    {stopServiceMutation.isPending ? (
                      <>
                        <RefreshCw className="h-4 w-4 animate-spin" />
                        중지 중...
                      </>
                    ) : (
                      <>
                        <StopCircle className="h-4 w-4" />
                        중지
                      </>
                    )}
                  </button>
                  <button
                    onClick={() => setSelectedService('cli-service')}
                    className="px-3 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 flex items-center gap-2"
                  >
                    <Terminal className="h-4 w-4" />
                    로그
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* 로그 뷰어 */}
          {selectedService && (
            <div className="bg-white shadow rounded-lg p-6">
              <div className="flex items-center justify-between mb-4">
                <h4 className="font-semibold text-gray-900 flex items-center gap-2">
                  <Terminal className="h-5 w-5" />
                  {selectedService === 'airflow' ? 'Airflow' : 'CLI Service'} 로그
                </h4>
                <div className="flex gap-2">
                  <button
                    onClick={async () => {
                      const logs = await api.getServiceLogs(selectedService, 100);
                      setServiceLogs(prev => ({ ...prev, [selectedService]: logs }));
                    }}
                    className="px-3 py-1 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 flex items-center gap-1"
                  >
                    <RefreshCw className="h-4 w-4" />
                    새로고침
                  </button>
                  <button
                    onClick={() => setSelectedService(null)}
                    className="px-3 py-1 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200"
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>
              </div>
              
              <div className="bg-gray-900 text-gray-100 rounded-lg p-4 font-mono text-xs overflow-x-auto max-h-96 overflow-y-auto">
                {serviceLogs[selectedService] && serviceLogs[selectedService].length > 0 ? (
                  serviceLogs[selectedService].map((log, idx) => (
                    <div key={idx} className="whitespace-pre-wrap">
                      {log}
                    </div>
                  ))
                ) : (
                  <div className="text-gray-400 text-center py-8">
                    로그가 없습니다. 서비스를 시작하면 로그가 표시됩니다.
                  </div>
                )}
              </div>
            </div>
          )}
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

      {/* 폐쇄망 설정 모달 */}
      {showClosedNetworkModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 overflow-y-auto">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-2xl p-6 my-8">
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-lg font-bold text-gray-900">폐쇄망 환경 설정</h3>
              <button onClick={() => setShowClosedNetworkModal(false)} className="text-gray-400 hover:text-gray-600">
                <X className="h-5 w-5" />
              </button>
            </div>
            
            <div className="space-y-6">
              <div className="p-4 bg-blue-50 rounded-lg text-sm text-blue-800">
                인터넷 연결이 없는 폐쇄망 환경입니다. 사내 구축된 LLM 서버와 CLI 도구를 설정합니다.
              </div>

              {/* 1. 워커 서버 설정 */}
              <div className="border-b pb-6">
                <h4 className="text-md font-semibold text-gray-900 mb-3 flex items-center gap-2">
                  <Server className="h-4 w-4" /> 워커 서버 설정
                </h4>
                <div className="space-y-3">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      워커 서버 URL
                    </label>
                    <input
                      type="text"
                      value={workerEnvValues['WORKER_SERVER_URL'] || ''}
                      onChange={(e) => handleWorkerChange('WORKER_SERVER_URL', e.target.value)}
                      placeholder="http://192.168.x.x:port"
                      className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                    />
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      id="fileServerEnabled"
                      checked={workerEnvValues['FILE_SERVER_ENABLED'] === 'true'}
                      onChange={(e) => handleWorkerChange('FILE_SERVER_ENABLED', e.target.checked ? 'true' : 'false')}
                      className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                    />
                    <label htmlFor="fileServerEnabled" className="text-sm font-medium text-gray-700">
                      파일 서버 활성화 (워커 서버에 파일 저장)
                    </label>
                  </div>
                </div>
              </div>

              {/* 2. CLI 도구 선택 */}
              <div className="border-b pb-6">
                <h4 className="text-md font-semibold text-gray-900 mb-3 flex items-center gap-2">
                  <Terminal className="h-4 w-4" /> CLI 도구 선택
                </h4>
                <div className="grid grid-cols-2 gap-4">
                  <label className={`relative flex cursor-pointer rounded-lg border p-4 shadow-sm focus:outline-none ${
                    cliEnvValues['ACTIVE_CLI_TOOL'] === 'aider' ? 'border-indigo-600 ring-2 ring-indigo-600' : 'border-gray-300'
                  }`}>
                    <input
                      type="radio"
                      name="cli-tool"
                      value="aider"
                      checked={cliEnvValues['ACTIVE_CLI_TOOL'] === 'aider'}
                      onChange={(e) => handleCliChange('ACTIVE_CLI_TOOL', e.target.value)}
                      className="sr-only"
                    />
                    <span className="flex flex-1">
                      <span className="flex flex-col">
                        <span className="block text-sm font-medium text-gray-900">Aider CLI</span>
                        <span className="mt-1 flex items-center text-sm text-gray-500">강력한 코드 편집 기능</span>
                      </span>
                    </span>
                    <CheckCircle className={`h-5 w-5 ${cliEnvValues['ACTIVE_CLI_TOOL'] === 'aider' ? 'text-indigo-600' : 'text-transparent'}`} />
                  </label>

                  <label className={`relative flex cursor-pointer rounded-lg border p-4 shadow-sm focus:outline-none ${
                    cliEnvValues['ACTIVE_CLI_TOOL'] === 'fabric' ? 'border-indigo-600 ring-2 ring-indigo-600' : 'border-gray-300'
                  }`}>
                    <input
                      type="radio"
                      name="cli-tool"
                      value="fabric"
                      checked={cliEnvValues['ACTIVE_CLI_TOOL'] === 'fabric'}
                      onChange={(e) => handleCliChange('ACTIVE_CLI_TOOL', e.target.value)}
                      className="sr-only"
                    />
                    <span className="flex flex-1">
                      <span className="flex flex-col">
                        <span className="block text-sm font-medium text-gray-900">Fabric CLI</span>
                        <span className="mt-1 flex items-center text-sm text-gray-500">패턴 기반 분석 도구</span>
                      </span>
                    </span>
                    <CheckCircle className={`h-5 w-5 ${cliEnvValues['ACTIVE_CLI_TOOL'] === 'fabric' ? 'text-indigo-600' : 'text-transparent'}`} />
                  </label>
                </div>
              </div>

              {/* 3. LLM 모델 선택 및 설정 */}
              <div>
                <h4 className="text-md font-semibold text-gray-900 mb-3 flex items-center gap-2">
                  <Settings className="h-4 w-4" /> LLM 모델 설정
                </h4>
                
                {/* LLM 선택 */}
                <div className="grid grid-cols-2 gap-4 mb-4">
                  <label className={`relative flex cursor-pointer rounded-lg border p-4 shadow-sm focus:outline-none ${
                    llmEnvValues['ACTIVE_LLM_PROVIDER'] === 'qwen3' ? 'border-indigo-600 ring-2 ring-indigo-600' : 'border-gray-300'
                  }`}>
                    <input
                      type="radio"
                      name="llm-provider"
                      value="qwen3"
                      checked={llmEnvValues['ACTIVE_LLM_PROVIDER'] === 'qwen3'}
                      onChange={(e) => handleLlmChange('ACTIVE_LLM_PROVIDER', e.target.value)}
                      className="sr-only"
                    />
                    <span className="flex flex-1">
                      <span className="flex flex-col">
                        <span className="block text-sm font-medium text-gray-900">QWEN3 VL</span>
                        <span className="mt-1 flex items-center text-sm text-gray-500">사내 구축형 (Vision Language)</span>
                      </span>
                    </span>
                    <CheckCircle className={`h-5 w-5 ${llmEnvValues['ACTIVE_LLM_PROVIDER'] === 'qwen3' ? 'text-indigo-600' : 'text-transparent'}`} />
                  </label>

                  <label className={`relative flex cursor-pointer rounded-lg border p-4 shadow-sm focus:outline-none ${
                    llmEnvValues['ACTIVE_LLM_PROVIDER'] === 'gpt_oss' ? 'border-indigo-600 ring-2 ring-indigo-600' : 'border-gray-300'
                  }`}>
                    <input
                      type="radio"
                      name="llm-provider"
                      value="gpt_oss"
                      checked={llmEnvValues['ACTIVE_LLM_PROVIDER'] === 'gpt_oss'}
                      onChange={(e) => handleLlmChange('ACTIVE_LLM_PROVIDER', e.target.value)}
                      className="sr-only"
                    />
                    <span className="flex flex-1">
                      <span className="flex flex-col">
                        <span className="block text-sm font-medium text-gray-900">GPT OSS</span>
                        <span className="mt-1 flex items-center text-sm text-gray-500">사내 구축형 (OpenAI 호환)</span>
                      </span>
                    </span>
                    <CheckCircle className={`h-5 w-5 ${llmEnvValues['ACTIVE_LLM_PROVIDER'] === 'gpt_oss' ? 'text-indigo-600' : 'text-transparent'}`} />
                  </label>
                </div>

                {/* 상세 설정 필드 */}
                <div className="bg-gray-50 p-4 rounded-lg space-y-3">
                  {llmEnvValues['ACTIVE_LLM_PROVIDER'] === 'qwen3' ? (
                    <>
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">QWEN3 API URL</label>
                        <input
                          type="text"
                          value={llmEnvValues['QWEN3_API_URL'] || ''}
                          onChange={(e) => handleLlmChange('QWEN3_API_URL', e.target.value)}
                          className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm"
                        />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">API Key</label>
                        <input
                          type="password"
                          value={llmEnvValues['QWEN3_API_KEY'] || ''}
                          onChange={(e) => handleLlmChange('QWEN3_API_KEY', e.target.value)}
                          className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm"
                        />
                      </div>
                    </>
                  ) : (
                    <>
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">GPT OSS API URL</label>
                        <input
                          type="text"
                          value={llmEnvValues['GPT_OSS_API_URL'] || ''}
                          onChange={(e) => handleLlmChange('GPT_OSS_API_URL', e.target.value)}
                          className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm"
                        />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">API Key</label>
                        <input
                          type="password"
                          value={llmEnvValues['GPT_OSS_API_KEY'] || ''}
                          onChange={(e) => handleLlmChange('GPT_OSS_API_KEY', e.target.value)}
                          className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm"
                        />
                      </div>
                    </>
                  )}
                </div>
              </div>
            </div>

            <div className="mt-6 flex justify-end gap-3">
              <button
                onClick={() => setShowClosedNetworkModal(false)}
                className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
              >
                취소
              </button>
              <button
                onClick={handleSaveClosedNetwork}
                className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700"
              >
                설정 저장
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 외부망 설정 모달 */}
      {showExternalNetworkModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-lg p-6">
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-lg font-bold text-gray-900">외부망 환경 설정</h3>
              <button onClick={() => setShowExternalNetworkModal(false)} className="text-gray-400 hover:text-gray-600">
                <X className="h-5 w-5" />
              </button>
            </div>
            
            <div className="space-y-6">
              <div className="p-4 bg-green-50 rounded-lg text-sm text-green-800">
                인터넷이 연결된 외부망 환경입니다. Cursor CLI를 사용하여 작업을 수행합니다.
              </div>

              {/* 1. CLI 도구 정보 */}
              <div className="border-b pb-6">
                <h4 className="text-md font-semibold text-gray-900 mb-3 flex items-center gap-2">
                  <Terminal className="h-4 w-4" /> CLI 도구 설정
                </h4>
                
                <div className="grid grid-cols-2 gap-3 mb-4">
                  <label className={`relative flex cursor-pointer rounded-lg border p-3 shadow-sm focus:outline-none ${
                    (!cliEnvValues['ACTIVE_CLI_TOOL'] || cliEnvValues['ACTIVE_CLI_TOOL'] === 'cursor') ? 'border-green-600 ring-2 ring-green-600' : 'border-gray-300'
                  }`}>
                    <input
                      type="radio"
                      name="ext-cli-tool"
                      value="cursor"
                      checked={!cliEnvValues['ACTIVE_CLI_TOOL'] || cliEnvValues['ACTIVE_CLI_TOOL'] === 'cursor'}
                      onChange={(e) => handleCliChange('ACTIVE_CLI_TOOL', e.target.value)}
                      className="sr-only"
                    />
                    <div className="flex-1 text-center">
                      <span className="block text-sm font-medium text-gray-900">Cursor</span>
                    </div>
                    <CheckCircle className={`absolute top-2 right-2 h-4 w-4 ${(!cliEnvValues['ACTIVE_CLI_TOOL'] || cliEnvValues['ACTIVE_CLI_TOOL'] === 'cursor') ? 'text-green-600' : 'hidden'}`} />
                  </label>

                  <label className={`relative flex cursor-pointer rounded-lg border p-3 shadow-sm focus:outline-none ${
                    cliEnvValues['ACTIVE_CLI_TOOL'] === 'claude' ? 'border-green-600 ring-2 ring-green-600' : 'border-gray-300'
                  }`}>
                    <input
                      type="radio"
                      name="ext-cli-tool"
                      value="claude"
                      checked={cliEnvValues['ACTIVE_CLI_TOOL'] === 'claude'}
                      onChange={(e) => handleCliChange('ACTIVE_CLI_TOOL', e.target.value)}
                      className="sr-only"
                    />
                    <div className="flex-1 text-center">
                      <span className="block text-sm font-medium text-gray-900">Claude</span>
                    </div>
                    <CheckCircle className={`absolute top-2 right-2 h-4 w-4 ${cliEnvValues['ACTIVE_CLI_TOOL'] === 'claude' ? 'text-green-600' : 'hidden'}`} />
                  </label>
                </div>
                
                {/* Cursor 설정 필드 */}
                {(cliEnvValues['ACTIVE_CLI_TOOL'] === 'cursor' || !cliEnvValues['ACTIVE_CLI_TOOL']) && (
                  <div className="space-y-3 bg-gray-50 p-4 rounded-lg">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        Cursor CLI 인증 토큰
                      </label>
                      <div className="relative">
                        <input
                          type={showCursorToken ? "text" : "password"}
                          value={cliEnvValues['CURSOR_CLI_AUTH_TOKEN'] || ''}
                          onChange={(e) => handleCliChange('CURSOR_CLI_AUTH_TOKEN', e.target.value)}
                          placeholder="필요한 경우 토큰 입력"
                          className="w-full rounded-md border-gray-300 shadow-sm focus:border-green-500 focus:ring-green-500 text-sm pr-10"
                        />
                        <button
                          type="button"
                          onClick={() => setShowCursorToken(!showCursorToken)}
                          className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
                        >
                          {showCursorToken ? (
                            <EyeOff className="h-4 w-4" />
                          ) : (
                            <Eye className="h-4 w-4" />
                          )}
                        </button>
                      </div>
                      {cliEnvValues['CURSOR_CLI_AUTH_TOKEN'] && (
                        <p className="text-xs text-green-600 mt-1 flex items-center gap-1">
                          <CheckCircle className="h-3 w-3" />
                          토큰이 설정되어 있습니다
                        </p>
                      )}
                      <p className="text-xs text-gray-500 mt-1">
                        Cursor CLI 사용에 필요한 인증 토큰만 입력하면 됩니다. 계정이 변경되면 토큰을 새로 입력하세요.
                      </p>
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        사용할 AI 모델
                      </label>
                      <select
                        value={cliEnvValues['CURSOR_CLI_MODEL'] || 'gpt-4'}
                        onChange={(e) => handleCliChange('CURSOR_CLI_MODEL', e.target.value)}
                        className="w-full rounded-md border-gray-300 shadow-sm focus:border-green-500 focus:ring-green-500 text-sm"
                      >
                        {CURSOR_MODELS.map(model => (
                          <option key={model.value} value={model.value}>
                            {model.label}
                          </option>
                        ))}
                      </select>
                      <p className="text-xs text-gray-500 mt-1">
                        Cursor CLI에서 사용할 AI 모델을 선택하세요.
                      </p>
                    </div>
                  </div>
                )}

                {/* Claude 설정 필드 */}
                {cliEnvValues['ACTIVE_CLI_TOOL'] === 'claude' && (
                  <div className="space-y-3 bg-gray-50 p-4 rounded-lg">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        Anthropic API Key <span className="text-red-500">*</span>
                      </label>
                      <div className="relative">
                        <input
                          type={showClaudeApiKey ? "text" : "password"}
                          value={cliEnvValues['ANTHROPIC_API_KEY'] || ''}
                          onChange={(e) => handleCliChange('ANTHROPIC_API_KEY', e.target.value)}
                          placeholder="sk-ant-..."
                          className="w-full rounded-md border-gray-300 shadow-sm focus:border-green-500 focus:ring-green-500 text-sm pr-10"
                        />
                        <button
                          type="button"
                          onClick={() => setShowClaudeApiKey(!showClaudeApiKey)}
                          className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
                        >
                          {showClaudeApiKey ? (
                            <EyeOff className="h-4 w-4" />
                          ) : (
                            <Eye className="h-4 w-4" />
                          )}
                        </button>
                      </div>
                      {cliEnvValues['ANTHROPIC_API_KEY'] && (
                        <p className="text-xs text-green-600 mt-1 flex items-center gap-1">
                          <CheckCircle className="h-3 w-3" />
                          API Key가 설정되어 있습니다
                        </p>
                      )}
                      <p className="text-xs text-gray-500 mt-1">
                        Claude CLI 사용을 위해 유효한 API Key가 필요합니다.
                      </p>
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        사용할 AI 모델
                      </label>
                      <select
                        value={cliEnvValues['CLAUDE_CLI_MODEL'] || 'claude-3-5-sonnet-20240620'}
                        onChange={(e) => handleCliChange('CLAUDE_CLI_MODEL', e.target.value)}
                        className="w-full rounded-md border-gray-300 shadow-sm focus:border-green-500 focus:ring-green-500 text-sm"
                      >
                        {CLAUDE_MODELS.map(model => (
                          <option key={model.value} value={model.value}>
                            {model.label}
                          </option>
                        ))}
                      </select>
                      <p className="text-xs text-gray-500 mt-1">
                        사용할 Claude 모델 버전을 선택하세요.
                      </p>
                    </div>
                  </div>
                )}
              </div>

              {/* 2. 워크스페이스 설정 */}
              <div>
                <h4 className="text-md font-semibold text-gray-900 mb-3 flex items-center gap-2">
                  <HardDrive className="h-4 w-4" /> 워크스페이스 설정
                </h4>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    워크스페이스 경로 (Workspace Path)
                  </label>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={cliEnvValues['WORKSPACE_PATH'] || ''}
                      onChange={(e) => handleCliChange('WORKSPACE_PATH', e.target.value)}
                      placeholder="/Users/username/projects/..."
                      className="flex-1 rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                    />
                    <button
                      onClick={handleOpenWorkspace}
                      disabled={!cliEnvValues['WORKSPACE_PATH'] || openWorkspaceMutation.isPending}
                      className="px-3 py-2 text-sm font-medium text-gray-700 bg-gray-100 border border-gray-300 rounded-md hover:bg-gray-200 disabled:opacity-50 flex items-center gap-1"
                      title="워크스페이스 폴더 열기"
                    >
                      <HardDrive className="h-4 w-4" />
                      열기
                    </button>
                  </div>
                  {cliEnvValues['WORKSPACE_PATH'] && (
                    <p className="text-xs text-green-600 mt-1 flex items-center gap-1">
                      <CheckCircle className="h-3 w-3" />
                      경로가 설정되어 있습니다: {cliEnvValues['WORKSPACE_PATH']}
                    </p>
                  )}
                  <p className="text-xs text-gray-500 mt-1">
                    변환할 코드가 위치한 로컬 프로젝트 경로를 지정하세요. 별도 서버 없이 이 경로에서 작업이 수행됩니다.
                  </p>
                </div>
              </div>

              {/* 3. 백엔드 LLM 연동 설정 (선택 사항) */}
              {(cliEnvValues['ACTIVE_CLI_TOOL'] !== 'cursor' && cliEnvValues['ACTIVE_CLI_TOOL'] !== 'claude') && (
                <div>
                  <h4 className="text-md font-semibold text-gray-900 mb-3 flex items-center gap-2">
                    <Settings className="h-4 w-4" /> 백엔드 LLM 연동 설정
                  </h4>
                  <div className="bg-gray-50 p-4 rounded-lg space-y-3">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm font-medium text-gray-900">OpenAI API 사용</span>
                      <span className="text-xs text-gray-500">외부망 기본값</span>
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">OpenAI API Key</label>
                      <input
                        type="password"
                        value={llmEnvValues['GPT_OSS_API_KEY'] || ''}
                        onChange={(e) => handleLlmChange('GPT_OSS_API_KEY', e.target.value)}
                        placeholder="sk-..."
                        className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm"
                      />
                      <p className="text-xs text-gray-500 mt-1">
                        OpenAI CLI를 사용하려면 API Key가 필수입니다.
                      </p>
                    </div>
                  </div>
                </div>
              )}
            </div>

            <div className="mt-6 flex justify-between gap-3">
              <button
                onClick={handleTestCliConnection}
                disabled={testCliConnectionMutation.isPending}
                className="px-4 py-2 text-sm font-medium text-indigo-700 bg-indigo-50 border border-indigo-200 rounded-md hover:bg-indigo-100 flex items-center gap-2"
              >
                {testCliConnectionMutation.isPending ? (
                  <>
                    <RefreshCw className="h-4 w-4 animate-spin" />
                    테스트 중...
                  </>
                ) : (
                  <>
                    <Zap className="h-4 w-4" />
                    연결 테스트
                  </>
                )}
              </button>
              <div className="flex gap-3">
                <button
                  onClick={() => setShowExternalNetworkModal(false)}
                  className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
                >
                  취소
                </button>
                <button
                  onClick={handleSaveExternalNetwork}
                  className="px-4 py-2 text-sm font-medium text-white bg-green-600 rounded-md hover:bg-green-700"
                >
                  설정 저장
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
