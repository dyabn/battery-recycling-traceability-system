import { http } from './http';

export interface ApiEnvelope<T> {
  code: string;
  message: string;
  traceId: string;
  data: T;
}

export interface Battery {
  id: number;
  enterpriseId: number;
  systemTraceCode: string;
  originalCode?: string | null;
  batteryType: 'PACK';
  batteryModel?: string | null;
  manufacturer?: string | null;
  batteryChemistry: string;
  nominalCapacity?: number | null;
  productionDate?: string | null;
  currentResponsibleEnterpriseId: number;
  lifecycleStatus: string;
  duplicateStatus: string;
  version: number;
}

export interface RecycleBatch {
  id: number;
  enterpriseId: number;
  batchNo: string;
  sourceType: string;
  sourceSubjectName: string;
  handoverDate: string;
  handoverLocation?: string | null;
  relatedDocumentNo?: string | null;
  handoverPerson?: string | null;
  remark?: string | null;
  batchStatus: string;
  submittedAt?: string | null;
  createdBy: number;
  createdAt: string;
  updatedBy?: number | null;
  updatedAt: string;
  version: number;
  batteries: Battery[];
}

export interface BatchPayload {
  sourceType: string;
  sourceSubjectName: string;
  handoverDate: string;
  handoverLocation?: string;
  relatedDocumentNo?: string;
  handoverPerson?: string;
  remark?: string;
  attachmentIds?: number[];
}

export interface BatteryPayload {
  originalCode?: string;
  batteryType: 'PACK';
  batteryModel?: string;
  manufacturer?: string;
  batteryChemistry: string;
  nominalCapacity?: number | null;
  productionDate?: string;
}

export interface BatteryRegistrationResult {
  resultType: 'BATTERY_CREATED' | 'DUPLICATE_REVIEW_REQUIRED';
  battery?: Battery;
  candidateId?: number;
  candidateStatus?: string;
  matchedBatteryIds: number[];
}

export interface TraceEvent {
  eventName: string;
  objectCode: string;
  operator: string;
  occurredAt: string;
  statusChange: string;
  result: string;
}

const pendingIdempotencyKeys = new Map<string, string>();

function idempotencyKey() {
  return crypto.randomUUID();
}

function normalizeForFingerprint(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(normalizeForFingerprint);
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .filter(([, entry]) => entry !== undefined)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, entry]) => [key, normalizeForFingerprint(entry)]),
    );
  }
  return value;
}

function fingerprint(value: unknown) {
  return JSON.stringify(normalizeForFingerprint(value));
}

function keyForOperation(operationCode: string, operationFingerprint: string) {
  const mapKey = `${operationCode}:${operationFingerprint}`;
  let key = pendingIdempotencyKeys.get(mapKey);
  if (!key) {
    key = idempotencyKey();
    pendingIdempotencyKeys.set(mapKey, key);
  }
  return { mapKey, key };
}

async function withIdempotency<T>(
  operationCode: string,
  operationFingerprint: string,
  request: (key: string) => Promise<T>,
  explicitKey?: string,
) {
  if (explicitKey) {
    return request(explicitKey);
  }
  const { mapKey, key } = keyForOperation(operationCode, operationFingerprint);
  const response = await request(key);
  pendingIdempotencyKeys.delete(mapKey);
  return response;
}

export function clearPendingI2IdempotencyKeys() {
  pendingIdempotencyKeys.clear();
}

export async function listBatches(status?: string) {
  const response = await http.get<ApiEnvelope<RecycleBatch[]>>('/recycle-batches', { params: { status: status || undefined } });
  return response.data.data;
}

export async function getBatch(id: number) {
  const response = await http.get<ApiEnvelope<RecycleBatch>>(`/recycle-batches/${id}`);
  return response.data.data;
}

export async function createBatch(payload: BatchPayload, key?: string) {
  return withIdempotency('CREATE_RECYCLE_BATCH', fingerprint(payload), async (idempotencyKeyValue) => {
    const response = await http.post<ApiEnvelope<RecycleBatch>>('/recycle-batches', payload, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function updateBatch(id: number, payload: BatchPayload, key?: string) {
  return withIdempotency('UPDATE_RECYCLE_BATCH', fingerprint({ id, payload }), async (idempotencyKeyValue) => {
    const response = await http.put<ApiEnvelope<RecycleBatch>>(`/recycle-batches/${id}`, payload, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function createBattery(payload: BatteryPayload, key?: string) {
  return withIdempotency('CREATE_BATTERY', fingerprint(payload), async (idempotencyKeyValue) => {
    const response = await http.post<ApiEnvelope<BatteryRegistrationResult>>('/batteries', payload, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function checkDuplicate(originalCode: string) {
  const response = await http.post<ApiEnvelope<{ duplicated: boolean; matchedBatteryIds: number[] }>>('/batteries/duplicate-check', { originalCode });
  return response.data.data;
}

export async function resolveDuplicate(candidateId: number, payload: { reviewResult: string; existingBatteryId?: number; duplicateReason?: string }, key?: string) {
  return withIdempotency('RESOLVE_DUPLICATE', fingerprint({ candidateId, payload }), async (idempotencyKeyValue) => {
    const response = await http.post<ApiEnvelope<Battery>>(`/battery-registration-candidates/${candidateId}/duplicate-resolution`, payload, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function addBatteryToBatch(batchId: number, batteryId: number, key?: string) {
  return withIdempotency('ADD_BATTERY_TO_BATCH', fingerprint({ batchId, batteryId }), async (idempotencyKeyValue) => {
    const response = await http.post<ApiEnvelope<RecycleBatch>>(`/recycle-batches/${batchId}/batteries`, { batteryId }, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function submitBatch(batchId: number, key?: string) {
  return withIdempotency('SUBMIT_RECYCLE_BATCH', String(batchId), async (idempotencyKeyValue) => {
    const response = await http.post<ApiEnvelope<RecycleBatch>>(`/recycle-batches/${batchId}/submit`, undefined, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function getBatteryTrace(batteryId: number) {
  const response = await http.get<ApiEnvelope<TraceEvent[]>>(`/batteries/${batteryId}/trace`);
  return response.data.data;
}
