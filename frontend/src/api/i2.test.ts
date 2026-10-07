import { beforeEach, describe, expect, it, vi } from 'vitest';

import { http } from './http';
import { clearPendingI2IdempotencyKeys, createBatch } from './i2';

vi.mock('./http', () => ({
  http: {
    post: vi.fn(),
    get: vi.fn(),
    put: vi.fn(),
  },
}));

describe('i2 api idempotency', () => {
  beforeEach(() => {
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
});
