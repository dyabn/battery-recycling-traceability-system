import { describe, expect, it } from 'vitest';

import { routes } from './routes';

describe('router', () => {
  it('marks protected routes with required permissions', () => {
    const childRoutes = routes.flatMap((route) => route.children || []);
    expect(childRoutes.find((route) => route.path === 'system/users')?.meta?.permission).toBe('permission:manage');
    expect(childRoutes.find((route) => route.path === 'system/audit')?.meta?.permission).toBe('audit:read');
  });
});
