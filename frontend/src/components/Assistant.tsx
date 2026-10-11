import { useEffect, useRef, useState } from 'react';
import { ExternalLink, Send, Trash2 } from 'lucide-react';
import { displayTime, numeric, queryString, request, safeLink } from '../api';
import type { Analytics, Filters, Health } from '../types';

interface Status { configured: boolean; model: string; webConfigured: boolean; privacy: string }
interface Source {
  id: string; kind: 'statistics' | 'article' | 'web'; title?: string; url?: string; articleId?: number;
  domain?: string; first_seen_at?: string; publishedAt?: string; retrievedAt?: string; text?: string;
  from?: string; to?: string; keyword?: string; data?: unknown;
}
interface Answer {
  status: 'answered' | 'partial' | 'insufficient'; claims: { text: string; citations: string[] }[];
  limitation: string; sources: Source[]; current: Analytics; previous: Analytics; freshness: Health;
  web: { status: string; message: string; query: string }; keyword: string; generatedAt: string; model: string;
}
interface Exchange { question: string; answer: Answer }
const suggestions = ['Tema apa yang paling banyak diberitakan dalam periode ini?',
  'Bagaimana perubahan volume dibanding periode sebelumnya?', 'Media mana yang paling banyak memberitakan dan bagaimana nada beritanya?'];

export function Assistant({ filters, onArticle }: { filters: Filters; onArticle: (id: number) => void }) {
  const [status, setStatus] = useState<Status>();
  const [statusError, setStatusError] = useState('');
  const [question, setQuestion] = useState('');
  const [useWeb, setUseWeb] = useState(false);
  const [exchanges, setExchanges] = useState<Exchange[]>([]);
  const [pending, setPending] = useState('');
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  const active = useRef<AbortController | null>(null);
  const bottom = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const controller = new AbortController();
    setStatusError('');
    request<Status>('/assistant/status', controller.signal).then(setStatus)
      .catch(exc => { if (!controller.signal.aborted) setStatusError(exc.message); });
    return () => { controller.abort(); active.current?.abort(); };
  }, [reload]);
  useEffect(() => { if (pending || exchanges.length) bottom.current?.scrollIntoView({ block: 'nearest' }); }, [pending, exchanges.length]);

  async function ask(event: React.FormEvent) {
    event.preventDefault();
    const text = question.trim();
    if (!text || active.current || !status?.configured) return;
    const controller = new AbortController(); active.current = controller;
    setPending(text); setError('');
    const history = exchanges.slice(-3).flatMap(item => [{ role: 'user', content: item.question },
      { role: 'assistant', content: item.answer.claims.map(claim => claim.text).join('\n').slice(0, 6000) || item.answer.limitation }]);
    try {
      const query = Object.fromEntries(new URLSearchParams(queryString(filters)));
      const answer = await request<Answer>('/assistant', controller.signal, { method: 'POST',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...query, question: text, useWeb, history }) }, 185000);
      if (!controller.signal.aborted) { setExchanges(items => [...items, { question: text, answer }]); setQuestion(''); }
    } catch (exc) {
      if (!controller.signal.aborted) setError(exc instanceof Error ? exc.message : 'Asisten tidak dapat dihubungi.');
    } finally { if (active.current === controller) { active.current = null; setPending(''); } }
  }

  return <section className="panel assistant-panel">
    <div className="panel-title"><h2>Tanya pemberitaan</h2><button className="text-button" disabled={!!pending || !exchanges.length}
      onClick={() => { setExchanges([]); setError(''); }}><Trash2 size={15} />Percakapan baru</button></div>
    <div className="assistant-scope">{displayTime(new Date(filters.from).toISOString())} sampai sebelum {displayTime(new Date(filters.to).toISOString())} WIB
      <span>{filters.scope === 'indonesian_outlet' ? 'Media Indonesia' : filters.scope === 'mentions_indonesia' ? 'Menyebut Indonesia' : 'Keduanya'}
        {filters.q && ` · Kata kunci: ${filters.q}`}{filters.domain && ` · ${filters.domain}`}</span></div>
    {statusError && <div className="error-box" role="alert"><span>{statusError}</span><button onClick={() => setReload(value => value + 1)}>Coba lagi</button></div>}
    {status && !status.configured && <div className="error-box" role="status">Asisten belum tersedia. Hubungi pengelola aplikasi untuk mengaktifkannya.</div>}
    {!exchanges.length && <div className="assistant-start"><p>Tanyakan tren, perbandingan periode, atau konteks sebuah isu.</p>
      <div className="assistant-suggestions">{suggestions.map(text => <button className="secondary-button" key={text} disabled={!!pending} onClick={() => setQuestion(text)}>{text}</button>)}</div></div>}
    <div className="assistant-thread" aria-live="polite">{exchanges.map((item, index) => <article className="assistant-exchange" key={index}>
      <p className="assistant-question">{item.question}</p>
      <div className="assistant-answer"><div className="assistant-answer-meta"><strong>{item.answer.status === 'answered' ? 'Analisis' : item.answer.status === 'partial' ? 'Jawaban sebagian' : 'Bukti belum cukup'}</strong>
        <span>{displayTime(item.answer.generatedAt)} WIB</span></div>
        <div className="assistant-metrics"><span>Periode aktif <strong>{numeric(item.answer.current.summary.total)} URL</strong></span>
          <span>Periode sebelumnya <strong>{numeric(item.answer.previous.summary.total)} URL</strong></span>
          <span>Observasi terbaru <strong>{displayTime(item.answer.current.summary.latest)} WIB</strong></span></div>
        {item.answer.claims.map((claim, claimIndex) => <div className="assistant-claim" key={claimIndex}><p>{claim.text}</p>
          <div className="assistant-citations">{claim.citations.map(id => <a key={id} href={`#source-${index}-${id}`} onClick={() => {
            const source = document.getElementById(`source-${index}-${id}`);
            const details = source?.closest('details'); if (details) details.open = true;
          }}>[{id}]</a>)}</div></div>)}
        <p className="method-note">{item.answer.limitation}</p>
        {item.answer.web.status !== 'disabled' && <p className={`assistant-web-status ${['failed', 'unconfigured', 'partial'].includes(item.answer.web.status) ? 'warning' : ''}`}>{item.answer.web.message}</p>}
        <details className="assistant-sources"><summary>Sumber dan angka ({item.answer.sources.length})</summary>
          {item.answer.sources.map(source => <div id={`source-${index}-${source.id}`} className="assistant-source" key={source.id}>
            <strong>[{source.id}] {source.title || source.url}</strong>
            {source.kind === 'statistics' ? <><span>Database · {source.keyword && `Pencarian: ${source.keyword} · `}{displayTime(source.from)} sampai sebelum {displayTime(source.to)} WIB</span><pre>{JSON.stringify(source.data, null, 2)}</pre></>
              : <><span>{source.kind === 'web' ? `Web · Diambil ${displayTime(source.retrievedAt)} WIB${source.publishedAt ? ` · Publikasi: ${source.publishedAt}` : ' · Tanggal publikasi tidak tersedia'}` : `${source.domain} · Observasi ${displayTime(source.first_seen_at)} WIB`}</span>
                {source.text && <p>{source.text}</p>}<div className="assistant-source-actions">
                  {source.articleId !== undefined && <button className="text-button" onClick={() => onArticle(source.articleId!)}>Lihat metadata</button>}
                  {source.url && safeLink(source.url) && <a href={safeLink(source.url)} target="_blank" rel="noopener noreferrer">Buka sumber <ExternalLink size={13} /></a>}</div></>}
          </div>)}
        </details></div>
    </article>)}{pending && <div className="assistant-pending" role="status"><p>{pending}</p><span>{useWeb ? 'Membaca database dan mencari konteks web...' : 'Menganalisis bukti database...'}</span></div>}</div>
    {error && <div className="error-box" role="alert">{error}</div>}
    <form className="assistant-form" onSubmit={ask}>
      <label htmlFor="assistant-question">Pertanyaan<textarea id="assistant-question" aria-label="Pertanyaan untuk asisten" value={question} onChange={event => setQuestion(event.target.value)}
        maxLength={1200} rows={3} required disabled={!!pending} placeholder="Contoh: Jelaskan isu yang paling menonjol dalam pemberitaan periode ini." /></label>
      <div className="assistant-form-actions"><label className="assistant-web-toggle"><input type="checkbox" aria-label="Cari di web" checked={useWeb}
        onChange={event => setUseWeb(event.target.checked)} disabled={!!pending || !status?.webConfigured} />Cari di web</label>
        {status && !status.webConfigured && <span>Pencarian web belum dikonfigurasi</span>}
        <button className="primary-button" type="submit" disabled={!!pending || !status?.configured || !question.trim()}><Send size={15} />Kirim</button></div>
    </form>
    <p className="method-note">Jawaban AI dapat keliru. Periksa sumber sebelum mengambil keputusan.</p>
    <details className="method-details"><summary>Privasi asisten</summary><p>{status?.privacy}</p></details>
    <div ref={bottom} />
  </section>;
}
