import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

import {
  createInbound,
  getBatteryTrace,
  listInventory,
  listPendingInbounds,
  listWarehouseLocations,
  listWarehouses,
  type Battery,
  type TraceEvent,
} from '../api/i2';
import { defaultHomePath } from '../router/defaultHome';
import { useAuthStore } from '../stores/auth';
import BatteryTraceView from './battery/BatteryTraceView.vue';
import InboundPendingView from './inbound/InboundPendingView.vue';
import InventoryView from './inventory/InventoryView.vue';

vi.mock('../api/i2', () => ({
  createInbound: vi.fn(),
  getBatteryTrace: vi.fn(),
  listInventory: vi.fn(),
  listPendingInbounds: vi.fn(),
  listWarehouseLocations: vi.fn(),
  listWarehouses: vi.fn(),
}));

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '9007199254742001' } }),
}));

function battery(overrides: Partial<Battery> = {}): Battery {
  return {
    id: '9007199254742001',
    enterpriseId: '1',
    systemTraceCode: 'BAT-9007199254742001',
    originalCode: 'ORI-I4-FE',
    batteryType: 'PACK',
    batteryChemistry: 'UNKNOWN',
    currentResponsibleEnterpriseId: '1',
    lifecycleStatus: 'ACCEPTED_PENDING_INBOUND',
    duplicateStatus: 'NORMAL',
    version: 1,
    ...overrides,
  };
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

async function flush() {
  await Promise.resolve();
  await nextTick();
}

function inboundTrace(): TraceEvent[] {
  return [
    {
      eventName: '入库完成',
      objectCode: 'BAT-9007199254742001',
      operator: '仓库管理员',
      occurredAt: '2026-10-10T10:00:00+08:00',
      statusChange: 'ACCEPTED_PENDING_INBOUND -> IN_STOCK',
      result: 'SUCCESS',
      details: {
        inbound: {
          id: '9007199254743001',
          inboundNo: 'IB-9007199254743001',
          inboundAt: '2026-10-10T10:00:00+08:00',
          warehouseCode: 'WH-101',
          warehouseName: 'A企业启用仓库',
          locationCode: 'WH-101-A01-R01-L01',
          inventoryId: '9007199254743002',
          inboundBy: '仓库管理员',
        },
      },
    },
  ];
}

describe('I4 inbound inventory pages', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    const authStore = useAuthStore();
    authStore.currentUser = {
      id: '4',
      enterpriseId: '1',
      username: 'warehouse_admin',
      displayName: '仓库管理员',
      enabledStatus: 'ENABLED',
      roles: ['WAREHOUSE_ADMIN'],
      permissions: ['inbound:create', 'warehouse:read', 'inventory:read', 'trace:read'],
    };
    authStore.token = 'token';
    vi.clearAllMocks();
    vi.mocked(listPendingInbounds).mockResolvedValue([battery()]);
    vi.mocked(listWarehouses).mockResolvedValue([
      { id: '20101', enterpriseId: '1', warehouseCode: 'WH-101', warehouseName: 'A企业启用仓库', enabledStatus: 'ENABLED' },
    ]);
    vi.mocked(listWarehouseLocations).mockResolvedValue([
      { id: '21101', enterpriseId: '1', warehouseId: '20101', locationCode: 'WH-101-A01-R01-L01', enabledStatus: 'ENABLED' },
    ]);
    vi.mocked(listInventory).mockResolvedValue([
      {
        id: '9007199254743002',
        enterpriseId: '1',
        batteryId: '9007199254742001',
        systemTraceCode: 'BAT-9007199254742001',
        warehouseId: '20101',
        warehouseCode: 'WH-101',
        warehouseName: 'A企业启用仓库',
        locationId: '21101',
        locationCode: 'WH-101-A01-R01-L01',
        inboundRecordId: '9007199254743001',
        inboundAt: '2026-10-10T10:00:00+08:00',
      },
    ]);
    vi.mocked(getBatteryTrace).mockResolvedValue(inboundTrace());
  });

  it('uses inbound as the warehouse admin home page', () => {
    expect(defaultHomePath(['inbound:create', 'inventory:read', 'batch:read'])).toBe('/inbounds/pending');
  });

  it('clears the previous location when warehouse changes and submits string ids', async () => {
    vi.mocked(createInbound).mockResolvedValue({ inboundRecordId: '9007199254743001', inventoryId: '9007199254743002', batteryStatus: 'IN_STOCK' });
    const wrapper = mountWithPlugins(InboundPendingView);
    await flush();

    expect(listPendingInbounds).toHaveBeenCalled();
    await (wrapper.vm as unknown as { openInbound: (row: Battery) => Promise<void> }).openInbound(battery());
    const vm = wrapper.vm as unknown as {
      inboundForm: { warehouseId: string; locationId: string };
      onWarehouseChange: () => Promise<void>;
      saveInbound: () => Promise<void>;
    };
    vm.inboundForm.warehouseId = '20101';
    vm.inboundForm.locationId = 'stale-location';
    await vm.onWarehouseChange();

    expect(vm.inboundForm.locationId).toBe('');
    expect(listWarehouseLocations).toHaveBeenCalledWith('20101');
    vm.inboundForm.locationId = '21101';
    await vm.saveInbound();

    expect(createInbound).toHaveBeenCalledWith('9007199254742001', {
      warehouseId: '20101',
      locationId: '21101',
    });
  });

  it('queries current inventory by system trace code', async () => {
    const wrapper = mountWithPlugins(InventoryView);
    await flush();
    const vm = wrapper.vm as unknown as { systemTraceCode: string; load: () => Promise<void> };
    vm.systemTraceCode = 'BAT-9007199254742001';
    await vm.load();

    expect(listInventory).toHaveBeenLastCalledWith('BAT-9007199254742001');
    expect(wrapper.text()).toContain('WH-101-A01-R01-L01');
  });

  it('shows inbound details in the trace page', async () => {
    const wrapper = mountWithPlugins(BatteryTraceView);
    await flush();

    expect(getBatteryTrace).toHaveBeenCalledWith('9007199254742001');
    expect(wrapper.text()).toContain('IB-9007199254743001');
    expect(wrapper.text()).toContain('WH-101 A企业启用仓库');
    expect(wrapper.text()).toContain('WH-101-A01-R01-L01');
    expect(wrapper.text()).toContain('9007199254743002');
  });
});
