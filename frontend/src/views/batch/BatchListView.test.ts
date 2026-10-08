import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

import { listBatches, updateBatch } from '../../api/i2';
import { useAuthStore } from '../../stores/auth';
import BatchListView from './BatchListView.vue';

vi.mock('../../api/i2', () => ({
  createBatch: vi.fn(),
  listBatches: vi.fn(),
  updateBatch: vi.fn(),
}));

const draftBatch = {
  id: '9007199254740997',
  enterpriseId: '1',
  batchNo: 'RB-20260929-0001',
  sourceType: 'ENTERPRISE',
  sourceSubjectName: '动力电池回收企业A',
  handoverDate: '2026-09-29',
  handoverLocation: '上海仓',
  relatedDocumentNo: 'DOC-001',
  handoverPerson: '张三',
  remark: '草稿备注',
  batchStatus: 'DRAFT',
  createdBy: '1',
  createdAt: '2026-09-29T12:00:00+08:00',
  updatedAt: '2026-09-29T12:00:00+08:00',
  version: 0,
  batteries: [],
};

const submittedBatch = {
  ...draftBatch,
  id: '9007199254740998',
  batchNo: 'RB-20260929-0002',
  sourceSubjectName: '已提交企业',
  batchStatus: 'PENDING_ACCEPTANCE',
};

async function flush() {
  await Promise.resolve();
  await nextTick();
}

describe('BatchListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    const authStore = useAuthStore();
    authStore.currentUser = {
      id: '1',
      enterpriseId: '1',
      username: 'recycle_operator',
      displayName: '回收操作员',
      enabledStatus: 'ENABLED',
      roles: ['RECYCLE_OPERATOR'],
      permissions: ['batch:create', 'batch:read'],
    };
    vi.clearAllMocks();
    vi.mocked(listBatches).mockResolvedValue([draftBatch, submittedBatch]);
    vi.mocked(updateBatch).mockResolvedValue(draftBatch);
  });

  it('shows an edit action only for draft batches and fills the form before update', async () => {
    const wrapper = mount(BatchListView, {
      global: {
        plugins: [ElementPlus],
        mocks: {
          $router: { push: vi.fn() },
        },
      },
    });
    await flush();
    await flush();

    const editButtons = wrapper.findAll('button').filter((button) => button.text().includes('编辑'));
    expect(editButtons).toHaveLength(1);

    await editButtons[0].trigger('click');
    await flush();

    const inputs = wrapper.findAll('input');
    expect(inputs.some((input) => input.element.value === '动力电池回收企业A')).toBe(true);
    expect(inputs.some((input) => input.element.value === '2026-09-29')).toBe(true);
    expect(inputs.some((input) => input.element.value === '上海仓')).toBe(true);

    await (wrapper.vm as unknown as { save: () => Promise<void> }).save();
    await flush();

    expect(updateBatch).toHaveBeenCalledWith('9007199254740997', expect.objectContaining({
      sourceSubjectName: '动力电池回收企业A',
      handoverLocation: '上海仓',
      relatedDocumentNo: 'DOC-001',
      handoverPerson: '张三',
      remark: '草稿备注',
    }));
  });
});
