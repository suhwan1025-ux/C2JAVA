import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useDropzone } from 'react-dropzone';
import { Upload as UploadIcon, FileCode, X, Loader2, CheckCircle, Settings } from 'lucide-react';
import { api } from '../lib/api';

export default function Upload() {
  const navigate = useNavigate();
  const [files, setFiles] = useState<File[]>([]);
  const [llmProvider, setLlmProvider] = useState('qwen3');
  const [jobName, setJobName] = useState('');
  const [jdbcConfig, setJdbcConfig] = useState({
    enabled: false,
    url: '',
    username: '',
    password: '',
    driver: 'oracle.jdbc.OracleDriver',
  });
  const [showJdbc, setShowJdbc] = useState(false);

  const uploadMutation = useMutation({
    mutationFn: async () => {
      const formData = new FormData();
      files.forEach((file) => {
        formData.append('files', file);
      });
      formData.append('llmProvider', llmProvider);
      formData.append('jobName', jobName || files[0]?.name || 'Conversion Job');
      if (jdbcConfig.enabled) {
        formData.append('jdbcConfig', JSON.stringify(jdbcConfig));
      }
      return api.uploadFiles(formData);
    },
    onSuccess: (data) => {
      navigate(`/jobs/${data.id}`);
    },
  });

  const onDrop = useCallback((acceptedFiles: File[]) => {
    const cFiles = acceptedFiles.filter(
      (f) => f.name.endsWith('.c') || f.name.endsWith('.h')
    );
    setFiles((prev) => [...prev, ...cFiles]);
    if (!jobName && cFiles.length > 0) {
      setJobName(cFiles[0].name.replace(/\.[ch]$/, ''));
    }
  }, [jobName]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'text/x-c': ['.c', '.h'],
    },
  });

  const removeFile = (index: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (files.length === 0) return;
    uploadMutation.mutate();
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">C 파일 업로드</h1>
        <p className="text-sm text-gray-500 mt-1">
          변환할 C 소스 파일을 업로드하세요. (.c, .h 파일 지원)
        </p>
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
              <p className="text-sm text-gray-400 mt-1">.c, .h 파일만 지원됩니다</p>
            </>
          )}
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

          <div className="grid grid-cols-2 gap-4">
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
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                LLM 모델
              </label>
              <select
                value={llmProvider}
                onChange={(e) => setLlmProvider(e.target.value)}
                className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
              >
                <option value="qwen3">QWEN3 VL (235B)</option>
                <option value="gpt_oss">GPT OSS</option>
              </select>
            </div>
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
            disabled={files.length === 0 || uploadMutation.isPending}
            className="px-6 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
          >
            {uploadMutation.isPending ? (
              <>
                <Loader2 className="h-5 w-5 animate-spin" />
                업로드 중...
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
