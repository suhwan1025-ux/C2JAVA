import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const axiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터: 토큰 추가
axiosInstance.interceptors.request.use(
  (config) => {
    const authData = localStorage.getItem('c2java-auth');
    if (authData) {
      try {
        const { state } = JSON.parse(authData);
        if (state?.token) {
          config.headers.Authorization = `Bearer ${state.token}`;
        }
      } catch (e) {
        // 파싱 오류 무시
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 응답 인터셉터: 401 처리
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('c2java-auth');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const api = {
  // Conversion APIs
  uploadFiles: async (formData: FormData) => {
    const response = await axiosInstance.post('/v1/conversions', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  createConversion: async (formData: FormData) => {
    const response = await axiosInstance.post('/v1/conversions', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  getJobs: async () => {
    const response = await axiosInstance.get('/v1/conversions');
    return response.data;
  },

  getAllJobs: async () => {
    const response = await axiosInstance.get('/v1/conversions');
    return response.data;
  },

  getJobStatus: async (jobId: string) => {
    const response = await axiosInstance.get(`/v1/conversions/${jobId}`);
    return response.data;
  },

  getJobsByStatus: async (status: string) => {
    const response = await axiosInstance.get(`/v1/conversions/status/${status}`);
    return response.data;
  },

  // Admin APIs
  getSystemStatus: async () => {
    const response = await axiosInstance.get('/v1/admin/status');
    return response.data;
  },

  getLlmConfig: async () => {
    const response = await axiosInstance.get('/v1/admin/llm/config');
    return response.data;
  },

  changeLlmProvider: async (provider: string) => {
    const response = await axiosInstance.put(`/v1/admin/llm/provider?provider=${provider}`);
    return response.data;
  },

  updateLlmConfig: async (config: any) => {
    const response = await axiosInstance.put('/v1/admin/llm/config', config);
    return response.data;
  },

  getEnvironmentVariables: async () => {
    const response = await axiosInstance.get('/v1/admin/env');
    return response.data;
  },

  getCliStatus: async () => {
    const response = await axiosInstance.get('/v1/admin/cli/status');
    return response.data;
  },

  getStatistics: async () => {
    const response = await axiosInstance.get('/v1/admin/statistics');
    return response.data;
  },

  // Configuration APIs (런타임 설정 관리)
  getAllConfigs: async () => {
    const response = await axiosInstance.get('/v1/configs');
    return response.data;
  },

  getConfigsByCategory: async (category: string) => {
    const response = await axiosInstance.get(`/v1/configs/category/${category}`);
    return response.data;
  },

  getConfig: async (key: string) => {
    const response = await axiosInstance.get(`/v1/configs/${key}`);
    return response.data;
  },

  updateConfig: async (key: string, value: string) => {
    const response = await axiosInstance.put(`/v1/configs/${key}`, { value });
    return response.data;
  },

  updateConfigs: async (configs: Record<string, string>) => {
    const response = await axiosInstance.put('/v1/configs/batch', configs);
    return response.data;
  },

  reloadConfigs: async () => {
    const response = await axiosInstance.post('/v1/configs/reload');
    return response.data;
  },

  // Authentication APIs
  login: async (data: { username: string; password: string }) => {
    const response = await axiosInstance.post('/v1/auth/login', data);
    return response.data;
  },

  register: async (data: { username: string; password: string; email: string; displayName: string }) => {
    const response = await axiosInstance.post('/v1/auth/register', data);
    return response.data;
  },

  getCurrentUser: async () => {
    const response = await axiosInstance.get('/v1/auth/me');
    return response.data;
  },

  changePassword: async (oldPassword: string, newPassword: string) => {
    const response = await axiosInstance.put('/v1/auth/password', { oldPassword, newPassword });
    return response.data;
  },
};

export default axiosInstance;
