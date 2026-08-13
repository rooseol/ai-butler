import { Router } from "express";
import multer from "multer";
import fs from "node:fs";
import path from "node:path";
import { nanoid } from "nanoid";
import { db } from "../db.js";

const storageDir = path.resolve(process.env.STORAGE_DIR || "./storage/files");
if (!fs.existsSync(storageDir)) fs.mkdirSync(storageDir, { recursive: true });

const upload = multer({ dest: storageDir });

export const filesRouter = Router();

// 폰 -> 서버 파일 업로드
filesRouter.post("/files", upload.single("file"), (req, res) => {
  const f = req.file;
  if (!f) return res.status(400).json({ error: "file required" });
  const id = nanoid();
  const finalPath = path.join(storageDir, `${id}__${f.originalname}`);
  fs.renameSync(f.path, finalPath);
  db.prepare(
    `INSERT INTO files (id, filename, mime, size, path, direction, created_at) VALUES (?, ?, ?, ?, ?, 'upload', ?)`
  ).run(id, f.originalname, f.mimetype, f.size, finalPath, Date.now());
  res.status(201).json({ id, filename: f.originalname, size: f.size });
});

filesRouter.get("/files", (_req, res) => {
  res.json(db.prepare(`SELECT id, filename, mime, size, direction, created_at FROM files ORDER BY created_at DESC`).all());
});

// 서버 -> 폰 파일 다운로드
filesRouter.get("/files/:id", (req, res) => {
  const row = db.prepare(`SELECT * FROM files WHERE id=?`).get(req.params.id) as any;
  if (!row) return res.status(404).json({ error: "not found" });
  res.download(row.path, row.filename);
});
