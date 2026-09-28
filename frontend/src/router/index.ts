import { createRouter, createWebHistory } from 'vue-router';

import { authGuard } from './authGuard';
import { routes } from './routes';

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(authGuard);

export default router;
