import { useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useDropzone } from 'react-dropzone';
import { Upload as UploadIcon, FileCode, X, Loader2, CheckCircle, Settings, BookOpen } from 'lucide-react';
import { api } from '../lib/api';

interface AvailableLanguage {
  id: string;
  name: string;
}

export default function Upload() {
  const navigate = useNavigate();
  const [files, setFiles] = useState<File[]>([]);
  const [jobName, setJobName] = useState('');
  const [selectedLanguage, setSelectedLanguage] = useState<string>('');
  const [jdbcConfig, setJdbcConfig] = useState({
    enabled: false,
    url: '',
    username: '',
    password: '',
    driver: 'oracle.jdbc.OracleDriver',
  });
  const [showJdbc, setShowJdbc] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  // 사용 가능한 언어 목록 조회
  const { data: languages, isLoading: languagesLoading } = useQuery<AvailableLanguage[]>({
    queryKey: ['availableLanguages'],
    queryFn: api.listAvailableLanguages,
  });

  // 진행 중인 작업 확인
  const { data: ongoingJobs, isLoading: jobsLoading } = useQuery({
    queryKey: ['ongoingJobs'],
    queryFn: async () => {
      const allJobs = await api.getAllJobs();
      return allJobs.filter((job: any) => 
        ['PENDING', 'ANALYZING', 'CONVERTING', 'COMPILING', 'TESTING'].includes(job.status)
      );
    },
    refetchInterval: 5000, // 5초마다 갱신
  });

  const hasOngoingJobs = ongoingJobs && ongoingJobs.length > 0;

  // 첫 번째 언어를 기본값으로 설정
  useEffect(() => {
    if (languages && languages.length > 0 && !selectedLanguage) {
      setSelectedLanguage(languages[0].id);
    }
  }, [languages, selectedLanguage]);

  const uploadMutation = useMutation({
    mutationFn: async () => {
      // 진행 중인 작업 체크
      if (hasOngoingJobs) {
        throw new Error('이미 진행 중인 변환 작업이 있습니다. 작업이 완료될 때까지 기다려주세요.');
      }

      const formData = new FormData();
      files.forEach((file) => {
        formData.append('files', file);
      });
      formData.append('jobName', jobName || files[0]?.name || 'Conversion Job');
      // 선택한 대상 언어 (변환 규칙)
      if (selectedLanguage) {
        formData.append('targetLanguage', selectedLanguage);
      }
      if (jdbcConfig.enabled) {
        formData.append('jdbcConfig', JSON.stringify(jdbcConfig));
      }
      return api.uploadFiles(formData);
    },
    onSuccess: (data) => {
      setUploadError(null);
      navigate(`/jobs/${data.id}`);
    },
    onError: (error: any) => {
      console.error('Upload error:', error);
      const errorMessage = error.response?.data?.message || error.message || '파일 업로드 중 오류가 발생했습니다.';
      setUploadError(errorMessage);
    },
  });

  const onDrop = useCallback((acceptedFiles: File[]) => {
    // C, C++, Pro*C 파일 필터링
    const validExtensions = ['.c', '.h', '.pc', '.cpp', '.cc', '.cxx', '.hpp'];
    const cFiles = acceptedFiles.filter((f) => 
      validExtensions.some(ext => f.name.toLowerCase().endsWith(ext))
    );
    
    setFiles((prev) => [...prev, ...cFiles]);
    if (!jobName && cFiles.length > 0) {
      setJobName(cFiles[0].name.replace(/\.(c|h|pc|cpp|cc|cxx|hpp)$/, ''));
    }
  }, [jobName]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'text/x-c': ['.c', '.h', '.pc', '.cpp', '.cc', '.cxx', '.hpp'],
    },
    // 폴더 업로드 지원 (웹 표준 제한으로 인한 대안)
    // 사용자가 폴더 내 모든 파일을 선택하여 드래그&드롭 가능
  });

  const removeFile = (index: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  };

  // 폴더 선택 핸들러
  const handleFolderSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = Array.from(e.target.files || []);
    const validExtensions = ['.c', '.h', '.pc', '.cpp', '.cc', '.cxx', '.hpp'];
    const cFiles = selectedFiles.filter((f) => 
      validExtensions.some(ext => f.name.toLowerCase().endsWith(ext))
    );
    
    setFiles((prev) => [...prev, ...cFiles]);
    if (!jobName && cFiles.length > 0) {
      // 폴더 이름을 job name으로 설정
      const folderPath = cFiles[0].webkitRelativePath || cFiles[0].name;
      const folderName = folderPath.split('/')[0];
      setJobName(folderName);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (files.length === 0) return;
    uploadMutation.mutate();
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">C/C++ 파일 업로드</h1>
        <p className="text-sm text-gray-500 mt-1">
          변환할 소스 파일을 업로드하세요. (.c, .h, .pc, .cpp, .cc, .cxx, .hpp 지원)
        </p>
        <p className="text-xs text-blue-600 mt-1">
          💡 Tip: 폴더 내 모든 파일을 선택(Ctrl+A/Cmd+A)하여 한번에 업로드할 수 있습니다
        </p>
        
        {/* 진행 중인 작업 알림 */}
        {hasOngoingJobs && (
          <div className="mt-3 p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
            <div className="flex items-center gap-2">
              <Loader2 className="h-5 w-5 text-yellow-600 animate-spin" />
              <div>
                <p className="text-sm font-medium text-yellow-800">
                  진행 중인 변환 작업이 {ongoingJobs.length}개 있습니다
                </p>
                <p className="text-xs text-yellow-700 mt-1">
                  현재 작업이 완료될 때까지 새로운 변환을 시작할 수 없습니다.
                </p>
              </div>
            </div>
          </div>
        )}
        
        {/* 에러 메시지 */}
        {uploadError && (
          <div className="mt-3 p-4 bg-red-50 border border-red-200 rounded-lg">
            <div className="flex items-start gap-2">
              <X className="h-5 w-5 text-red-600 flex-shrink-0 mt-0.5" />
              <div className="flex-1">
                <p className="text-sm font-medium text-red-800">업로드 실패</p>
                <p className="text-xs text-red-700 mt-1">{uploadError}</p>
              </div>
              <button
                onClick={() => setUploadError(null)}
                className="text-red-600 hover:text-red-800"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      <form onSubmit={handleSubmit} className="space-y-6">
        {/* 파일 드롭존 */}
        <div
          {...getRootProps()}
          className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${
            isDragActive
              ? 'border-indigo-500 bg-indigo-50'
              : 'border-gray-300 hover:border-indigo-400 hover:bg-gray-50'
          }`}
        >
          <input {...getInputProps()} />
          <UploadIcon className="mx-auto h-12 w-12 text-gray-400 mb-4" />
          {isDragActive ? (
            <p className="text-indigo-600 font-medium">파일을 여기에 놓으세요</p>
          ) : (
            <>
              <p className="text-gray-600 font-medium">
                클릭하거나 파일을 드래그하여 업로드
              </p>
              <p className="text-sm text-gray-400 mt-1">
                .c, .h, .pc, .cpp, .cc, .cxx, .hpp 파일 지원
              </p>
              <p className="text-xs text-green-600 mt-2">
                📂 여러 파일을 한번에 선택 가능
              </p>
            </>
          )}
        </div>

        {/* 폴더 선택 버튼 */}
        <div className="flex items-center justify-center gap-4">
          <div className="flex-1 border-t border-gray-300"></div>
          <span className="text-sm text-gray-500">또는</span>
          <div className="flex-1 border-t border-gray-300"></div>
        </div>
        
        <div className="flex justify-center">
          <label className="inline-flex items-center gap-2 px-6 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 cursor-pointer transition-colors">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
            </svg>
            <span className="font-medium">폴더 선택</span>
            <input
              type="file"
              className="hidden"
              /* @ts-ignore - webkitdirectory is not in TypeScript types */
              webkitdirectory=""
              directory=""
              multiple
              onChange={handleFolderSelect}
            />
          </label>
        </div>

        {/* 업로드된 파일 목록 */}
        {files.length > 0 && (
          <div className="bg-white rounded-lg shadow p-4">
            <h3 className="font-medium text-gray-900 mb-3">업로드할 파일 ({files.length})</h3>
            <div className="space-y-2">
              {files.map((file, index) => (
                <div
                  key={index}
                  className="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
                >
                  <div className="flex items-center gap-3">
                    <FileCode className="h-5 w-5 text-indigo-500" />
                    <div>
                      <p className="font-medium text-gray-900">{file.name}</p>
                      <p className="text-sm text-gray-500">
                        {(file.size / 1024).toFixed(1)} KB
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => removeFile(index)}
                    className="p-1 hover:bg-gray-200 rounded"
                  >
                    <X className="h-5 w-5 text-gray-400" />
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 변환 설정 */}
        <div className="bg-white rounded-lg shadow p-6 space-y-4">
          <h3 className="font-medium text-gray-900 flex items-center gap-2">
            <Settings className="h-5 w-5 text-gray-500" />
            변환 설정
          </h3>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              작업 이름
            </label>
            <input
              type="text"
              value={jobName}
              onChange={(e) => setJobName(e.target.value)}
              placeholder="예: UserService"
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>

          {/* 대상 언어 선택 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1 flex items-center gap-2">
              <BookOpen className="h-4 w-4" />
              변환 대상 언어
            </label>
            {languagesLoading ? (
              <div className="flex items-center gap-2 text-gray-500 text-sm py-2">
                <Loader2 className="h-4 w-4 animate-spin" />
                언어 목록 로딩 중...
              </div>
            ) : languages && languages.length > 0 ? (
              <>
                <select
                  value={selectedLanguage}
                  onChange={(e) => setSelectedLanguage(e.target.value)}
                  className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                >
                  {languages.map((lang) => (
                    <option key={lang.id} value={lang.id}>
                      {lang.name}
                    </option>
                  ))}
                </select>
                <p className="text-xs text-gray-400 mt-1">
                  선택한 언어의 변환 규칙과 프로젝트 구조에 따라 변환됩니다.
                </p>
              </>
            ) : (
              <div className="p-3 bg-yellow-50 text-yellow-700 rounded-lg text-sm">
                <p className="font-medium">등록된 언어가 없습니다.</p>
                <p className="text-xs mt-1">관리자에게 문의하여 변환 규칙을 추가해주세요.</p>
              </div>
            )}
          </div>

          {/* JDBC 설정 (옵션) */}
          <div className="border-t pt-4 mt-4">
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={showJdbc}
                onChange={(e) => {
                  setShowJdbc(e.target.checked);
                  setJdbcConfig((prev) => ({ ...prev, enabled: e.target.checked }));
                }}
                className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
              />
              <span className="text-sm font-medium text-gray-700">
                JDBC 연결 설정 (선택사항)
              </span>
            </label>

            {showJdbc && (
              <div className="mt-4 grid grid-cols-2 gap-4 p-4 bg-gray-50 rounded-lg">
                <div className="col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    JDBC URL
                  </label>
                  <input
                    type="text"
                    value={jdbcConfig.url}
                    onChange={(e) =>
                      setJdbcConfig((prev) => ({ ...prev, url: e.target.value }))
                    }
                    placeholder="jdbc:oracle:thin:@localhost:1521:orcl"
                    className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    사용자명
                  </label>
                  <input
                    type="text"
                    value={jdbcConfig.username}
                    onChange={(e) =>
                      setJdbcConfig((prev) => ({ ...prev, username: e.target.value }))
                    }
                    className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    비밀번호
                  </label>
                  <input
                    type="password"
                    value={jdbcConfig.password}
                    onChange={(e) =>
                      setJdbcConfig((prev) => ({ ...prev, password: e.target.value }))
                    }
                    className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                  />
                </div>
              </div>
            )}
          </div>
        </div>

        {/* 제출 버튼 */}
        <div className="flex justify-end gap-4">
          <button
            type="button"
            onClick={() => navigate('/')}
            className="px-6 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
          >
            취소
          </button>
          <button
            type="submit"
            disabled={files.length === 0 || uploadMutation.isPending || hasOngoingJobs || !selectedLanguage}
            className="px-6 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            title={
              hasOngoingJobs 
                ? '진행 중인 작업이 있습니다' 
                : !selectedLanguage 
                ? '변환 대상 언어를 선택해주세요'
                : files.length === 0
                ? '파일을 선택해주세요'
                : ''
            }
          >
            {uploadMutation.isPending ? (
              <>
                <Loader2 className="h-5 w-5 animate-spin" />
                업로드 중...
              </>
            ) : hasOngoingJobs ? (
              <>
                <Loader2 className="h-5 w-5 animate-spin" />
                작업 진행 중
              </>
            ) : (
              <>
                <CheckCircle className="h-5 w-5" />
                변환 시작
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
