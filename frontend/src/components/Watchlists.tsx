import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { numeric, request } from '../api';
import type { Alert } from '../types';

export function Watchlists({ onSelect }: { onSelect: (keyword: string) => void }) {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [error, setError] = useState('');
  const [key, setKey] = useState('');
  const [name, setName] = useState('');
  const [keyword, setKeyword] = useState('');
  const [threshold, setThreshold] = useState(10);
  const [busy, setBusy] = useState(false);
  async function load() { setAlerts(await request<Alert[]>('/alerts')); }
  useEffect(() => { const controller = new AbortController(); request<Alert[]>('/alerts', controller.signal).then(setAlerts).catch(exc => { if (!controller.signal.aborted) setError(exc.message); }); return () => controller.abort(); }, []);
  async function change(action: 'create' | 'delete', id?: number) {
    setBusy(true); setError('');
    try {
      await request(action === 'create' ? '/watchlists' : `/watchlists/${id}`, undefined, {
        method: action === 'create' ? 'POST' : 'DELETE', headers: { 'Content-Type': 'application/json', 'X-Admin-Key': key },
        body: action === 'create' ? JSON.stringify({ name, keyword, threshold }) : undefined,
      });
      await load(); if (action === 'create') { setName(''); setKeyword(''); }
    } catch (exc) { setError((exc as Error).message); } finally { setBusy(false); }
  }
  return <section className="panel watchlist-panel"><div className="panel-title"><h2>Daftar pantauan</h2><span>24 jam terakhir</span></div>
    {error && <p role="alert" className="error-box">{error}</p>}
    <div className="watchlist-grid">{alerts.map(alert => <article key={alert.watchlist.id} className="watch-card">
      <div><button className="text-button" onClick={() => onSelect(alert.watchlist.keyword)}>{alert.watchlist.name}</button><button disabled={busy || !key} onClick={() => change('delete', alert.watchlist.id)} aria-label={`Hapus ${alert.watchlist.name}`}><Trash2 size={16} /></button></div>
      <p>{alert.watchlist.keyword}</p><strong>{numeric(alert.count)} URL</strong><span className={alert.triggered ? 'alert-text' : 'muted'}>{alert.triggered ? 'Ambang pantauan tercapai' : `Ambang ${alert.watchlist.threshold} URL`}</span>
    </article>)}</div>
    {!alerts.length && <p className="empty-state">Belum ada topik pantauan. Tambahkan topik yang ingin diperiksa.</p>}
    <form className="watchlist-form" onSubmit={event => { event.preventDefault(); void change('create'); }}>
      <label>Nama pantauan<input value={name} onChange={event => setName(event.target.value)} maxLength={100} required /></label>
      <label>Kata kunci<input value={keyword} onChange={event => setKeyword(event.target.value)} maxLength={100} required /></label>
      <label>Ambang URL<input type="number" min={1} max={10000} value={threshold} onChange={event => setThreshold(Number(event.target.value))} required /></label>
      <label>Kunci admin<input type="password" autoComplete="off" value={key} onChange={event => setKey(event.target.value)} required /></label>
      <button className="primary-button" disabled={busy}><Plus size={16} /> Tambahkan</button>
    </form><details className="method-details"><summary>Tentang pantauan</summary><p>Pantauan menghitung berita yang cocok dengan kata kunci pada judul, URL, atau tema. Ambang bukan penilaian krisis. Kunci admin diperlukan untuk mengubah pantauan dan tidak disimpan di browser.</p></details>
  </section>;
}
