import { useEffect, useState } from 'react';
import { Activity, ArrowDownToLine, ArrowLeft, ArrowRight, Database, ExternalLink, FileText, Layers, MessageSquare, Radio, RefreshCw, Search, SlidersHorizontal } from 'lucide-react';
import { displayTime, numeric, queryString, request, safeLink } from './api';
import type { Analytics, Evidence, Filters, Health, Page, Run } from './types';
import { Timeline, SourceBars, ToneDistribution } from './components/Charts';
import { ArticleDrawer } from './components/ArticleDrawer';
import { Watchlists } from './components/Watchlists';
import { Assistant } from './components/Assistant';

type Tab = 'overview' | 'articles' | 'evidence' | 'operations' | 'watchlists' | 'assistant';
const tabs = [{ id: 'overview', title: 'Ringkasan', icon: Activity }, { id: 'articles', title: 'Berita', icon: FileText },
  { id: 'assistant', title: 'Asisten AI', icon: MessageSquare }, { id: 'evidence', title: 'Bukti lintas media', icon: Layers }, { id: 'watchlists', title: 'Pantauan', icon: Radio }, { id: 'operations', title: 'Pengambilan data', icon: Database }] as const;

function localInput(date: Date) { return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16); }
function defaults(): Filters {
  const now = new Date();
  return { q: '', domain: '', scope: 'indonesian_outlet', from: localInput(new Date(now.getTime() - 7 * 86400000)), to: localInput(new Date(now.getTime() + 60000)) };
}

export default function App() {
  const [tab, setTab] = useState<Tab>('overview');
  const [draft, setDraft] = useState<Filters>(defaults);
  const [filters, setFilters] = useState<Filters>(defaults);
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [analytics, setAnalytics] = useState<Analytics>();
  const [articles, setArticles] = useState<Page>();
  const [health, setHealth] = useState<Health>();
  const [runs, setRuns] = useState<Run[]>([]);
  const [evidence, setEvidence] = useState<Evidence>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<number>();

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError('');
    const query = queryString(filters);
    Promise.all([request<Analytics>(`/analytics?${query}`, controller.signal), request<Page>(`/articles?${query}&page=${page}`, controller.signal),
      request<Health>('/health', controller.signal), request<Run[]>('/runs', controller.signal), request<Evidence>(`/evidence?${query}`, controller.signal)])
      .then(([a, p, h, r, e]) => { setAnalytics(a); setArticles(p); setHealth(h); setRuns(r); setEvidence(e); })
      .catch(exc => { if (!controller.signal.aborted) setError(exc.message || 'API tidak dapat dihubungi.'); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [filters, page, refresh]);

  function apply(next: Filters) {
    if (!next.from || !next.to || new Date(next.from) >= new Date(next.to)) { setError('Tanggal awal harus sebelum tanggal akhir.'); return; }
    setDraft(next); setFilters(next); setPage(0);
  }
  const summary = analytics?.summary;
  const fresh = health?.lastSuccess.finished_at && Date.now() - new Date(health.lastSuccess.finished_at).getTime() < 90 * 60000;
  const failing = ['failed', 'partial'].includes(health?.lastRun.status || '');
  const sourceStatus = failing ? 'Pengambilan bermasalah' : !health?.lastSuccess.finished_at ? 'Belum ada pengambilan berhasil' : fresh ? 'Data baru tersedia' : 'Data perlu diperbarui';

  return <div className="app-shell">
    <aside className="sidebar"><a className="brand" href="#" aria-label="Indomonitor, ringkasan" onClick={() => setTab('overview')}><img src="/indomonitor-icon.svg" width="38" height="38" alt="" /><span>Indomonitor</span></a>
      <nav aria-label="Navigasi utama">{tabs.map(item => <button key={item.id} className={tab === item.id ? 'active' : ''} onClick={() => setTab(item.id)}><item.icon size={18} />{item.title}</button>)}</nav>
      <div className="sidebar-bottom"><a href="https://www.gdeltproject.org/" target="_blank" rel="noopener noreferrer">GDELT Project <ExternalLink size={13} /></a></div>
    </aside>
    <main>
      <header className="page-header"><div><h1>{tabs.find(item => item.id === tab)?.title}</h1></div><div className="header-actions">
        <button className="secondary-button" disabled={loading} onClick={() => { const next = { ...filters, to: localInput(new Date(Date.now() + 60000)) }; apply(next); setRefresh(value => value + 1); }}><RefreshCw size={16} className={loading ? 'loading-icon' : ''} />Perbarui tampilan</button>
        <a className="primary-button" href={`/api/report?${queryString(filters)}`}><ArrowDownToLine size={16} />Laporan</a></div></header>
      {tab === 'operations' ? <div className="source-status"><span className={`status-dot ${fresh && !failing ? 'online' : 'warning'}`} /><strong>{sourceStatus}</strong><span>Berhasil terakhir: {displayTime(health?.lastSuccess.finished_at)} WIB</span></div>
        : <div className="source-status"><span>Data terakhir: {displayTime(health?.lastSuccess.finished_at)} WIB</span><button onClick={() => setTab('operations')}>Lihat pengambilan <ArrowRight size={14} /></button></div>}
      <form className="filter-bar" onSubmit={event => { event.preventDefault(); apply(draft); }}>
        <label className="search-field"><Search size={17} /><input aria-label="Cari judul, URL, atau tema" placeholder="Cari judul, URL, atau tema" value={draft.q} onChange={event => setDraft({ ...draft, q: event.target.value })} maxLength={100} /></label>
        <label>Cakupan<select value={draft.scope} onChange={event => setDraft({ ...draft, scope: event.target.value })}><option value="indonesian_outlet">Media Indonesia</option><option value="mentions_indonesia">Menyebut Indonesia</option><option value="all">Keduanya</option></select></label>
        <label>Dari<input type="datetime-local" value={draft.from} onChange={event => setDraft({ ...draft, from: event.target.value })} required /></label>
        <label>Sebelum<input type="datetime-local" value={draft.to} onChange={event => setDraft({ ...draft, to: event.target.value })} required /></label>
        <button type="submit" className="filter-button"><SlidersHorizontal size={15} />Terapkan</button>
      </form>
      {filters.domain && <div className="active-filter">Media: <strong>{filters.domain}</strong><button onClick={() => apply({ ...filters, domain: '' })}>Hapus filter</button></div>}
      {error && <div className="error-box" role="alert"><strong>Data tidak dapat ditampilkan.</strong><span>{error}</span><button onClick={() => setRefresh(value => value + 1)}>Coba lagi</button></div>}
      {loading && <div role="status" className="loading-state">Mengambil hasil dari database...</div>}
      {!loading && !error && analytics && articles && <>
        {tab === 'overview' && <>
          <div className="stats-grid"><Stat title="Berita terkumpul" value={numeric(summary?.total)} /><Stat title="Domain media" value={numeric(summary?.outlets)} />
            <Stat title="Rata-rata nada" value={numeric(summary?.mean_tone, 2)} detail={`${numeric(summary?.tone_count)} berita berskor`} /><Stat title="Judul tersedia" value={numeric(summary?.titled)} detail={`Dari ${numeric(summary?.total)} berita`} /></div>
          <div className="overview-grid"><section className="panel"><div className="panel-title"><h2>Tren pemberitaan</h2><span>Berita / jam · WIB</span></div><Timeline data={analytics.timeline} /></section>
            <section className="panel"><div className="panel-title"><h2>Distribusi nada</h2><span>GDELT V2Tone</span></div><ToneDistribution data={analytics.tones} /></section></div>
          <div className="overview-grid"><section className="panel"><div className="panel-title"><h2>Media teratas</h2></div><SourceBars data={analytics.sources} total={analytics.summary.total} onSelect={domain => apply({ ...filters, domain })} /></section>
            <section className="panel"><div className="panel-title"><h2>Tema pemberitaan</h2><span>Kategori otomatis GDELT</span></div><div className="theme-list">{analytics.themes.slice(0, 9).map(item => <button key={item.theme} onClick={() => apply({ ...filters, q: item.theme })}><span>{item.theme.replaceAll('_', ' ')}</span><strong>{numeric(item.count)}</strong></button>)}{!analytics.themes.length && <p className="empty-chart">Belum ada tema pada metadata.</p>}</div></section></div>
          <section className="panel"><div className="panel-title"><h2>Berita terbaru</h2><button className="text-button" onClick={() => setTab('articles')}>Lihat semua <ArrowRight size={14} /></button></div><ArticleTable page={{ ...articles, items: articles.items.slice(0, 5) }} select={setSelected} /></section>
        </>}
        {tab === 'articles' && <section className="panel"><div className="panel-title"><h2>Daftar berita</h2><span>{numeric(articles.total)} URL unik</span></div><ArticleTable page={articles} select={setSelected} />
          <div className="pagination"><span>Halaman {page + 1} dari {Math.max(1, Math.ceil(articles.total / articles.size))}</span><div><button disabled={page === 0} onClick={() => setPage(page - 1)}><ArrowLeft size={16} />Sebelumnya</button><button disabled={(page + 1) * articles.size >= articles.total} onClick={() => setPage(page + 1)}>Berikutnya<ArrowRight size={16} /></button></div></div></section>}
        {tab === 'evidence' && evidence && <section className="panel"><div className="panel-title"><h2>Kemiripan judul lintas media</h2><span>{evidence.candidateCount} judul diperiksa</span></div>
          <div className="evidence-list">{evidence.items.map((pair, index) => <article key={index} className="evidence-card"><div className="evidence-score">{numeric(pair.similarity * 100, 1)}%<span>Kemiripan judul</span></div><div className="evidence-pair">{[pair.left, pair.right].map(item => <button key={item.id} onClick={() => setSelected(item.id)}><span className="source-name">{item.domain}</span><strong>{item.title}</strong><span>{displayTime(item.first_seen_at)} WIB</span></button>)}</div></article>)}</div>
          {!evidence.items.length && <p className="empty-state">Tidak ada pasangan judul serupa dalam periode ini.</p>}
          <details className="method-details"><summary>Metode kemiripan</summary><p>{evidence.method}. {evidence.limitation}</p></details></section>}
        {tab === 'operations' && <><div className="stats-grid"><Stat title="Database" value="Terhubung" /><Stat title="Total tersimpan" value={numeric(health?.articles)} detail="Semua periode" /><Stat title="Pengambilan terakhir" value={health?.lastRun.status || 'Belum ada'} detail={health?.lastRun.adapter?.toUpperCase()} /><Stat title="Arsip berhasil" value={numeric(runs.filter(run => run.status === 'success' && run.adapter === 'gkg').length)} detail="Dari 40 pengambilan terakhir" /></div>
          <section className="panel"><div className="panel-title"><h2>Riwayat pengambilan</h2><span>{runs.length} terbaru</span></div><div className="table-scroll"><table><thead><tr><th>Sumber</th><th>Waktu mulai (WIB)</th><th>Status</th><th>Diperiksa</th><th>Diterima</th><th>Baru</th><th>Duplikat</th><th>Ditolak</th></tr></thead><tbody>{runs.map(run => <tr key={run.id}><td><strong>{run.adapter.toUpperCase()}</strong><small className="run-id" title={run.id}>{run.id.slice(0, 8)}</small><small className="run-error">{run.error || run.artifact.split('/').pop()}</small></td><td>{displayTime(run.started_at)}</td><td><span className={`run-status ${run.status}`}>{run.status}</span></td><td>{numeric(run.fetched)}</td><td>{numeric(run.accepted)}</td><td>{numeric(run.inserted)}</td><td>{numeric(run.duplicates)}</td><td>{numeric(run.rejected)}</td></tr>)}</tbody></table></div>{!runs.length && <p className="empty-state">Belum ada pengambilan data.</p>}</section></>}
        {tab === 'watchlists' && <Watchlists onSelect={keyword => { apply({ ...filters, q: keyword }); setTab('articles'); }} />}
      </>}
      <div hidden={tab !== 'assistant'}><Assistant key={queryString(filters)} filters={filters} onArticle={setSelected} /></div>
      <details className="method-details"><summary>Tentang data</summary><p>Jumlah berita adalah URL unik dalam filter, bukan seluruh pemberitaan Indonesia. Waktu mengikuti observasi GDELT, bukan tanggal terbit. Jeda grafik berarti data tidak teramati, bukan nol berita. Tema dan nada dibuat otomatis; nada bukan opini publik. Kemiripan judul bukan bukti koordinasi.</p></details>
      <footer className="page-footer"><span>Data: <a href="https://www.gdeltproject.org/" target="_blank" rel="noopener noreferrer">GDELT Project</a>. Hak artikel tetap pada penerbit.</span><a href="https://github.com/ramaikn/indomonitor" target="_blank" rel="noopener noreferrer">Indomonitor · Open source</a></footer>
    </main>{selected !== undefined && <ArticleDrawer key={selected} id={selected} close={() => setSelected(undefined)} />}
  </div>;
}

function Stat({ title, value, detail }: { title: string; value: string; detail?: string }) {
  return <section className="stat-card"><h2>{title}</h2><strong>{value}</strong>{detail && <span>{detail}</span>}</section>;
}

function ArticleTable({ page, select }: { page: Page; select: (id: number) => void }) {
  return <>{page.items.length ? <div className="table-scroll"><table className="article-table"><thead><tr><th>Berita / URL sumber</th><th>Media</th><th>Teramati (WIB)</th><th>Nada</th><th><span className="sr-only">Buka sumber</span></th></tr></thead><tbody>{page.items.map(item => <tr key={item.id}>
    <td><button className="article-title" onClick={() => select(item.id)}>{item.title || item.url}</button>{!item.title && <small>Judul tidak tersedia dalam metadata</small>}</td><td>{item.domain}</td><td>{displayTime(item.first_seen_at)}</td><td className={item.tone != null && item.tone < -1 ? 'negative-number' : ''}>{numeric(item.tone, 2)}</td><td><a className="icon-link" href={safeLink(item.url)} target="_blank" rel="noopener noreferrer" aria-label="Buka berita asli"><ExternalLink size={16} /></a></td>
  </tr>)}</tbody></table></div> : <p className="empty-state">Belum ada berita yang sesuai filter. Periksa cakupan, periode, atau status pengambilan.</p>}</>;
}
