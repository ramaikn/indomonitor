import { numeric } from '../api';
import type { Analytics } from '../types';

export function Timeline({ data }: { data: Analytics['timeline'] }) {
  if (!data.length) return <div className="empty-chart">Belum ada observasi pada periode ini.</div>;
  const max = Math.max(...data.map(item => item.count), 1);
  const label = (value: string) => new Intl.DateTimeFormat('id-ID', { day: '2-digit', month: 'short', hour: '2-digit', timeZone: 'Asia/Jakarta' }).format(new Date(value));
  const start = new Date(data[0].bucket).getTime();
  const span = new Date(data[data.length - 1].bucket).getTime() - start;
  const x = (bucket: string) => span === 0 ? 394 : 48 + (new Date(bucket).getTime() - start) / span * 692;
  const path = data.map((item, index) => {
    const gap = index > 0 && new Date(item.bucket).getTime() - new Date(data[index - 1].bucket).getTime() > 3600000;
    return `${index === 0 || gap ? 'M' : 'L'}${x(item.bucket)},${170 - item.count / max * 132}`;
  }).join(' ');
  return <div className="timeline"><svg viewBox="0 0 780 218" role="img" aria-label="Volume URL unik per jam observasi GDELT">
    {[0, .5, 1].map(value => <g key={value}><line x1="48" x2="740" y1={170 - value * 132} y2={170 - value * 132} className="grid-line" />
      <text x="34" y={174 - value * 132} textAnchor="end">{numeric(max * value)}</text></g>)}
    <path d={path} fill="none" stroke="var(--green)" strokeWidth="2.5" />
    {data.map(item => <circle key={item.bucket} cx={x(item.bucket)} cy={170 - item.count / max * 132} r="3.5" fill="var(--green)"><title>{label(item.bucket)} WIB: {item.count} URL</title></circle>)}
    <text x="48" y="204">{label(data[0].bucket)}</text><text x="740" y="204" textAnchor="end">{label(data[data.length - 1].bucket)} WIB</text>
  </svg></div>;
}

export function SourceBars({ data, total, onSelect }: { data: Analytics['sources']; total: number; onSelect: (domain: string) => void }) {
  const max = Math.max(...data.map(item => item.count), 1);
  return <div className="source-bars">{data.length ? data.slice(0, 8).map(item => <button key={item.domain} onClick={() => onSelect(item.domain)} className="source-row">
    <span className="source-label">{item.domain}</span><span className="bar-track"><span style={{ width: `${item.count / max * 100}%` }} /></span>
    <strong>{numeric(item.count)}</strong><span className="muted">{numeric(item.count / Math.max(total, 1) * 100, 1)}%</span>
  </button>) : <p className="empty-chart">Belum ada sumber pada filter ini.</p>}</div>;
}

const toneLabels: Record<string, string> = { negative_tone: 'Nada negatif (< −1)', near_zero: 'Mendekati nol (−1 sampai 1)', positive_tone: 'Nada positif (> 1)', unavailable: 'Tanpa skor' };
export function ToneDistribution({ data }: { data: Analytics['tones'] }) {
  const total = data.reduce((sum, item) => sum + item.count, 0);
  return <div className="tone-distribution">{data.map(item => <div key={item.label} className={`tone-row ${item.label}`}>
    <span>{toneLabels[item.label]}</span><strong>{numeric(item.count)}</strong><span>{numeric(item.count / Math.max(total, 1) * 100, 1)}%</span>
  </div>)}{!data.length && <p className="empty-chart">Belum tersedia.</p>}</div>;
}
