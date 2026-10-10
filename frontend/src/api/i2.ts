import { http } from './http';
import { clearPendingI2IdempotencyKeys, completeIdempotencyKey, nextIdempotencyKey } from './idempotencyRegistry';

export type ApiId = string;

export interface ApiEnvelope<T> {
  code: string;
  message: string;
  traceId: string;
  data: T;
}

export interface Battery {
  id: ApiId;
  enterpriseId: ApiId;
  systemTraceCode: string;
  originalCode?: string | null;
  batteryType: 'PACK';
  batteryModel?: string | null;
  manufacturer?: string | null;
  batteryChemistry: string;
  nominalCapacity?: number | null;
  productionDate?: string | null;
  currentResponsibleEnterpriseId: ApiId;
  lifecycleStatus: string;
  duplicateStatus: string;
  version: number;
}

export interface RecycleBatch {
  id: ApiId;
  enterpriseId: ApiId;
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
  createdBy: ApiId;
  createdAt: string;
  updatedBy?: ApiId | null;
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
  attachmentIds?: ApiId[];
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
  candidateId?: ApiId;
  candidateStatus?: string;
  matchedBatteryIds: ApiId[];
}

export interface TraceEvent {
  eventName: string;
  objectCode: string;
  operator: string;
  occurredAt: string;
  statusChange: string;
  result: string;
  details?: {
    acceptance?: {
      id: ApiId;
      acceptanceResult: string;
      identityCheckResult: string;
      appearanceCheckResult: string;
      documentCheckResult: string;
      acceptanceNote?: string;
      acceptedBy: string;
      acceptedAt: string;
    };
    supplement?: {
      id: ApiId;
      acceptanceRecordId?: ApiId;
      supplementNote: string;
      supplementedBy: string;
      supplementedAt: string;
      attachments: Array<{
        id: ApiId;
        fileName: string;
        fileExt: string;
        fileSizeBytes: string;
        downloadUrl: string;
      }>;
    };
    inbound?: {
      id: ApiId;
      inboundNo: string;
      inboundAt: string;
      warehouseCode: string;
      warehouseName: string;
      locationCode: string;
      inventoryId: ApiId;
      inboundBy: string;
    };
  };
}

export interface AcceptancePayload {
  acceptanceResult: 'PASS' | 'NEED_SUPPLEMENT' | 'REJECT';
  identityCheckResult: string;
  appearanceCheckResult: string;
  documentCheckResult: string;
  acceptanceNote?: string;
}

export interface AcceptanceResult {
  acceptanceRecordId: ApiId;
  batteryStatus: string;
}

export interface AcceptanceSupplementPayload {
  supplementNote?: string;
  attachmentIds?: ApiId[];
}

export interface Attachment {
  id: ApiId;
  fileName: string;
  fileExt: string;
  fileSizeBytes: string;
  contentSha256?: string | null;
  bindingStatus: 'TEMP' | 'BOUND';
  expiresAt?: string | null;
}

export interface Warehouse {
  id: ApiId;
  enterpriseId: ApiId;
  warehouseCode: string;
  warehouseName: string;
  enabledStatus: string;
}

export interface WarehouseLocation {
  id: ApiId;
  enterpriseId: ApiId;
  warehouseId: ApiId;
  locationCode: string;
  enabledStatus: string;
}

export interface InboundPayload {
  warehouseId: ApiId;
  locationId: ApiId;
}

export interface InboundResult {
  inboundRecordId: ApiId;
  inventoryId: ApiId;
  batteryStatus: string;
}

export interface InventoryItem {
  id: ApiId;
  enterpriseId: ApiId;
  batteryId: ApiId;
  systemTraceCode: string;
  warehouseId: ApiId;
  warehouseCode: string;
  warehouseName: string;
  locationId: ApiId;
  locationCode: string;
  inboundRecordId: ApiId;
  inboundAt: string;
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

async function fileContentFingerprint(file: File) {
  const bytes = new Uint8Array(await file.arrayBuffer());
  if (globalThis.crypto?.subtle) {
    const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes);
    return Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, '0')).join('');
  }
  return Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
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
  const { mapKey, key } = nextIdempotencyKey(operationCode, operationFingerprint);
  const response = await request(key);
  completeIdempotencyKey(mapKey);
  return response;
}

export { clearPendingI2IdempotencyKeys };

export async function listBatches(status?: string) {
  const response = await http.get<ApiEnvelope<RecycleBatch[]>>('/recycle-batches', { params: { status: status || undefined } });
  return response.data.data;
}

export async function getBatch(id: ApiId) {
  const response = await http.get<ApiEnvelope<RecycleBatch>>(`/recycle-batches/${id}`);
  return response.data.data;
}

export async function createBatch(payload: BatchPayload, key?: string) {
  return withIdempotency('CREATE_RECYCLE_BATCH', fingerprint(payload), async (idempotencyKeyValue) => {
    const response = await http.post<ApiEnvelope<RecycleBatch>>('/recycle-batches', payload, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function updateBatch(id: ApiId, payload: BatchPayload, key?: string) {
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
  const response = await http.post<ApiEnvelope<{ duplicated: boolean; matchedBatteryIds: ApiId[] }>>('/batteries/duplicate-check', { originalCode });
  return response.data.data;
}

export async function resolveDuplicate(candidateId: ApiId, payload: { reviewResult: string; existingBatteryId?: ApiId; duplicateReason?: string }, key?: string) {
  return withIdempotency('RESOLVE_DUPLICATE', fingerprint({ candidateId, payload }), async (idempotencyKeyValue) => {
    const response = await http.post<ApiEnvelope<Battery>>(`/battery-registration-candidates/${candidateId}/duplicate-resolution`, payload, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function addBatteryToBatch(batchId: ApiId, batteryId: ApiId, key?: string) {
  return withIdempotency('ADD_BATTERY_TO_BATCH', fingerprint({ batchId, batteryId }), async (idempotencyKeyValue) => {
    const response = await http.post<ApiEnvelope<RecycleBatch>>(`/recycle-batches/${batchId}/batteries`, { batteryId }, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function submitBatch(batchId: ApiId, key?: string) {
  return withIdempotency('SUBMIT_RECYCLE_BATCH', String(batchId), async (idempotencyKeyValue) => {
    const response = await http.post<ApiEnvelope<RecycleBatch>>(`/recycle-batches/${batchId}/submit`, undefined, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function getBatteryTrace(batteryId: ApiId) {
  const response = await http.get<ApiEnvelope<TraceEvent[]>>(`/batteries/${batteryId}/trace`);
  return response.data.data;
}

export async function listPendingAcceptances() {
  const response = await http.get<ApiEnvelope<Battery[]>>('/acceptances/pending');
  return response.data.data;
}

export async function createAcceptance(batteryId: ApiId, payload: AcceptancePayload, key?: string) {
  return withIdempotency('CREATE_ACCEPTANCE', fingerprint({ batteryId, payload }), async (idempotencyKeyValue) => {
    const response = await http.post<ApiEnvelope<AcceptanceResult>>(`/batteries/${batteryId}/acceptances`, payload, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function supplementAcceptance(batteryId: ApiId, payload: AcceptanceSupplementPayload, key?: string) {
  return withIdempotency('SUPPLEMENT_ACCEPTANCE', fingerprint({ batteryId, payload }), async (idempotencyKeyValue) => {
    const response = await http.post<ApiEnvelope<Battery>>(`/batteries/${batteryId}/acceptance-supplements`, payload, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function uploadAttachment(file: File, key?: string) {
  const contentFingerprint = await fileContentFingerprint(file);
  return withIdempotency('UPLOAD_ATTACHMENT', fingerprint({ name: file.name, size: file.size, type: file.type, contentFingerprint }), async (idempotencyKeyValue) => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await http.post<ApiEnvelope<Attachment>>('/attachments', formData, {
      headers: { 'Idempotency-Key': idempotencyKeyValue, 'Content-Type': 'multipart/form-data' },
    });
    return response.data.data;
  }, key);
}

export async function downloadAttachment(attachmentId: ApiId, fileName: string) {
  const response = await http.get<Blob>(`/attachments/${attachmentId}/download`, { responseType: 'blob' });
  const blob = response.data instanceof Blob ? response.data : new Blob([response.data]);
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export async function listPendingInbounds() {
  const response = await http.get<ApiEnvelope<Battery[]>>('/inbounds/pending');
  return response.data.data;
}

export async function listWarehouses() {
  const response = await http.get<ApiEnvelope<Warehouse[]>>('/warehouses');
  return response.data.data;
}

export async function listWarehouseLocations(warehouseId: ApiId) {
  const response = await http.get<ApiEnvelope<WarehouseLocation[]>>(`/warehouses/${warehouseId}/locations`);
  return response.data.data;
}

export async function createInbound(batteryId: ApiId, payload: InboundPayload, key?: string) {
  return withIdempotency('CREATE_INBOUND', fingerprint({ batteryId, payload }), async (idempotencyKeyValue) => {
    const response = await http.post<ApiEnvelope<InboundResult>>(`/batteries/${batteryId}/inbounds`, payload, { headers: { 'Idempotency-Key': idempotencyKeyValue } });
    return response.data.data;
  }, key);
}

export async function listInventory(systemTraceCode?: string) {
  const response = await http.get<ApiEnvelope<InventoryItem[]>>('/inventory', { params: { systemTraceCode: systemTraceCode || undefined } });
  return response.data.data;
}
