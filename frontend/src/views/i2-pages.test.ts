import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

import { addBatteryToBatch, checkDuplicate, createBattery, getBatch, getBatteryTrace, resolveDuplicate, submitBatch, type Battery, type RecycleBatch } from '../api/i2';
import { useAuthStore } from '../stores/auth';
import BatteryRegisterView from './battery/BatteryRegisterView.vue';
import BatteryTraceView from './battery/BatteryTraceView.vue';
import DuplicateResolutionView from './battery/DuplicateResolutionView.vue';
import BatchDetailView from './batch/BatchDetailView.vue';

vi.mock('../api/i2', () => ({
  addBatteryToBatch: vi.fn(),
  checkDuplicate: vi.fn(),
  createBattery: vi.fn(),
  getBatch: vi.fn(),
  getBatteryTrace: vi.fn(),
  resolveDuplicate: vi.fn(),
  submitBatch: vi.fn(),
}));

const routeState = {
  params: { id: '501' },
  query: { matches: '301,302' },
};

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
}));

const battery: Battery = {
  id: '9007199254740993',
  enterpriseId: '1',
  systemTraceCode: 'BAT-301',
  originalCode: 'ORI-I2-FE',
  batteryType: 'PACK',
  batteryChemistry: 'UNKNOWN',
  currentResponsibleEnterpriseId: '1',
  lifecycleStatus: 'REGISTERED',
  duplicateStatus: 'NORMAL',
  version: 0,
};

const batch: RecycleBatch = {
  id: '9007199254740995',
  enterpriseId: '1',
  batchNo: 'RB-501',
  sourceType: 'ENTERPRISE',
  sourceSubjectName: '测试来源',
  handoverDate: '2026-09-29',
  batchStatus: 'DRAFT',
  createdBy: '1',
  createdAt: '2026-09-29T12:00:00+08:00',
  updatedAt: '2026-09-29T12:00:00+08:00',
  version: 0,
  batteries: [battery],
};

async function flush() {
  await Promise.resolve();
  await nextTick();
}

function mountWithPlugins(component: object) {
  return mount(component, {
    global: {
      plugins: [ElementPlus],
      mocks: {
        $router: { push: vi.fn(), back: vi.fn() },
      },
    },
  });
}

describe('I2 pages', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    const authStore = useAuthStore();
    authStore.currentUser = {
      id: '3',
      enterpriseId: '1',
      username: 'recycle_operator',
      displayName: '回收操作员',
      enabledStatus: 'ENABLED',
      roles: ['RECYCLE_OPERATOR'],
      permissions: ['batch:read', 'batch:submit', 'battery:create', 'battery:duplicate:resolve', 'trace:read'],
    };
    vi.clearAllMocks();
    routeState.params = { id: '501' };
    routeState.query = { matches: '301,302' };
  });

  it('checks duplicate codes and registers batteries from the registration page', async () => {
    vi.mocked(checkDuplicate).mockResolvedValue({ duplicated: true, matchedBatteryIds: ['9007199254740993'] });
    vi.mocked(createBattery).mockResolvedValue({ resultType: 'BATTERY_CREATED', battery, matchedBatteryIds: [] });
    const wrapper = mountWithPlugins(BatteryRegisterView);

    (wrapper.vm as unknown as { form: { originalCode: string }; duplicateCheck: () => Promise<void>; save: () => Promise<void> }).form.originalCode = 'ORI-I2-FE';
    await (wrapper.vm as unknown as { duplicateCheck: () => Promise<void> }).duplicateCheck();
    await (wrapper.vm as unknown as { save: () => Promise<void> }).save();

    expect(checkDuplicate).toHaveBeenCalledWith('ORI-I2-FE');
    expect(createBattery).toHaveBeenCalledWith(expect.objectContaining({ originalCode: 'ORI-I2-FE', batteryType: 'PACK' }));
    expect(wrapper.text()).toContain('电池登记成功');
  });

  it('resolves duplicate candidates from the duplicate resolution page', async () => {
    vi.mocked(resolveDuplicate).mockResolvedValue(battery);
    routeState.params = { id: '601' };
    const wrapper = mountWithPlugins(DuplicateResolutionView);

    await (wrapper.vm as unknown as { save: () => Promise<void> }).save();

    expect(resolveDuplicate).toHaveBeenCalledWith('601', expect.objectContaining({
      reviewResult: 'SAME_BATTERY',
      existingBatteryId: '301',
    }));
    expect(wrapper.text()).toContain('核实完成');
  });

  it('adds batteries and submits batches from the batch detail page', async () => {
    vi.mocked(getBatch).mockResolvedValue(batch);
    vi.mocked(addBatteryToBatch).mockResolvedValue(batch);
    vi.mocked(submitBatch).mockResolvedValue({ ...batch, batchStatus: 'PENDING_ACCEPTANCE' });
    const wrapper = mountWithPlugins(BatchDetailView);
    await flush();

    (wrapper.vm as unknown as { batteryId: string }).batteryId = '9007199254740993';
    await (wrapper.vm as unknown as { addBattery: () => Promise<void>; submit: () => Promise<void> }).addBattery();
    await (wrapper.vm as unknown as { submit: () => Promise<void> }).submit();

    expect(getBatch).toHaveBeenCalledWith('501');
    expect(addBatteryToBatch).toHaveBeenCalledWith('501', '9007199254740993');
    expect(submitBatch).toHaveBeenCalledWith('501');
  });

  it('loads trace events on the trace page', async () => {
    routeState.params = { id: '301' };
    vi.mocked(getBatteryTrace).mockResolvedValue([{
      eventName: '电池登记',
      objectCode: 'BAT-301',
      operator: '回收操作员',
      occurredAt: '2026-09-29T12:00:00+08:00',
      statusChange: 'null -> REGISTERED',
      result: 'SUCCESS',
    }]);
    const wrapper = mountWithPlugins(BatteryTraceView);
    await flush();

    expect(getBatteryTrace).toHaveBeenCalledWith('301');
    expect(wrapper.text()).toContain('电池登记');
  });
});
