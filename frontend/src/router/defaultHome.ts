export function defaultHomePath(permissions: string[] = []) {
  const can = (permission: string) => permissions.includes(permission);

  if (can('permission:manage')) {
    return '/system/users';
  }

  if (can('inbound:create')) {
    return '/inbounds/pending';
  }

  if (can('inventory:read')) {
    return '/inventory';
  }

  if (can('batch:read')) {
    return '/batches';
  }

  if (can('battery:create')) {
    return '/batteries/register';
  }

  if (can('audit:read')) {
    return '/system/audit';
  }

  return '/403';
}
