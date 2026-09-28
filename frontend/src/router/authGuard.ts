import type { RouteLocationNormalized } from 'vue-router';

import { useAuthStore } from '../stores/auth';

export async function authGuard(to: Pick<RouteLocationNormalized, 'meta' | 'fullPath'>) {
  const authStore = useAuthStore();
  if (to.meta.public) {
    return true;
  }
  if (!authStore.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (!authStore.currentUser) {
    try {
      await authStore.loadCurrentUser();
    } catch {
      authStore.clearSession();
      return { path: '/login', query: { redirect: to.fullPath } };
    }
  }
  const permission = to.meta.permission as string | undefined;
  if (permission && !authStore.hasPermission(permission)) {
    return '/403';
  }
  return true;
}

