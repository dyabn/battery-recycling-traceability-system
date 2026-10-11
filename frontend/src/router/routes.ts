import type { RouteRecordRaw } from 'vue-router';

import ForbiddenView from '../views/ForbiddenView.vue';
import HomeRedirectView from '../views/HomeRedirectView.vue';
import LoginView from '../views/LoginView.vue';
import ShellView from '../views/ShellView.vue';
import AcceptancePendingView from '../views/acceptance/AcceptancePendingView.vue';
import BatteryRegisterView from '../views/battery/BatteryRegisterView.vue';
import BatteryTraceView from '../views/battery/BatteryTraceView.vue';
import DuplicateResolutionView from '../views/battery/DuplicateResolutionView.vue';
import BatchDetailView from '../views/batch/BatchDetailView.vue';
import BatchListView from '../views/batch/BatchListView.vue';
import DataQualityCheckRunView from '../views/data-quality/DataQualityCheckRunView.vue';
import DataQualityChecksView from '../views/data-quality/DataQualityChecksView.vue';
import DataQualityIssueDetailView from '../views/data-quality/DataQualityIssueDetailView.vue';
import DataQualityIssuesView from '../views/data-quality/DataQualityIssuesView.vue';
import DataQualityRulesView from '../views/data-quality/DataQualityRulesView.vue';
import InboundPendingView from '../views/inbound/InboundPendingView.vue';
import InventoryView from '../views/inventory/InventoryView.vue';
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
      { path: '', name: 'home', component: HomeRedirectView },
      { path: 'batches', name: 'batches', component: BatchListView, meta: { permission: 'batch:read' } },
      { path: 'batches/:id', name: 'batch-detail', component: BatchDetailView, meta: { permission: 'batch:read' } },
      { path: 'acceptances', name: 'acceptances', component: AcceptancePendingView, meta: { permission: 'acceptance:create' } },
      { path: 'inbounds/pending', name: 'inbounds-pending', component: InboundPendingView, meta: { permission: 'inbound:create' } },
      { path: 'inventory', name: 'inventory', component: InventoryView, meta: { permission: 'inventory:read' } },
      { path: 'data-quality/rules', name: 'dq-rules', component: DataQualityRulesView, meta: { permission: 'dq:rule:read' } },
      { path: 'data-quality/checks', name: 'dq-checks', component: DataQualityChecksView, meta: { permission: 'dq:check:execute' } },
      { path: 'data-quality/check-runs/:runId', name: 'dq-check-run', component: DataQualityCheckRunView, meta: { permission: 'dq:check:read' } },
      { path: 'data-quality/issues', name: 'dq-issues', component: DataQualityIssuesView, meta: { permission: 'dq:issue:read' } },
      { path: 'data-quality/issues/:issueId', name: 'dq-issue-detail', component: DataQualityIssueDetailView, meta: { permission: 'dq:issue:read' } },
      { path: 'batteries/register', name: 'battery-register', component: BatteryRegisterView, meta: { permission: 'battery:create' } },
      { path: 'duplicates/:id', name: 'duplicate-resolution', component: DuplicateResolutionView, meta: { permission: 'battery:duplicate:resolve' } },
      { path: 'batteries/:id/trace', name: 'battery-trace', component: BatteryTraceView, meta: { permission: 'trace:read' } },
      { path: 'system/users', name: 'users', component: UsersView, meta: { permission: 'permission:manage' } },
      { path: 'system/roles', name: 'roles', component: RolesView, meta: { permission: 'permission:manage' } },
      { path: 'system/permissions', name: 'permissions', component: PermissionsView, meta: { permission: 'permission:manage' } },
      { path: 'system/audit', name: 'audit', component: AuditView, meta: { permission: 'audit:read' } },
    ],
  },
];
