import { Router } from "express";
import { nanoid } from "nanoid";
import { db } from "../db.js";

export const calendarRouter = Router();

calendarRouter.get("/events", (req, res) => {
  const rows = db.prepare(`SELECT * FROM calendar_events ORDER BY start_at ASC`).all();
  res.json(rows);
});

calendarRouter.post("/events", (req, res) => {
  const { title, description, startAt, endAt, allDay } = req.body;
  if (!title || !startAt) return res.status(400).json({ error: "title, startAt required" });
  const id = nanoid();
  const now = Date.now();
  db.prepare(
    `INSERT INTO calendar_events (id, title, description, start_at, end_at, all_day, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
  ).run(id, title, description || null, startAt, endAt || null, allDay ? 1 : 0, now, now);
  res.status(201).json({ id });
});

calendarRouter.put("/events/:id", (req, res) => {
  const { title, description, startAt, endAt, allDay } = req.body;
  const now = Date.now();
  const result = db
    .prepare(
      `UPDATE calendar_events SET title=?, description=?, start_at=?, end_at=?, all_day=?, updated_at=?
       WHERE id=?`
    )
    .run(title, description || null, startAt, endAt || null, allDay ? 1 : 0, now, req.params.id);
  if (result.changes === 0) return res.status(404).json({ error: "not found" });
  res.json({ ok: true });
});

calendarRouter.delete("/events/:id", (req, res) => {
  db.prepare(`DELETE FROM calendar_events WHERE id=?`).run(req.params.id);
  res.json({ ok: true });
});
