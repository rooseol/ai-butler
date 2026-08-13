import { Router } from "express";
import { db } from "../db.js";

export const chatRouter = Router();

// 최근 대화 히스토리 (agent 필터 + 페이지네이션)
chatRouter.get("/messages", (req, res) => {
  const agent = req.query.agent as string | undefined;
  const before = req.query.before ? Number(req.query.before) : Date.now() + 1;
  const limit = req.query.limit ? Number(req.query.limit) : 50;

  const rows = agent
    ? db
        .prepare(
          `SELECT * FROM messages WHERE agent = ? AND created_at < ? ORDER BY created_at DESC LIMIT ?`
        )
        .all(agent, before, limit)
    : db.prepare(`SELECT * FROM messages WHERE created_at < ? ORDER BY created_at DESC LIMIT ?`).all(before, limit);

  res.json((rows as any[]).reverse());
});
