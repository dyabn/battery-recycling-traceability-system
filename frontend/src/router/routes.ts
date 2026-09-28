import type { RouteRecordRaw } from 'vue-router';

import ForbiddenView from '../views/ForbiddenView.vue';
import LoginView from '../views/LoginView.vue';
import ShellView from '../views/ShellView.vue';
import AuditView from '../views/system/AuditView.vue';
import PermissionsView from '../views/system/PermissionsView.vue';
import RolesView from '../views/system/RolesView.vue';
import UsersView from '../views/system/UsersView.vue';

export const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  { path: '/403', name: 'forbidden', component: ForbiddenView, meta: { public: true } },
  {
    path: '/',
    component: ShellView,
    children: [
      { path: '', redirect: '/system/users' },
      { path: 'system/users', name: 'users', component: UsersView, meta: { permission: 'permission:manage' } },
      { path: 'system/roles', name: 'roles', component: RolesView, meta: { permission: 'permission:manage' } },
      { path: 'system/permissions', name: 'permissions', component: PermissionsView, meta: { permission: 'permission:manage' } },
      { path: 'system/audit', name: 'audit', component: AuditView, meta: { permission: 'audit:read' } },
    ],
  },
];

