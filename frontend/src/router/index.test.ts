import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useAuthStore } from '../stores/auth';
import { authGuard } from './authGuard';
import { defaultHomePath } from './defaultHome';
import { routes } from './routes';

describe('router', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('marks protected routes with required permissions', () => {
    const childRoutes = routes.flatMap((route) => route.children || []);
    expect(childRoutes.find((route) => route.path === '')?.name).toBe('home');
    expect(childRoutes.find((route) => route.path === '')?.redirect).toBeUndefined();
    expect(childRoutes.find((route) => route.path === 'batches')?.meta?.permission).toBe('batch:read');
    expect(childRoutes.find((route) => route.path === 'batteries/register')?.meta?.permission).toBe('battery:create');
    expect(childRoutes.find((route) => route.path === 'system/users')?.meta?.permission).toBe('permission:manage');
    expect(childRoutes.find((route) => route.path === 'system/audit')?.meta?.permission).toBe('audit:read');
  });

  it('chooses the default home from the current user permissions', () => {
    expect(defaultHomePath(['permission:manage', 'batch:read'])).toBe('/system/users');
    expect(defaultHomePath(['batch:read', 'battery:create'])).toBe('/batches');
    expect(defaultHomePath(['battery:create'])).toBe('/batteries/register');
    expect(defaultHomePath(['audit:read'])).toBe('/system/audit');
    expect(defaultHomePath([])).toBe('/403');
  });

  it('redirects unauthenticated users to login', async () => {
    await expect(authGuard({ meta: {}, fullPath: '/system/users' })).resolves.toEqual({
      path: '/login',
      query: { redirect: '/system/users' },
    });
  });

  it('loads current user when token exists but cache is empty', async () => {
    const authStore = useAuthStore();
    authStore.token = 'token';
    authStore.currentUser = null;
    authStore.loadCurrentUser = vi.fn(async () => {
      authStore.currentUser = {
        id: '1',
        enterpriseId: '1',
        username: 'admin',
        displayName: '系统管理员',
        enabledStatus: 'ENABLED',
        roles: ['SYSTEM_ADMIN'],
        permissions: ['permission:manage'],
      };
    });

    await expect(authGuard({ meta: { permission: 'permission:manage' }, fullPath: '/system/users' })).resolves.toBe(true);
    expect(authStore.loadCurrentUser).toHaveBeenCalledOnce();
  });

  it('rejects authenticated users without route permission', async () => {
    const authStore = useAuthStore();
    authStore.token = 'token';
    authStore.currentUser = {
      id: '2',
      enterpriseId: '1',
      username: 'supervisor',
      displayName: '业务主管',
      enabledStatus: 'ENABLED',
      roles: ['BUSINESS_SUPERVISOR'],
      permissions: ['audit:read'],
    };

    await expect(authGuard({ meta: { permission: 'permission:manage' }, fullPath: '/system/users' })).resolves.toBe('/403');
  });
});
