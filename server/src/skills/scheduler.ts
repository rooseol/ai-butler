import cron, { type ScheduledTask } from "node-cron";
import { db } from "../db.js";
import { runAgent } from "../agents/runAgent.js";
import type { AgentName } from "../agents/types.js";
import { broadcast } from "../ws/index.js";
import { sendPush } from "../push/fcm.js";
import { nanoid } from "nanoid";

const activeTasks = new Map<string, ScheduledTask>();

function saveAgentMessage(agent: string, content: string) {
  const id = nanoid();
  const createdAt = Date.now();
  db.prepare(
    `INSERT INTO messages (id, agent, role, content, status, created_at) VALUES (?, ?, 'agent', ?, 'done', ?)`
  ).run(id, agent, content, createdAt);
  return { id, createdAt };
}

function runSchedule(scheduleId: string) {
  const schedule = db.prepare(`SELECT * FROM schedules WHERE id=?`).get(scheduleId) as any;
  if (!schedule || !schedule.enabled) return;
  const skill = db.prepare(`SELECT * FROM skills WHERE id=?`).get(schedule.skill_id) as any;
  if (!skill) return;

  const agent = skill.agent as AgentName;
  const handle = runAgent(agent, skill.prompt_template);
  let full = "";
  handle.onChunk(({ text }) => (full += text));
  handle.onDone((output) => {
    const saved = saveAgentMessage(agent, output || full);
    broadcast({ type: "chat_done", id: saved.id, agent, content: output || full, createdAt: saved.createdAt });
    db.prepare(`UPDATE schedules SET last_run_at=? WHERE id=?`).run(Date.now(), scheduleId);
    notifyDevices(`[${skill.name}] 스케줄 실행 완료`, (output || full).slice(0, 120));
  });
  handle.onError((err) => {
    saveAgentMessage(agent, `[스케줄 오류: ${skill.name}] ${err.message}`);
    notifyDevices(`[${skill.name}] 스케줄 실행 실패`, err.message.slice(0, 120));
  });
}

function notifyDevices(title: string, body: string) {
  const devices = db.prepare(`SELECT fcm_token FROM devices WHERE fcm_token IS NOT NULL`).all() as any[];
  for (const d of devices) sendPush(d.fcm_token, title, body);
}

/** DB의 schedules 테이블을 읽어 cron 작업을 (재)등록합니다. */
export function reloadSchedules() {
  for (const task of activeTasks.values()) task.stop();
  activeTasks.clear();

  const rows = db.prepare(`SELECT * FROM schedules WHERE enabled = 1`).all() as any[];
  for (const row of rows) {
    if (!cron.validate(row.cron)) {
      console.warn(`[scheduler] 잘못된 cron 표현식: ${row.cron} (schedule ${row.id})`);
      continue;
    }
    const task = cron.schedule(row.cron, () => runSchedule(row.id));
    activeTasks.set(row.id, task);
  }
  console.log(`[scheduler] ${activeTasks.size}개의 스케줄이 등록되었습니다.`);
}

/** 60초마다 DB 변경 사항을 반영 (스케줄 CRUD 후 별도 재시작 없이 반영되도록) */
export function startScheduleWatcher() {
  reloadSchedules();
  setInterval(reloadSchedules, 60_000);
}
