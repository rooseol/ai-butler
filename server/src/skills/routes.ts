import { Router } from "express";
import { nanoid } from "nanoid";
import { db } from "../db.js";
import { runAgent } from "../agents/runAgent.js";
import type { AgentName } from "../agents/types.js";

export const skillsRouter = Router();

// ---- Skills (저장된 프롬프트 템플릿) ----
skillsRouter.get("/skills", (_req, res) => {
  res.json(db.prepare(`SELECT * FROM skills ORDER BY created_at DESC`).all());
});

skillsRouter.post("/skills", (req, res) => {
  const { name, description, agent, promptTemplate } = req.body;
  if (!name || !promptTemplate) return res.status(400).json({ error: "name, promptTemplate required" });
  const id = nanoid();
  db.prepare(
    `INSERT INTO skills (id, name, description, agent, prompt_template, created_at) VALUES (?, ?, ?, ?, ?, ?)`
  ).run(id, name, description || null, agent || "claude", promptTemplate, Date.now());
  res.status(201).json({ id });
});

skillsRouter.delete("/skills/:id", (req, res) => {
  db.prepare(`DELETE FROM skills WHERE id=?`).run(req.params.id);
  res.json({ ok: true });
});

// 스킬 즉시 실행 (결과는 REST 응답으로 동기 반환; 긴 작업은 WS 채팅쪽을 권장)
skillsRouter.post("/skills/:id/run", (req, res) => {
  const skill = db.prepare(`SELECT * FROM skills WHERE id=?`).get(req.params.id) as any;
  if (!skill) return res.status(404).json({ error: "not found" });
  const handle = runAgent(skill.agent as AgentName, skill.prompt_template);
  handle.onDone((full) => res.json({ ok: true, output: full }));
  handle.onError((err) => res.status(500).json({ error: err.message }));
});

// ---- Schedules (cron) ----
skillsRouter.get("/schedules", (_req, res) => {
  res.json(db.prepare(`SELECT * FROM schedules ORDER BY created_at DESC`).all());
});

skillsRouter.post("/schedules", (req, res) => {
  const { skillId, cron, enabled } = req.body;
  if (!skillId || !cron) return res.status(400).json({ error: "skillId, cron required" });
  const skill = db.prepare(`SELECT id FROM skills WHERE id=?`).get(skillId);
  if (!skill) return res.status(404).json({ error: "skill not found" });
  const id = nanoid();
  db.prepare(
    `INSERT INTO schedules (id, skill_id, cron, enabled, created_at) VALUES (?, ?, ?, ?, ?)`
  ).run(id, skillId, cron, enabled === false ? 0 : 1, Date.now());
  res.status(201).json({ id });
});

skillsRouter.put("/schedules/:id", (req, res) => {
  const { cron, enabled } = req.body;
  db.prepare(`UPDATE schedules SET cron=COALESCE(?, cron), enabled=COALESCE(?, enabled) WHERE id=?`).run(
    cron ?? null,
    enabled === undefined ? null : enabled ? 1 : 0,
    req.params.id
  );
  res.json({ ok: true });
});

skillsRouter.delete("/schedules/:id", (req, res) => {
  db.prepare(`DELETE FROM schedules WHERE id=?`).run(req.params.id);
  res.json({ ok: true });
});
