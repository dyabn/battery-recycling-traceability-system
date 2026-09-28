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

interface AuthState {
  token: string;
  currentUser: CurrentUser | null;
}

const tokenKey = 'battery-traceability-token';
const userKey = 'battery-traceability-current-user';

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem(tokenKey) || '',
    currentUser: JSON.parse(localStorage.getItem(userKey) || 'null') as CurrentUser | null,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token && state.currentUser),
    hasPermission: (state) => (permission: string) => state.currentUser?.permissions.includes(permission) ?? false,
    roleNames: (state) => state.currentUser?.roles.join(', ') || '',
  },
  actions: {
    async login(username: string, password: string) {
      const response = await http.post<LoginResponse>('/auth/login', { username, password });
      this.token = response.data.accessToken;
      this.currentUser = response.data.currentUser;
      localStorage.setItem(tokenKey, this.token);
      localStorage.setItem(userKey, JSON.stringify(this.currentUser));
    },
    async loadCurrentUser() {
      const response = await http.get<CurrentUser>('/auth/current-user');
      this.currentUser = response.data;
      localStorage.setItem(userKey, JSON.stringify(this.currentUser));
    },
    clearSession() {
      this.token = '';
      this.currentUser = null;
      localStorage.removeItem(tokenKey);
      localStorage.removeItem(userKey);
    },
  },
});

