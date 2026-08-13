import { Router } from "express";
import { nanoid } from "nanoid";
import { db } from "../db.js";

export const deviceRouter = Router();

// 앱이 페어링 완료 후 자신을 등록 (FCM 토큰 갱신도 이 엔드포인트로)
deviceRouter.post("/devices/register", (req, res) => {
  const { deviceId, name, fcmToken } = req.body;
  const id = deviceId || nanoid();
  const now = Date.now();
  const existing = db.prepare(`SELECT id FROM devices WHERE id=?`).get(id);
  if (existing) {
    db.prepare(`UPDATE devices SET name=?, fcm_token=?, last_seen_at=? WHERE id=?`).run(
      name || null,
      fcmToken || null,
      now,
      id
    );
  } else {
    db.prepare(
      `INSERT INTO devices (id, name, fcm_token, paired_at, last_seen_at) VALUES (?, ?, ?, ?, ?)`
    ).run(id, name || null, fcmToken || null, now, now);
  }
  res.json({ id });
});
