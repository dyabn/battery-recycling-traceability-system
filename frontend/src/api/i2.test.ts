import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { http } from './http';
import { clearPendingI2IdempotencyKeys, createBatch, uploadAttachment } from './i2';
import { useAuthStore } from '../stores/auth';

vi.mock('./http', () => ({
  http: {
    post: vi.fn(),
    get: vi.fn(),
    put: vi.fn(),
  },
}));

describe('i2 api idempotency', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    clearPendingI2IdempotencyKeys();
    let counter = 0;
    vi.spyOn(crypto, 'randomUUID').mockImplementation(() => `uuid-${++counter}` as `${string}-${string}-${string}-${string}-${string}`);
  });

  it('reuses the same idempotency key when a logical operation is retried after an unknown result', async () => {
    const payload = {
      sourceType: 'ENTERPRISE',
      sourceSubjectName: '测试来源企业',
      handoverDate: '2026-09-29',
    };
    const response = {
      data: {
        data: {
          id: 1,
          enterpriseId: 1,
          batchNo: 'RB-20260929-0001',
          sourceType: 'ENTERPRISE',
          sourceSubjectName: '测试来源企业',
          handoverDate: '2026-09-29',
          batchStatus: 'DRAFT',
          createdBy: 1,
          createdAt: '2026-09-29T12:00:00+08:00',
          updatedAt: '2026-09-29T12:00:00+08:00',
          version: 0,
          batteries: [],
        },
      },
    };

    vi.mocked(http.post)
      .mockRejectedValueOnce(Object.assign(new Error('timeout'), { code: 'ECONNABORTED' }))
      .mockResolvedValueOnce(response);

    await expect(createBatch(payload)).rejects.toThrow('timeout');
    await expect(createBatch(payload)).resolves.toEqual(response.data.data);

    expect(vi.mocked(http.post).mock.calls[0][2]?.headers?.['Idempotency-Key']).toBe('uuid-1');
    expect(vi.mocked(http.post).mock.calls[1][2]?.headers?.['Idempotency-Key']).toBe('uuid-1');
  });

  it('uses a new idempotency key after the request content changes', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        data: {
          id: 1,
          enterpriseId: 1,
          batchNo: 'RB-20260929-0001',
          sourceType: 'ENTERPRISE',
          sourceSubjectName: '测试来源企业',
          handoverDate: '2026-09-29',
          batchStatus: 'DRAFT',
          createdBy: 1,
          createdAt: '2026-09-29T12:00:00+08:00',
          updatedAt: '2026-09-29T12:00:00+08:00',
          version: 0,
          batteries: [],
        },
      },
    });

    await createBatch({
      sourceType: 'ENTERPRISE',
      sourceSubjectName: '测试来源企业A',
      handoverDate: '2026-09-29',
    });
    await createBatch({
      sourceType: 'ENTERPRISE',
      sourceSubjectName: '测试来源企业B',
      handoverDate: '2026-09-29',
    });

    expect(vi.mocked(http.post).mock.calls[0][2]?.headers?.['Idempotency-Key']).toBe('uuid-1');
    expect(vi.mocked(http.post).mock.calls[1][2]?.headers?.['Idempotency-Key']).toBe('uuid-2');
  });

  it('clears pending retry idempotency keys when the session is cleared on logout or 401', async () => {
    const payload = {
      sourceType: 'ENTERPRISE',
      sourceSubjectName: '测试来源企业',
      handoverDate: '2026-09-29',
    };
    const response = {
      data: {
        data: {
          id: 1,
          enterpriseId: 1,
          batchNo: 'RB-20260929-0001',
          sourceType: 'ENTERPRISE',
          sourceSubjectName: '测试来源企业',
          handoverDate: '2026-09-29',
          batchStatus: 'DRAFT',
          createdBy: 1,
          createdAt: '2026-09-29T12:00:00+08:00',
          updatedAt: '2026-09-29T12:00:00+08:00',
          version: 0,
          batteries: [],
        },
      },
    };
    vi.mocked(http.post)
      .mockRejectedValueOnce(Object.assign(new Error('timeout'), { code: 'ECONNABORTED' }))
      .mockResolvedValueOnce(response);

    await expect(createBatch(payload)).rejects.toThrow('timeout');
    useAuthStore().clearSession();
    await expect(createBatch(payload)).resolves.toEqual(response.data.data);

    expect(vi.mocked(http.post).mock.calls[0][2]?.headers?.['Idempotency-Key']).toBe('uuid-1');
    expect(vi.mocked(http.post).mock.calls[1][2]?.headers?.['Idempotency-Key']).toBe('uuid-2');
  });

  it('uses a new attachment idempotency key when same name and size have different content', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        data: {
          id: 1,
          fileName: 'proof.txt',
          fileExt: 'txt',
          fileSizeBytes: 2,
          contentSha256: 'hash',
          bindingStatus: 'TEMP',
          expiresAt: '2026-10-08T12:00:00',
        },
      },
    });

    await uploadAttachment(new File(['aa'], 'proof.txt', { type: 'text/plain' }));
    await uploadAttachment(new File(['bb'], 'proof.txt', { type: 'text/plain' }));

    expect(vi.mocked(http.post).mock.calls[0][2]?.headers?.['Idempotency-Key']).toBe('uuid-1');
    expect(vi.mocked(http.post).mock.calls[1][2]?.headers?.['Idempotency-Key']).toBe('uuid-2');
  });
});
