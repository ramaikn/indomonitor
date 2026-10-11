import type { Filters } from './types';

export async function request<T>(path: string, signal?: AbortSignal, init?: RequestInit, timeoutMs = 20000): Promise<T> {
  const timeout = AbortSignal.timeout(timeoutMs);
  const response = await fetch(`/api${path}`, { ...init, signal: signal ? AbortSignal.any([signal, timeout]) : timeout });
  if (!response.ok) {
    let message = `Permintaan gagal (${response.status}).`;
    try { const body = await response.json(); message = body.message || message; } catch { /* Respons proxy dapat bukan JSON. */ }
    throw new Error(message);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function queryString(filters: Filters): string {
  return new URLSearchParams({ q: filters.q, domain: filters.domain, scope: filters.scope,
    from: new Date(filters.from).toISOString(), to: new Date(filters.to).toISOString() }).toString();
}

export function displayTime(value?: string | null): string {
  if (!value) return 'Belum tersedia';
  return new Intl.DateTimeFormat('id-ID', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Jakarta' }).format(new Date(value));
}

export function numeric(value: number | null | undefined, decimals = 0): string {
  return value == null ? 'Tidak tersedia' : new Intl.NumberFormat('id-ID', { maximumFractionDigits: decimals }).format(value);
}

export function safeLink(value: string): string | undefined {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) ? url.href : undefined; }
  catch { return undefined; }
}
