import { useEffect, useRef, useState } from 'react';
import { ExternalLink, X } from 'lucide-react';
import { displayTime, numeric, request, safeLink } from '../api';
import type { Article } from '../types';

export function ArticleDrawer({ id, close }: { id: number; close: () => void }) {
  const [item, setItem] = useState<Article>();
  const [error, setError] = useState('');
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    dialog.current?.showModal();
    const controller = new AbortController();
    request<Article>(`/articles/${id}`, controller.signal).then(setItem).catch(exc => { if (!controller.signal.aborted) setError(exc.message); });
    return () => controller.abort();
  }, [id]);
  return <dialog ref={dialog} className="article-dialog" onCancel={close}>
    <div className="drawer-header"><h2>Bukti sumber</h2><button onClick={close} aria-label="Tutup detail"><X size={20} /></button></div>
    {error ? <p role="alert" className="error-box">{error}</p> : !item ? <p role="status">Memuat bukti...</p> : <>
      <p className="source-name">{item.domain}</p><h3>{item.title || 'Judul tidak tersedia'}</h3>
      <a className="source-link" href={safeLink(item.url)} target="_blank" rel="noopener noreferrer">Buka berita asli <ExternalLink size={15} /></a>
      <dl className="detail-grid"><dt>Pertama teramati</dt><dd>{displayTime(item.first_seen_at)} WIB</dd><dt>Terakhir teramati</dt><dd>{displayTime(item.last_seen_at)} WIB</dd>
        <dt>Cakupan</dt><dd>{item.scope === 'indonesian_outlet' ? 'Media Indonesia' : 'Menyebut Indonesia'}</dd><dt>Bahasa metadata</dt><dd>{item.language || 'Tidak tersedia'}</dd>
        <dt>GDELT V2Tone</dt><dd>{numeric(item.tone, 2)}</dd></dl>
      <h4>Tema otomatis GDELT</h4><div className="tag-list">{item.themes.length ? item.themes.map(theme => <span key={theme}>{theme}</span>) : <p className="muted">Tidak tersedia.</p>}</div>
      <h4>Lokasi yang dikenali</h4><p>{item.locations.join(' · ') || 'Tidak tersedia.'}</p>
      <details className="method-details"><summary>Riwayat sumber</summary><div className="provenance">{item.observations?.map(run => <div key={run.id}><strong>{run.adapter.toUpperCase()} · {run.status}</strong>
        <span>{displayTime(run.started_at)} WIB</span><code>{run.artifact}</code><small>{run.id}</small></div>)}</div></details>
    </>}
  </dialog>;
}
