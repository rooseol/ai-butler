import { DatabaseSync } from "node:sqlite";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

// Node.js 내장 sqlite 모듈 사용 (Node >= 22.5, 네이티브 빌드 도구 불필요)

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dataDir = path.resolve(__dirname, "..", "data");
if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir, { recursive: true });

export const db = new DatabaseSync(path.join(dataDir, "ai-butler.sqlite"));
db.exec("PRAGMA journal_mode = WAL;");

db.exec(`
CREATE TABLE IF NOT EXISTS messages (
  id TEXT PRIMARY KEY,
  agent TEXT NOT NULL,            -- 'claude' | 'codex' | 'gemini' | 'system'
  role TEXT NOT NULL,             -- 'user' | 'agent' | 'system'
  content TEXT NOT NULL,
  file_id TEXT,
  status TEXT NOT NULL DEFAULT 'done', -- 'pending' | 'streaming' | 'done' | 'error'
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS files (
  id TEXT PRIMARY KEY,
  filename TEXT NOT NULL,
  mime TEXT,
  size INTEGER,
  path TEXT NOT NULL,
  direction TEXT NOT NULL,        -- 'upload' (phone->server) | 'download' (server->phone)
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS calendar_events (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT,
  start_at INTEGER NOT NULL,
  end_at INTEGER,
  all_day INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS skills (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT,
  agent TEXT NOT NULL DEFAULT 'claude',
  prompt_template TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS schedules (
  id TEXT PRIMARY KEY,
  skill_id TEXT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
  cron TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  last_run_at INTEGER,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
  id TEXT PRIMARY KEY,
  name TEXT,
  fcm_token TEXT,
  paired_at INTEGER NOT NULL,
  last_seen_at INTEGER
);

-- 에이전트별로 "지금 이어가는 중인" 세션. 폰에서 메시지를 보낼 때마다
-- 여기 저장된 session_id로 --resume 해서 대화 맥락이 유지됩니다.
-- (현재는 claude만 채워짐 — codex/gemini는 세션 이어가기 미지원)
CREATE TABLE IF NOT EXISTS active_sessions (
  agent TEXT PRIMARY KEY,
  session_id TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
`);
