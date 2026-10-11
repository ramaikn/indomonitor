export interface Article {
  id: number;
  url: string;
  title: string | null;
  domain: string;
  language: string | null;
  scope: string;
  first_seen_at: string;
  last_seen_at: string;
  tone: number | null;
  themes: string[];
  locations: string[];
  last_run_id: string;
  observations?: Pick<Run, 'id' | 'adapter' | 'artifact' | 'started_at' | 'status'>[];
}
export interface Page {
  items: Article[];
  total: number;
  page: number;
  size: number;
}
export interface Analytics {
  summary: { total: number; outlets: number; tone_count: number; mean_tone: number | null; titled: number; earliest: string | null; latest: string | null; local_outlets: number; indonesia_mentions: number };
  timeline: { bucket: string; count: number; mean_tone: number | null }[];
  sources: { domain: string; count: number; mean_tone: number | null }[];
  themes: { theme: string; count: number }[];
  tones: { label: string; count: number }[];
  method: { clock: string; denominator: string; tone: string; missingBuckets: string };
}
export interface Run {
  id: string;
  adapter: string;
  artifact: string;
  started_at: string;
  finished_at: string | null;
  status: string;
  fetched: number;
  accepted: number;
  inserted: number;
  updated: number;
  duplicates: number;
  rejected: number;
  out_of_scope: number;
  error: string | null;
}
export interface Health {
  database: string;
  articles: number;
  checkedAt: string;
  lastRun: Partial<Run>;
  lastSuccess: Partial<Run>;
}
export interface Evidence {
  items: { left: Article; right: Article; similarity: number; reason: string }[];
  candidateCount: number;
  pairCount: number;
  method: string;
  limitation: string;
}
export interface Watchlist { id: number; name: string; keyword: string; threshold: number }
export interface Alert { watchlist: Watchlist; count: number; triggered: boolean; period: string; method: string }
export interface Filters { q: string; domain: string; scope: string; from: string; to: string }
