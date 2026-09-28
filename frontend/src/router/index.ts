import { createRouter, createWebHistory } from 'vue-router';

import { useAuthStore } from '../stores/auth';
import { routes } from './routes';

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore();
  if (to.meta.public) {
    return true;
  }
  if (!authStore.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (!authStore.currentUser) {
    await authStore.loadCurrentUser();
  }
  const permission = to.meta.permission as string | undefined;
  if (permission && !authStore.hasPermission(permission)) {
    return '/403';
  }
  return true;
});

export default router;
