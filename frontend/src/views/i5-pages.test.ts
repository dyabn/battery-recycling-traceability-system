import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

import {
  createDataQualityCheckRun,
  getDataQualityCheckRun,
  getDataQualityIssue,
  listDataQualityCheckResults,
  listDataQualityIssues,
  listDataQualityRules,
  type DataQualityCheckRun,
  type DataQualityIssue,
  type DataQualityRule,
} from '../api/i2';
import { routes } from '../router/routes';
import DataQualityCheckRunView from './data-quality/DataQualityCheckRunView.vue';
import DataQualityChecksView from './data-quality/DataQualityChecksView.vue';
import DataQualityIssueDetailView from './data-quality/DataQualityIssueDetailView.vue';
import DataQualityIssuesView from './data-quality/DataQualityIssuesView.vue';
import DataQualityRulesView from './data-quality/DataQualityRulesView.vue';

const routerPush = vi.fn();

vi.mock('../api/i2', () => ({
  createDataQualityCheckRun: vi.fn(),
  getDataQualityCheckRun: vi.fn(),
  getDataQualityIssue: vi.fn(),
  listDataQualityCheckResults: vi.fn(),
  listDataQualityIssues: vi.fn(),
  listDataQualityRules: vi.fn(),
}));

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { runId: '9007199254745001', issueId: '9007199254746001' } }),
  useRouter: () => ({ push: routerPush }),
}));

function rule(code: string): DataQualityRule {
  return {
    id: `100${code.slice(-1)}`,
    ruleCode: code,
    ruleName: `${code}规则`,
    dimension: '完整性',
    objectType: code === 'DQ-007' ? 'INVENTORY' : 'BATTERY',
    handlerCode: `${code}_HANDLER`,
    severity: 'MEDIUM',
    defaultOwnerRoleCode: 'BUSINESS_SUPERVISOR',
    dataStandardMetadata: {},
    involvedFields: [],
    checkCondition: '固定条件',
    remediationGuidance: '处理建议',
    allowExemption: false,
    ruleStatus: 'ACTIVE',
    enabledStatus: 'ENABLED',
  };
}

function checkRun(): DataQualityCheckRun {
  return {
    id: '9007199254745001',
    enterpriseId: '1',
    runNo: 'DQR-9007199254745001',
    runScope: 'ALL',
    runStatus: 'COMPLETED',
    requestedRuleCodes: [],
    executedRuleCodes: ['DQ-001', 'DQ-002'],
    checkedObjectCount: 12,
    checkedRuleCount: 2,
    issueCount: 1,
    resultSummary: { passRuleCount: 1, violationRuleCount: 1, newIssueCount: 1, dedupHitCount: 0 },
    startedBy: '2',
    startedAt: '2026-10-11T10:00:00+08:00',
    completedAt: '2026-10-11T10:00:01+08:00',
  };
}

function issue(): DataQualityIssue {
  return {
    id: '9007199254746001',
    enterpriseId: '1',
    issueNo: 'DQI-9007199254746001',
    ruleCode: 'DQ-002',
    objectType: 'BATTERY',
    objectId: '9007199254747001',
    objectIdentity: '9007199254747001',
    objectKey: '9007199254747001',
    title: 'DQ-002 核心字段为空',
    description: '电池核心字段为空',
    severity: 'MEDIUM',
    ownerRoleCode: 'RECYCLE_OPERATOR',
    issueStatus: 'OPEN',
    discoveredRunId: '9007199254745001',
    discoveredResultId: '9007199254745002',
    firstDetectedAt: '2026-10-11T10:00:00+08:00',
    lastDetectedAt: '2026-10-11T10:00:00+08:00',
  };
}

function mountWithPlugins(component: object) {
  return mount(component, {
    global: {
      plugins: [ElementPlus],
      mocks: {
        $router: { push: routerPush, back: vi.fn() },
      },
    },
  });
}

async function flush() {
  await Promise.resolve();
  await nextTick();
}

describe('I5 data quality pages', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routerPush.mockReset();
    vi.mocked(listDataQualityRules).mockResolvedValue(['DQ-001', 'DQ-002', 'DQ-003', 'DQ-004', 'DQ-005', 'DQ-006', 'DQ-007'].map(rule));
    vi.mocked(createDataQualityCheckRun).mockResolvedValue(checkRun());
    vi.mocked(getDataQualityCheckRun).mockResolvedValue(checkRun());
    vi.mocked(listDataQualityCheckResults).mockResolvedValue([
      {
        id: '9007199254745002',
        enterpriseId: '1',
        runId: '9007199254745001',
        ruleCode: 'DQ-002',
        checkStatus: 'VIOLATION',
        checkedObjectCount: 4,
        violationCount: 1,
        ruleSnapshot: {},
        startedAt: '2026-10-11T10:00:00+08:00',
        completedAt: '2026-10-11T10:00:01+08:00',
      },
    ]);
    vi.mocked(listDataQualityIssues).mockResolvedValue([issue()]);
    vi.mocked(getDataQualityIssue).mockResolvedValue(issue());
  });

  it('marks data quality routes with required permissions', () => {
    const shell = routes.find((route) => route.path === '/');
    const children = shell?.children || [];
    expect(children.find((route) => route.path === 'data-quality/rules')?.meta?.permission).toBe('dq:rule:read');
    expect(children.find((route) => route.path === 'data-quality/checks')?.meta?.permission).toBe('dq:check:execute');
    expect(children.find((route) => route.path === 'data-quality/issues')?.meta?.permission).toBe('dq:issue:read');
  });

  it('loads fixed data quality rules', async () => {
    const wrapper = mountWithPlugins(DataQualityRulesView);
    await flush();

    expect(listDataQualityRules).toHaveBeenCalled();
    expect(wrapper.text()).toContain('DQ-001');
    expect(wrapper.text()).toContain('ENABLED');
  });

  it('starts a manual check and navigates to the result page with a string id', async () => {
    const wrapper = mountWithPlugins(DataQualityChecksView);
    await flush();
    const vm = wrapper.vm as unknown as { form: { runScope: string; requestedRuleCodes: string[] }; submit: () => Promise<void> };
    vm.form.runScope = 'BATTERY';
    await nextTick();
    vm.form.requestedRuleCodes = ['DQ-002'];
    await vm.submit();

    expect(createDataQualityCheckRun).toHaveBeenCalledWith({ runScope: 'BATTERY', requestedRuleCodes: ['DQ-002'] });
    expect(routerPush).toHaveBeenCalledWith('/data-quality/check-runs/9007199254745001');
  });

  it('shows check run summary and rule results', async () => {
    const wrapper = mountWithPlugins(DataQualityCheckRunView);
    await flush();

    expect(getDataQualityCheckRun).toHaveBeenCalledWith('9007199254745001');
    expect(listDataQualityCheckResults).toHaveBeenCalledWith('9007199254745001');
    expect(wrapper.text()).toContain('DQR-9007199254745001');
    expect(wrapper.text()).toContain('DQ-002');
  });

  it('lists and shows read-only issue details', async () => {
    const listWrapper = mountWithPlugins(DataQualityIssuesView);
    await flush();
    expect(listDataQualityIssues).toHaveBeenCalledWith('OPEN');
    expect(listWrapper.text()).toContain('DQI-9007199254746001');

    const detailWrapper = mountWithPlugins(DataQualityIssueDetailView);
    await flush();
    expect(getDataQualityIssue).toHaveBeenCalledWith('9007199254746001');
    expect(detailWrapper.text()).toContain('电池核心字段为空');
  });
});
