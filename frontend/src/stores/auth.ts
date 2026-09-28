import { defineStore } from 'pinia';

import { http } from '../api/http';

export interface CurrentUser {
  id: number;
  enterpriseId: number;
  username: string;
  displayName: string;
  enabledStatus: string;
  roles: string[];
  permissions: string[];
}

interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  currentUser: CurrentUser;
}

interface ApiEnvelope<T> {
  code: string;
  message: string;
  traceId: string;
  data: T;
}

interface AuthState {
  token: string;
  currentUser: CurrentUser | null;
}

const tokenKey = 'battery-traceability-token';
const userKey = 'battery-traceability-current-user';

function storage(): Storage | null {
  return typeof localStorage === 'undefined' ? null : localStorage;
}

function readCurrentUser(): CurrentUser | null {
  try {
    return JSON.parse(storage()?.getItem(userKey) || 'null') as CurrentUser | null;
  } catch {
    storage()?.removeItem(userKey);
    return null;
  }
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: storage()?.getItem(tokenKey) || '',
    currentUser: readCurrentUser(),
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    hasPermission: (state) => (permission: string) => state.currentUser?.permissions.includes(permission) ?? false,
    roleNames: (state) => state.currentUser?.roles.join(', ') || '',
  },
  actions: {
    async login(username: string, password: string) {
      const response = await http.post<ApiEnvelope<LoginResponse>>('/auth/login', { username, password });
      this.token = response.data.data.accessToken;
      this.currentUser = response.data.data.currentUser;
      storage()?.setItem(tokenKey, this.token);
      storage()?.setItem(userKey, JSON.stringify(this.currentUser));
    },
    async loadCurrentUser() {
      const response = await http.get<ApiEnvelope<CurrentUser>>('/auth/me');
      this.currentUser = response.data.data;
      storage()?.setItem(userKey, JSON.stringify(this.currentUser));
    },
    clearSession() {
      this.token = '';
      this.currentUser = null;
      storage()?.removeItem(tokenKey);
      storage()?.removeItem(userKey);
    },
  },
});
