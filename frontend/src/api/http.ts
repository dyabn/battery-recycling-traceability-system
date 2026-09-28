import axios from 'axios';
import { ElMessage } from 'element-plus';

import { useAuthStore } from '../stores/auth';

export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1';

export const http = axios.create({
  baseURL: apiBaseUrl,
  timeout: 10000,
});

http.interceptors.request.use((config) => {
  const authStore = useAuthStore();
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status;
    const code = error.response?.data?.code;
    const message = error.response?.data?.message || '请求失败';
    const authStore = useAuthStore();

    if (status === 401) {
      authStore.clearSession();
      if (window.location.pathname !== '/login') {
        window.location.assign('/login');
      }
      ElMessage.error(code === 'TOKEN_EXPIRED' ? '登录已过期，请重新登录' : '请先登录');
      return Promise.reject(error);
    }

    if (status === 403) {
      ElMessage.error(message);
      if (window.location.pathname !== '/403') {
        window.location.assign('/403');
      }
      return Promise.reject(error);
    }

    ElMessage.error(message);
    return Promise.reject(error);
  },
);
