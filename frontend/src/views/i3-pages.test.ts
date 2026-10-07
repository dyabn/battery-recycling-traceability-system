import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

import {
  createAcceptance,
  listPendingAcceptances,
  supplementAcceptance,
  uploadAttachment,
  type Attachment,
  type Battery,
} from '../api/i2';
import { useAuthStore } from '../stores/auth';
import AcceptancePendingView from './acceptance/AcceptancePendingView.vue';

vi.mock('../api/i2', () => ({
  createAcceptance: vi.fn(),
  listPendingAcceptances: vi.fn(),
  supplementAcceptance: vi.fn(),
  uploadAttachment: vi.fn(),
}));

const routerPush = vi.fn();

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
});
