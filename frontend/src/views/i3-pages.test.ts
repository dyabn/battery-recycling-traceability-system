import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

import {
  createAcceptance,
  downloadAttachment,
  getBatch,
  getBatteryTrace,
  listPendingAcceptances,
  supplementAcceptance,
  uploadAttachment,
  type Attachment,
  type Battery,
  type RecycleBatch,
  type TraceEvent,
} from '../api/i2';
import { useAuthStore } from '../stores/auth';
import AcceptancePendingView from './acceptance/AcceptancePendingView.vue';
import BatchDetailView from './batch/BatchDetailView.vue';
import BatteryTraceView from './battery/BatteryTraceView.vue';

vi.mock('../api/i2', () => ({
  createAcceptance: vi.fn(),
  downloadAttachment: vi.fn(),
  getBatch: vi.fn(),
  getBatteryTrace: vi.fn(),
  listPendingAcceptances: vi.fn(),
  supplementAcceptance: vi.fn(),
  uploadAttachment: vi.fn(),
}));

const routerPush = vi.fn();

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '701' } }),
}));

function battery(overrides: Partial<Battery> = {}): Battery {
  return {
    id: 701,
    enterpriseId: 1,
    systemTraceCode: 'BAT-701',
    originalCode: 'ORI-I3-FE',
    batteryType: 'PACK',
    batteryChemistry: 'UNKNOWN',
    currentResponsibleEnterpriseId: 1,
    lifecycleStatus: 'PENDING_ACCEPTANCE',
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
        $router: { push: routerPush, back: vi.fn() },
      },
    },
  });
}

function batch(): RecycleBatch {
  return {
    id: 701,
    enterpriseId: 1,
    batchNo: 'RB-I3-FE',
    sourceType: 'ENTERPRISE',
    sourceSubjectName: '测试来源',
    handoverDate: '2026-10-08',
    batchStatus: 'ACCEPTANCE_PROCESSING',
    createdBy: 3,
    createdAt: '2026-10-08T10:00:00+08:00',
    updatedAt: '2026-10-08T10:00:00+08:00',
    version: 1,
    batteries: [
      battery({ id: 1, lifecycleStatus: 'PENDING_ACCEPTANCE' }),
      battery({ id: 2, lifecycleStatus: 'PENDING_SUPPLEMENT' }),
      battery({ id: 3, lifecycleStatus: 'ACCEPTED_PENDING_INBOUND' }),
      battery({ id: 4, lifecycleStatus: 'ACCEPTANCE_REJECTED' }),
    ],
  };
}

function traceEvents(): TraceEvent[] {
  return [
    {
      eventName: '验收待补充资料',
      objectCode: 'BAT-701',
      operator: '回收操作员',
      occurredAt: '2026-10-08T10:00:00+08:00',
      statusChange: 'PENDING_ACCEPTANCE -> PENDING_SUPPLEMENT',
      result: 'SUCCESS',
      details: {
        acceptance: {
          id: 801,
          acceptanceResult: 'NEED_SUPPLEMENT',
          identityCheckResult: '身份一致',
          appearanceCheckResult: '外观需说明',
          documentCheckResult: '资料缺失',
          acceptanceNote: '缺少来源照片',
          acceptedBy: '回收操作员',
          acceptedAt: '2026-10-08T10:00:00+08:00',
        },
      },
    },
    {
      eventName: '验收资料已补充',
      objectCode: 'BAT-701',
      operator: '回收操作员',
      occurredAt: '2026-10-08T10:05:00+08:00',
      statusChange: 'PENDING_SUPPLEMENT -> PENDING_ACCEPTANCE',
      result: 'SUCCESS',
      details: {
        supplement: {
          id: 901,
          acceptanceRecordId: 801,
          supplementNote: '补充来源照片说明',
          supplementedBy: '回收操作员',
          supplementedAt: '2026-10-08T10:05:00+08:00',
          attachments: [
            {
              id: 902,
              fileName: 'proof.txt',
              fileExt: 'txt',
              fileSizeBytes: 5,
              downloadUrl: '/api/v1/attachments/902/download',
            },
          ],
        },
      },
    },
  ];
}

async function flush() {
  await Promise.resolve();
  await nextTick();
}

describe('I3 acceptance page', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    const authStore = useAuthStore();
    authStore.currentUser = {
      id: 3,
      enterpriseId: 1,
      username: 'recycle_operator',
      displayName: '回收操作员',
      enabledStatus: 'ENABLED',
      roles: ['RECYCLE_OPERATOR'],
      permissions: ['acceptance:create', 'acceptance:supplement', 'attachment:upload', 'trace:read'],
    };
    authStore.token = 'token';
    vi.clearAllMocks();
    vi.mocked(listPendingAcceptances).mockResolvedValue([battery()]);
    vi.mocked(getBatteryTrace).mockResolvedValue(traceEvents());
    vi.mocked(getBatch).mockResolvedValue(batch());
  });

  it('loads pending batteries and saves an acceptance result', async () => {
    vi.mocked(createAcceptance).mockResolvedValue({ acceptanceRecordId: 801, batteryStatus: 'ACCEPTED_PENDING_INBOUND' });
    const wrapper = mountWithPlugins(AcceptancePendingView);
    await flush();

    expect(listPendingAcceptances).toHaveBeenCalled();
    (wrapper.vm as unknown as { openAcceptance: (row: Battery) => void }).openAcceptance(battery());
    Object.assign((wrapper.vm as unknown as { acceptanceForm: Record<string, string> }).acceptanceForm, {
      acceptanceResult: 'PASS',
      identityCheckResult: '身份一致',
      appearanceCheckResult: '外观完整',
      documentCheckResult: '资料完整',
    });
    await (wrapper.vm as unknown as { saveAcceptance: () => Promise<void> }).saveAcceptance();

    expect(createAcceptance).toHaveBeenCalledWith(701, expect.objectContaining({
      acceptanceResult: 'PASS',
      identityCheckResult: '身份一致',
    }));
  });

  it('uploads evidence and supplements a pending battery', async () => {
    const pendingSupplement = battery({ id: 702, lifecycleStatus: 'PENDING_SUPPLEMENT', systemTraceCode: 'BAT-702' });
    const attachment: Attachment = {
      id: 901,
      fileName: 'proof.txt',
      fileExt: 'txt',
      fileSizeBytes: 5,
      bindingStatus: 'TEMP',
      expiresAt: '2026-10-08T00:00:00+08:00',
    };
    vi.mocked(listPendingAcceptances).mockResolvedValue([pendingSupplement]);
    vi.mocked(uploadAttachment).mockResolvedValue(attachment);
    vi.mocked(supplementAcceptance).mockResolvedValue({ ...pendingSupplement, lifecycleStatus: 'PENDING_ACCEPTANCE' });
    const wrapper = mountWithPlugins(AcceptancePendingView);
    await flush();

    (wrapper.vm as unknown as { openSupplement: (row: Battery) => void }).openSupplement(pendingSupplement);
    (wrapper.vm as unknown as { supplementForm: { supplementNote: string } }).supplementForm.supplementNote = '补充说明';
    await (wrapper.vm as unknown as { uploadSelectedFile: (event: Event) => Promise<void> }).uploadSelectedFile({
      target: { files: [new File(['proof'], 'proof.txt', { type: 'text/plain' })], value: '' },
    } as unknown as Event);
    await (wrapper.vm as unknown as { saveSupplement: () => Promise<void> }).saveSupplement();

    expect(uploadAttachment).toHaveBeenCalledWith(expect.objectContaining({ name: 'proof.txt' }));
    expect(supplementAcceptance).toHaveBeenCalledWith(702, {
      supplementNote: '补充说明',
      attachmentIds: [901],
    });
  });

  it('shows acceptance history details and downloads attachments from the trace page', async () => {
    vi.mocked(downloadAttachment).mockResolvedValue(undefined);
    const wrapper = mountWithPlugins(BatteryTraceView);
    await flush();

    expect(getBatteryTrace).toHaveBeenCalledWith(701);
    expect(wrapper.text()).toContain('身份一致');
    expect(wrapper.text()).toContain('外观需说明');
    expect(wrapper.text()).toContain('资料缺失');
    expect(wrapper.text()).toContain('缺少来源照片');
    expect(wrapper.text()).toContain('补充来源照片说明');
    expect(wrapper.text()).toContain('proof.txt');

    await wrapper.findAll('button').find((button) => button.text().includes('proof.txt'))?.trigger('click');
    expect(downloadAttachment).toHaveBeenCalledWith(902, 'proof.txt');
  });

  it('shows batch acceptance progress summary in the batch detail page', async () => {
    const wrapper = mountWithPlugins(BatchDetailView);
    await flush();

    expect(getBatch).toHaveBeenCalledWith(701);
    expect(wrapper.text()).toContain('待验收 1');
    expect(wrapper.text()).toContain('待补充 1');
    expect(wrapper.text()).toContain('通过待入库 1');
    expect(wrapper.text()).toContain('不通过 1');
  });
});
