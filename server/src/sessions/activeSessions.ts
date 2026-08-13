import { db } from "../db.js";
import type { AgentName } from "../agents/types.js";

/** 에이전트별 "지금 이어가는 중" 세션 ID 조회/저장 (현재는 claude만 의미 있게 사용). */

export function getActiveSession(agent: AgentName): string | undefined {
  const row = db.prepare(`SELECT session_id FROM active_sessions WHERE agent = ?`).get(agent) as
    | { session_id: string }
    | undefined;
  return row?.session_id;
}

export function setActiveSession(agent: AgentName, sessionId: string): void {
  db.prepare(
    `INSERT INTO active_sessions (agent, session_id, updated_at) VALUES (?, ?, ?)
     ON CONFLICT(agent) DO UPDATE SET session_id = excluded.session_id, updated_at = excluded.updated_at`
  ).run(agent, sessionId, Date.now());
}

export function clearActiveSession(agent: AgentName): void {
  db.prepare(`DELETE FROM active_sessions WHERE agent = ?`).run(agent);
}
