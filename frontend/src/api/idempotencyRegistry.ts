const pendingIdempotencyKeys = new Map<string, string>();

function idempotencyKey() {
  return crypto.randomUUID();
}

export function nextIdempotencyKey(operationCode: string, operationFingerprint: string) {
  const mapKey = `${operationCode}:${operationFingerprint}`;
  let key = pendingIdempotencyKeys.get(mapKey);
  if (!key) {
    key = idempotencyKey();
    pendingIdempotencyKeys.set(mapKey, key);
  }
  return { mapKey, key };
}

export function completeIdempotencyKey(mapKey: string) {
  pendingIdempotencyKeys.delete(mapKey);
}

export function clearPendingI2IdempotencyKeys() {
  pendingIdempotencyKeys.clear();
}
