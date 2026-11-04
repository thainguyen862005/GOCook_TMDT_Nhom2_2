export function money(value: number | string | null | undefined): string {
  const amount = Number(value ?? 0);
  return `${new Intl.NumberFormat('vi-VN').format(amount)}đ`;
}

export function dateTime(value: string | number | Date | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

export function initials(name = ''): string {
  return name.trim().split(/\s+/).slice(-2).map((part) => part[0]?.toUpperCase()).join('') || '--';
}

/** Workflow API may return ISO strings or Unix timestamps encoded as strings. */
export function requestDate(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  const raw = String(value).trim();
  const numeric = /^\d{10,13}$/.test(raw) ? Number(raw) : null;
  const input = numeric === null ? raw : numeric < 100_000_000_000 ? numeric * 1000 : numeric;
  const date = new Date(input);
  if (Number.isNaN(date.getTime())) return raw;
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(date);
}
