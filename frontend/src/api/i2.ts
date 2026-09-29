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

function idempotencyKey() {
  return crypto.randomUUID();
}

export async function listBatches(status?: string) {
  const response = await http.get<ApiEnvelope<RecycleBatch[]>>('/recycle-batches', { params: { status: status || undefined } });
  return response.data.data;
}

export async function getBatch(id: number) {
  const response = await http.get<ApiEnvelope<RecycleBatch>>(`/recycle-batches/${id}`);
  return response.data.data;
}

export async function createBatch(payload: BatchPayload, key = idempotencyKey()) {
  const response = await http.post<ApiEnvelope<RecycleBatch>>('/recycle-batches', payload, { headers: { 'Idempotency-Key': key } });
  return response.data.data;
}

export async function updateBatch(id: number, payload: BatchPayload, key = idempotencyKey()) {
  const response = await http.put<ApiEnvelope<RecycleBatch>>(`/recycle-batches/${id}`, payload, { headers: { 'Idempotency-Key': key } });
  return response.data.data;
}

export async function createBattery(payload: BatteryPayload, key = idempotencyKey()) {
  const response = await http.post<ApiEnvelope<BatteryRegistrationResult>>('/batteries', payload, { headers: { 'Idempotency-Key': key } });
  return response.data.data;
}

export async function checkDuplicate(originalCode: string) {
  const response = await http.post<ApiEnvelope<{ duplicated: boolean; matchedBatteryIds: number[] }>>('/batteries/duplicate-check', { originalCode });
  return response.data.data;
}

export async function resolveDuplicate(candidateId: number, payload: { reviewResult: string; existingBatteryId?: number; duplicateReason?: string }, key = idempotencyKey()) {
  const response = await http.post<ApiEnvelope<Battery>>(`/battery-registration-candidates/${candidateId}/duplicate-resolution`, payload, { headers: { 'Idempotency-Key': key } });
  return response.data.data;
}

export async function addBatteryToBatch(batchId: number, batteryId: number, key = idempotencyKey()) {
  const response = await http.post<ApiEnvelope<RecycleBatch>>(`/recycle-batches/${batchId}/batteries`, { batteryId }, { headers: { 'Idempotency-Key': key } });
  return response.data.data;
}

export async function submitBatch(batchId: number, key = idempotencyKey()) {
  const response = await http.post<ApiEnvelope<RecycleBatch>>(`/recycle-batches/${batchId}/submit`, undefined, { headers: { 'Idempotency-Key': key } });
  return response.data.data;
}

export async function getBatteryTrace(batteryId: number) {
  const response = await http.get<ApiEnvelope<TraceEvent[]>>(`/batteries/${batteryId}/trace`);
  return response.data.data;
}
