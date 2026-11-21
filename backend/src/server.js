import express from 'express';
import multer from 'multer';
import pg from 'pg';
import { createHash, createHmac, randomBytes, randomUUID, timingSafeEqual } from 'node:crypto';
import { loadConfig } from './config.js';

const config = loadConfig();
const { Pool } = pg;
const pool = new Pool({ connectionString: config.databaseUrl, ssl: config.databaseSsl ? { rejectUnauthorized: true } : false, max: 10 });
const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '64kb' }));
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: config.maxUploadBytes, files: 1 } });

async function auth(req, res, next) {
  const supplied = (req.get('authorization') || '').replace(/^Bearer\s+/i, '');
  const a = Buffer.from(supplied); const b = Buffer.from(config.apiKey);
  if (a.length === b.length && timingSafeEqual(a, b)) return next();
  if (supplied.length >= 32) {
    try {
      const hash = createHash('sha256').update(supplied).digest('hex');
      const found = await pool.query('UPDATE nexora_devices SET last_seen_at=NOW() WHERE token_hash=$1 AND revoked_at IS NULL RETURNING id', [hash]);
      if (found.rowCount) { req.deviceId = found.rows[0].id; return next(); }
    } catch (error) { console.error('Validasi sesi perangkat gagal:', error.message); }
  }
  return res.status(401).json({ error: 'UNAUTHORIZED', message: 'Sesi atau kunci API tidak valid.' });
}
function pairingToken(pairId, pairSecret) {
  return createHmac('sha256', config.apiKey).update(`nexora-device:${pairId}:${pairSecret}`).digest('hex');
}

async function logActivity(action, fileId = null, fileName = null, details = {}) {
  try { await pool.query('INSERT INTO activity_logs (action, file_id, file_name, details) VALUES ($1,$2,$3,$4)', [action, fileId, fileName, JSON.stringify(details)]); }
  catch (error) { console.error('Pencatatan aktivitas gagal:', error.message); }
}
function safeName(name = 'file') { return String(name).replace(/[\\/\0-\x1f]/g, '_').slice(0, 240) || 'file'; }
async function telegram(method, options = {}) {
  const response = await fetch(`${config.telegramApiBase}/bot${config.telegramToken}/${method}`, options);
  const data = await response.json().catch(() => ({}));
  if (!response.ok || !data.ok) throw new Error(data.description || `Telegram API gagal (${response.status})`);
  return data.result;
}

app.get('/health', async (_req, res) => {
  try { await pool.query('SELECT 1'); res.json({ ok: true, service: 'nexora-cloud-backend', version: '3.4.0-alpha', database: 'connected' }); }
  catch { res.status(503).json({ ok: false, service: 'nexora-cloud-backend', database: 'unavailable' }); }
});

// Pairing perangkat: QR hanya berisi ID + rahasia sementara; sesi final baru
// diterbitkan setelah perangkat tepercaya menyetujui melalui endpoint terlindungi.
const pairingAttempts = new Map();
app.post('/api/v1/pairing/start', async (req, res) => {
  try {
    const now = Date.now();
    const ip = req.ip || 'unknown';
    const attempts = (pairingAttempts.get(ip) || []).filter((time) => now - time < 60_000);
    if (attempts.length >= 5) return res.status(429).json({ error: 'RATE_LIMIT', message: 'Terlalu banyak permintaan pairing. Coba lagi sebentar.' });
    attempts.push(now); pairingAttempts.set(ip, attempts);
    const pairId = randomUUID();
    const pairSecret = randomBytes(32).toString('hex');
    const expiresAt = new Date(now + 5 * 60_000);
    await pool.query(`INSERT INTO nexora_pairings (id, secret_hash, device_name, expires_at)
      VALUES ($1,$2,$3,$4)`, [pairId, createHash('sha256').update(pairSecret).digest('hex'), String(req.body?.device_name || 'Perangkat Android').slice(0, 80), expiresAt]);
    res.status(201).json({ pair_id: pairId, pair_secret: pairSecret, expires_at: expiresAt.toISOString(),
      qr_payload: `nexora://pair?id=${pairId}&secret=${pairSecret}` });
  } catch (error) { console.error('Pairing start gagal:', error.message); res.status(500).json({ error: 'PAIRING_START_FAILED', message: 'Sesi pairing tidak dapat dibuat.' }); }
});

app.post('/api/v1/pairing/:id/approve', auth, async (req, res) => {
  try {
    const secret = String(req.body?.pair_secret || '');
    if (!/^[a-f0-9]{64}$/.test(secret)) return res.status(400).json({ error: 'INVALID_PAIR_SECRET', message: 'Rahasia pairing tidak valid.' });
    const secretHash = createHash('sha256').update(secret).digest('hex');
    const result = await pool.query(`SELECT id, device_name, expires_at, approved_at, consumed_at
      FROM nexora_pairings WHERE id=$1 AND secret_hash=$2`, [req.params.id, secretHash]);
    if (!result.rowCount) return res.status(404).json({ error: 'PAIRING_NOT_FOUND', message: 'Kode pairing tidak ditemukan.' });
    const pair = result.rows[0];
    if (new Date(pair.expires_at) <= new Date()) return res.status(410).json({ error: 'PAIRING_EXPIRED', message: 'Kode pairing sudah kedaluwarsa.' });
    if (pair.consumed_at) return res.status(409).json({ error: 'PAIRING_CONSUMED', message: 'Kode pairing sudah digunakan.' });
    if (pair.approved_at) return res.json({ ok: true, status: 'already_approved' });
    const token = pairingToken(pair.id, secret);
    const tokenHash = createHash('sha256').update(token).digest('hex');
    const db = await pool.connect();
    try {
      await db.query('BEGIN');
      await db.query('INSERT INTO nexora_devices (device_name, token_hash) VALUES ($1,$2)', [pair.device_name, tokenHash]);
      const approved = await db.query('UPDATE nexora_pairings SET approved_at=NOW() WHERE id=$1 AND approved_at IS NULL AND consumed_at IS NULL RETURNING id', [pair.id]);
      if (!approved.rowCount) throw new Error('Pairing sudah diproses perangkat lain.');
      await db.query('COMMIT');
    } catch (error) { await db.query('ROLLBACK'); throw error; }
    finally { db.release(); }
    res.json({ ok: true, status: 'approved', device_name: pair.device_name });
  } catch (error) { console.error('Persetujuan pairing gagal:', error.message); res.status(500).json({ error: 'PAIRING_APPROVE_FAILED', message: 'Pairing tidak dapat disetujui.' }); }
});

app.post('/api/v1/pairing/:id/poll', async (req, res) => {
  try {
    const secret = String(req.body?.pair_secret || '');
    if (!/^[a-f0-9]{64}$/.test(secret)) return res.status(400).json({ error: 'INVALID_PAIR_SECRET', message: 'Rahasia pairing tidak valid.' });
    const secretHash = createHash('sha256').update(secret).digest('hex');
    const result = await pool.query(`SELECT id, expires_at, approved_at, consumed_at FROM nexora_pairings
      WHERE id=$1 AND secret_hash=$2`, [req.params.id, secretHash]);
    if (!result.rowCount) return res.status(404).json({ error: 'PAIRING_NOT_FOUND', message: 'Sesi pairing tidak ditemukan.' });
    const pair = result.rows[0];
    if (new Date(pair.expires_at) <= new Date()) return res.status(410).json({ error: 'PAIRING_EXPIRED', message: 'Kode pairing sudah kedaluwarsa.' });
    if (pair.consumed_at) return res.status(410).json({ error: 'PAIRING_CONSUMED', message: 'Sesi pairing sudah diambil.' });
    if (!pair.approved_at) return res.json({ status: 'pending' });
    const token = pairingToken(pair.id, secret);
    const tokenHash = createHash('sha256').update(token).digest('hex');
    const device = await pool.query('SELECT id FROM nexora_devices WHERE token_hash=$1 AND revoked_at IS NULL', [tokenHash]);
    if (!device.rowCount) return res.status(410).json({ error: 'DEVICE_REVOKED', message: 'Perangkat sudah tidak aktif.' });
    const consumed = await pool.query('UPDATE nexora_pairings SET consumed_at=NOW() WHERE id=$1 AND consumed_at IS NULL RETURNING id', [pair.id]);
    if (!consumed.rowCount) return res.status(410).json({ error: 'PAIRING_CONSUMED', message: 'Sesi pairing sudah diambil.' });
    res.json({ status: 'approved', device_token: token, device_id: device.rows[0].id });
  } catch (error) { console.error('Pairing poll gagal:', error.message); res.status(500).json({ error: 'PAIRING_POLL_FAILED', message: 'Status pairing tidak dapat diperiksa.' }); }
});

app.get('/api/v1/devices', auth, async (_req, res) => {
  try {
    const result = await pool.query(`SELECT id, device_name, created_at, last_seen_at FROM nexora_devices
      WHERE revoked_at IS NULL ORDER BY created_at DESC`);
    res.json({ devices: result.rows });
  } catch (error) { res.status(500).json({ error: 'DEVICE_LIST_FAILED', message: 'Daftar perangkat tidak tersedia.' }); }
});

app.delete('/api/v1/devices/:id', auth, async (req, res) => {
  try {
    const result = await pool.query(`UPDATE nexora_devices SET revoked_at=NOW()
      WHERE id=$1 AND revoked_at IS NULL RETURNING id`, [req.params.id]);
    if (!result.rowCount) return res.status(404).json({ error: 'DEVICE_NOT_FOUND', message: 'Perangkat aktif tidak ditemukan.' });
    res.json({ ok: true, revoked_device_id: result.rows[0].id });
  } catch (error) { res.status(500).json({ error: 'DEVICE_REVOKE_FAILED', message: 'Perangkat tidak dapat dicabut.' }); }
});

// Status login akun Telegram. Bot API dan Telegram Client API adalah dua hal berbeda.
// Endpoint ini tidak mengirim OTP atau membuat sesi; hanya melaporkan kesiapan konfigurasi.
app.get('/api/v1/telegram/account-auth/status', auth, async (_req, res) => {
  const configured = config.telegramAccountAuthConfigured;
  res.json({
    provider: 'telegram',
    mode: 'telegram-client-api',
    configured,
    methods: configured ? ['phone_code', 'qr'] : [],
    ready: false,
    message: configured
      ? 'Kredensial Telegram Client API tersedia. Mesin autentikasi MTProto belum diaktifkan pada rilis ini.'
      : 'Login akun Telegram memerlukan TELEGRAM_API_ID dan TELEGRAM_API_HASH dari my.telegram.org. Bot token saja tidak dapat digunakan untuk login akun pribadi.'
  });
});

app.get('/api/v1/telegram/status', auth, async (_req, res) => {
  try {
    const me = await telegram('getMe');
    res.json({ connected: true, provider: 'telegram', bot_username: me.username || null, storage_chat_configured: Boolean(config.telegramChatId) });
  } catch (error) { res.status(502).json({ connected: false, error: 'TELEGRAM_UNAVAILABLE', message: error.message }); }
});

app.post('/api/v1/files', auth, upload.single('file'), async (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'FILE_REQUIRED', message: 'Kirim satu file pada field multipart bernama file.' });
  try {
    const hash = createHash('sha256').update(req.file.buffer).digest('hex');
    // Arsip Telegram memakai penamaan waktu WIB agar mudah ditelusuri di chat/channel.
    const uploadedAt = new Date();
    const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Jakarta', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', hourCycle: 'h23' }).formatToParts(uploadedAt);
    const part = (type) => parts.find((item) => item.type === type)?.value || '00';
    const archivePath = `${part('year')}/${part('month')}/${part('day')}/${part('hour')}`;
    const archiveCaption = `NEXORA CLOUD\nFolder: ${archivePath} WIB\nFile: ${safeName(req.file.originalname)}\nSHA256: ${hash}`;
    const form = new FormData();
    form.append('chat_id', config.telegramChatId);
    form.append('document', new Blob([req.file.buffer], { type: req.file.mimetype || 'application/octet-stream' }), safeName(req.file.originalname));
    form.append('caption', archiveCaption);
    const message = await telegram('sendDocument', { method: 'POST', body: form });
    const doc = message.document;
    if (!doc?.file_id) throw new Error('Telegram tidak mengembalikan file_id.');
    const result = await pool.query(
      `INSERT INTO cloud_files (original_name,mime_type,byte_size,sha256,telegram_chat_id,telegram_message_id,telegram_file_id,telegram_file_unique_id,created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id,original_name,mime_type,byte_size,sha256,created_at`,
      [safeName(req.file.originalname), req.file.mimetype || 'application/octet-stream', req.file.size, hash, String(message.chat.id), message.message_id, doc.file_id, doc.file_unique_id || null, uploadedAt]
    );
    await logActivity('upload', result.rows[0].id, result.rows[0].original_name, { byte_size: req.file.size, mime_type: req.file.mimetype || 'application/octet-stream' });
    res.status(201).json({ file: result.rows[0], provider: 'telegram' });
  } catch (error) {
    console.error('Upload gagal:', error.message);
    res.status(502).json({ error: 'UPLOAD_FAILED', message: 'Upload ke Telegram atau pencatatan metadata gagal.' });
  }
});

app.get('/api/v1/stats', auth, async (_req, res) => {
  try {
    const result = await pool.query(
      `SELECT COUNT(*)::bigint AS total_files,
              COALESCE(SUM(byte_size),0)::bigint AS total_bytes,
              COUNT(*) FILTER (WHERE mime_type ILIKE 'image/%')::bigint AS images,
              COUNT(*) FILTER (WHERE mime_type ILIKE 'video/%')::bigint AS videos,
              COUNT(*) FILTER (WHERE mime_type ILIKE 'audio/%')::bigint AS audios,
              COUNT(*) FILTER (WHERE mime_type ILIKE 'application/%' OR mime_type ILIKE 'text/%')::bigint AS documents,
              MAX(created_at) AS latest_upload
       FROM cloud_files WHERE deleted_at IS NULL`
    );
    const row = result.rows[0] || {};
    res.json({
      total_files: Number(row.total_files || 0), total_bytes: Number(row.total_bytes || 0),
      images: Number(row.images || 0), videos: Number(row.videos || 0),
      audios: Number(row.audios || 0), documents: Number(row.documents || 0),
      latest_upload: row.latest_upload || null
    });
  } catch (error) {
    console.error('Statistik cloud gagal:', error.message);
    res.status(500).json({ error: 'STATS_ERROR', message: 'Statistik cloud tidak dapat dibaca.' });
  }
});

app.get('/api/v1/files', auth, async (req, res) => {
  try {
    // Pagination berbasis offset; batas page kecil menjaga memori server dan perangkat.
    const limit = Math.min(100, Math.max(1, Number(req.query.limit || 50)));
    const offset = Math.min(1000000, Math.max(0, Number(req.query.offset || 0)));
    const search = String(req.query.q || '').trim().slice(0, 160);
    const type = String(req.query.type || 'all').toLowerCase();
    const sort = String(req.query.sort || 'date').toLowerCase();
    const direction = String(req.query.direction || 'desc').toLowerCase() === 'asc' ? 'ASC' : 'DESC';
    const sortColumn = ({ date: 'created_at', name: 'original_name', size: 'byte_size' })[sort] || 'created_at';
    const typeConditions = {
      image: "mime_type ILIKE 'image/%'",
      video: "mime_type ILIKE 'video/%'",
      audio: "mime_type ILIKE 'audio/%'",
      document: "(mime_type ILIKE 'text/%' OR mime_type IN ('application/pdf','application/msword','application/vnd.openxmlformats-officedocument.wordprocessingml.document','application/vnd.ms-excel','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'))",
      archive: "(mime_type ILIKE '%zip%' OR mime_type ILIKE '%compressed%' OR original_name ~* '\\.(zip|rar|7z|tar|gz|bz2|xz)$')"
    };
    const typeSql = typeConditions[type] ? ` AND ${typeConditions[type]}` : '';
    const count = await pool.query(
      `SELECT COUNT(*)::bigint AS total, COALESCE(SUM(byte_size),0)::bigint AS total_bytes
       FROM cloud_files WHERE deleted_at IS NULL AND ($1 = '' OR original_name ILIKE $2)${typeSql}`,
      [search, `%${search}%`]
    );
    const result = await pool.query(
      `SELECT id,original_name,mime_type,byte_size,sha256,provider,created_at,
        to_char(created_at AT TIME ZONE 'Asia/Jakarta','YYYY/MM/DD/HH24') AS archive_path
       FROM cloud_files WHERE deleted_at IS NULL AND ($1 = '' OR original_name ILIKE $2)${typeSql}
       ORDER BY ${sortColumn} ${direction}, id DESC LIMIT $3 OFFSET $4`,
      [search, `%${search}%`, limit, offset]
    );
    const total = Number(count.rows[0]?.total || 0);
    res.json({ files: result.rows, pagination: { limit, offset, total, total_bytes: Number(count.rows[0]?.total_bytes || 0), has_more: offset + result.rows.length < total, next_offset: offset + result.rows.length } });
  } catch (error) {
    console.error('Daftar file gagal:', error.message);
    res.status(500).json({ error: 'DATABASE_ERROR', message: 'Metadata file tidak dapat dibaca.' });
  }
});

// Ringkasan ukuran dan jumlah file untuk setiap folder virtual di bawah prefix.
app.get('/api/v1/archive/folders', auth, async (req, res) => {
  try {
    const prefix = String(req.query.prefix || '').replace(/^\/+|\/+$/g, '').slice(0, 80);
    const depth = prefix ? prefix.split('/').length : 0;
    if (depth >= 4) return res.json({ folders: [] });
    const result = await pool.query(
      `SELECT split_part(archive_path, '/', $2) AS folder_name,
              COUNT(*)::bigint AS file_count, COALESCE(SUM(byte_size),0)::bigint AS total_bytes
       FROM (
         SELECT to_char(created_at AT TIME ZONE 'Asia/Jakarta','YYYY/MM/DD/HH24') AS archive_path, byte_size
         FROM cloud_files
         WHERE deleted_at IS NULL AND ($1 = '' OR to_char(created_at AT TIME ZONE 'Asia/Jakarta','YYYY/MM/DD/HH24') LIKE $1 || '/%')
       ) archived
       WHERE split_part(archive_path, '/', $2) <> ''
       GROUP BY folder_name ORDER BY folder_name DESC`,
      [prefix, depth + 1]
    );
    res.json({ prefix, folders: result.rows });
  } catch (error) {
    console.error('Ringkasan folder gagal:', error.message);
    res.status(500).json({ error: 'ARCHIVE_ERROR', message: 'Ringkasan folder tidak dapat dibaca.' });
  }
});

// Penghapusan aman: hanya menyembunyikan metadata dari katalog aktif.
// File fisik di Telegram tidak disentuh sehingga tidak ada penghapusan permanen yang tidak disengaja.
app.delete('/api/v1/files/:id', auth, async (req, res) => {
  try {
    const result = await pool.query(
      `UPDATE cloud_files SET deleted_at = NOW() WHERE id = $1 AND deleted_at IS NULL RETURNING id, original_name, deleted_at`,
      [req.params.id]
    );
    if (!result.rowCount) return res.status(404).json({ error: 'NOT_FOUND', message: 'File aktif tidak ditemukan.' });
    await logActivity('delete', result.rows[0].id, result.rows[0].original_name);
    res.json({ ok: true, action: 'soft_deleted', file: result.rows[0], note: 'Metadata dipindahkan ke arsip terhapus. File fisik Telegram tetap tersimpan.' });
  } catch (error) {
    console.error('Pengarsipan file gagal:', error.message);
    res.status(500).json({ error: 'DELETE_ERROR', message: 'File tidak dapat dipindahkan ke arsip terhapus.' });
  }
});

app.post('/api/v1/files/:id/restore', auth, async (req, res) => {
  try {
    const result = await pool.query(
      `UPDATE cloud_files SET deleted_at = NULL WHERE id = $1 AND deleted_at IS NOT NULL RETURNING id, original_name`,
      [req.params.id]
    );
    if (!result.rowCount) return res.status(404).json({ error: 'NOT_FOUND', message: 'File terhapus tidak ditemukan.' });
    await logActivity('restore', result.rows[0].id, result.rows[0].original_name);
    res.json({ ok: true, action: 'restored', file: result.rows[0] });
  } catch (error) {
    console.error('Pemulihan file gagal:', error.message);
    res.status(500).json({ error: 'RESTORE_ERROR', message: 'File tidak dapat dipulihkan.' });
  }
});

app.get('/api/v1/trash', auth, async (_req, res) => {
  try {
    const result = await pool.query(
      `SELECT id, original_name, mime_type, byte_size, sha256, deleted_at
       FROM cloud_files WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC LIMIT 200`
    );
    res.json({ files: result.rows, limit: 200 });
  } catch (error) {
    console.error('Arsip terhapus gagal dibaca:', error.message);
    res.status(500).json({ error: 'TRASH_ERROR', message: 'Arsip terhapus tidak dapat dibaca.' });
  }
});

app.get('/api/v1/activity', auth, async (req, res) => {
  try {
    const limit = Math.min(200, Math.max(1, Number(req.query.limit || 50)));
    const allowedActions = new Set(['upload', 'delete', 'restore', 'download_requested']);
    const requestedAction = String(req.query.action || 'all').toLowerCase();
    const action = allowedActions.has(requestedAction) ? requestedAction : 'all';
    const since = req.query.since ? new Date(String(req.query.since)) : null;
    const until = req.query.until ? new Date(String(req.query.until)) : null;
    if ((since && Number.isNaN(since.getTime())) || (until && Number.isNaN(until.getTime())))
      return res.status(400).json({ error: 'INVALID_DATE', message: 'Gunakan tanggal ISO-8601 yang valid.' });
    const result = await pool.query(
      `SELECT id, action, file_id, file_name, details, created_at
       FROM activity_logs
       WHERE ($1 = 'all' OR action = $1)
         AND ($2::timestamptz IS NULL OR created_at >= $2)
         AND ($3::timestamptz IS NULL OR created_at < $3)
       ORDER BY created_at DESC, id DESC LIMIT $4`,
      [action, since, until, limit]
    );
    res.json({ activities: result.rows, limit, action, since: since?.toISOString() || null, until: until?.toISOString() || null });
  } catch (error) {
    console.error('Riwayat aktivitas gagal dibaca:', error.message);
    res.status(500).json({ error: 'ACTIVITY_ERROR', message: 'Riwayat aktivitas tidak dapat dibaca.' });
  }
});

// Ekspor CSV dibatasi 5.000 baris dan memakai escaping RFC 4180 sederhana.
app.get('/api/v1/activity/export.csv', auth, async (req, res) => {
  try {
    const allowedActions = new Set(['upload', 'delete', 'restore', 'download_requested']);
    const requestedAction = String(req.query.action || 'all').toLowerCase();
    const action = allowedActions.has(requestedAction) ? requestedAction : 'all';
    const since = req.query.since ? new Date(String(req.query.since)) : null;
    const until = req.query.until ? new Date(String(req.query.until)) : null;
    if ((since && Number.isNaN(since.getTime())) || (until && Number.isNaN(until.getTime())))
      return res.status(400).json({ error: 'INVALID_DATE', message: 'Gunakan tanggal ISO-8601 yang valid.' });
    const result = await pool.query(
      `SELECT id, action, file_id, file_name, details, created_at FROM activity_logs
       WHERE ($1 = 'all' OR action = $1)
         AND ($2::timestamptz IS NULL OR created_at >= $2)
         AND ($3::timestamptz IS NULL OR created_at < $3)
       ORDER BY created_at DESC, id DESC LIMIT 5000`, [action, since, until]
    );
    const cell = (value) => '"' + String(value ?? '').replace(/"/g, '""') + '"';
    const lines = [['id','action','file_id','file_name','details','created_at'], ...result.rows.map(r => [r.id,r.action,r.file_id,r.file_name,JSON.stringify(r.details || {}),r.created_at])];
    const csv = '\uFEFF' + lines.map(row => row.map(cell).join(',')).join('\r\n');
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', 'attachment; filename="nexora-activity.csv"');
    res.send(csv);
  } catch (error) {
    console.error('Ekspor aktivitas gagal:', error.message);
    res.status(500).json({ error: 'ACTIVITY_EXPORT_ERROR', message: 'Ekspor aktivitas gagal.' });
  }
});

// Rekap kapasitas per bulan/tahun berdasarkan timestamp unggah (zona WIB).
app.get('/api/v1/stats/storage', auth, async (_req, res) => {
  try {
    const [monthly, yearly] = await Promise.all([
      pool.query(`SELECT to_char(created_at AT TIME ZONE 'Asia/Jakarta','YYYY-MM') AS period,
                         COUNT(*)::bigint AS total_files, COALESCE(SUM(byte_size),0)::bigint AS total_bytes
                  FROM cloud_files WHERE deleted_at IS NULL
                  GROUP BY period ORDER BY period DESC LIMIT 24`),
      pool.query(`SELECT to_char(created_at AT TIME ZONE 'Asia/Jakarta','YYYY') AS period,
                         COUNT(*)::bigint AS total_files, COALESCE(SUM(byte_size),0)::bigint AS total_bytes
                  FROM cloud_files WHERE deleted_at IS NULL
                  GROUP BY period ORDER BY period DESC LIMIT 10`)
    ]);
    const normalize = rows => rows.map(r => ({ period: r.period, total_files: Number(r.total_files), total_bytes: Number(r.total_bytes) }));
    res.json({ timezone: 'Asia/Jakarta', monthly: normalize(monthly.rows), yearly: normalize(yearly.rows) });
  } catch (error) {
    console.error('Statistik kapasitas gagal:', error.message);
    res.status(500).json({ error: 'STORAGE_STATS_ERROR', message: 'Statistik kapasitas tidak dapat dibaca.' });
  }
});

app.get('/api/v1/files/:id/download', auth, async (req, res) => {
  try {
    const found = await pool.query('SELECT * FROM cloud_files WHERE id=$1 AND deleted_at IS NULL', [req.params.id]);
    if (!found.rowCount) return res.status(404).json({ error: 'NOT_FOUND', message: 'File tidak ditemukan.' });
    const item = found.rows[0];
    await logActivity('download_requested', item.id, item.original_name, { byte_size: item.byte_size });
    const info = await telegram('getFile', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ file_id: item.telegram_file_id }) });
    const fileUrl = `${config.telegramFileBase}${config.telegramToken}/${info.file_path}`;
    const upstream = await fetch(fileUrl);
    if (!upstream.ok || !upstream.body) throw new Error('File tidak dapat diambil dari Telegram.');
    res.setHeader('Content-Type', item.mime_type || 'application/octet-stream');
    res.setHeader('Content-Length', String(item.byte_size));
    res.setHeader('Content-Disposition', `attachment; filename*=UTF-8''${encodeURIComponent(item.original_name)}`);
    const { Readable } = await import('node:stream');
    Readable.fromWeb(upstream.body).pipe(res);
  } catch (error) {
    console.error('Download gagal:', error.message);
    if (!res.headersSent) res.status(502).json({ error: 'DOWNLOAD_FAILED', message: 'File gagal diambil dari Telegram.' });
  }
});

app.use((error, _req, res, _next) => {
  if (error instanceof multer.MulterError && error.code === 'LIMIT_FILE_SIZE') return res.status(413).json({ error: 'FILE_TOO_LARGE', message: 'Ukuran file melewati batas konfigurasi server.' });
  console.error('Kesalahan server:', error.message);
  res.status(500).json({ error: 'INTERNAL_ERROR', message: 'Terjadi kesalahan server.' });
});

// Migrasi aditif yang aman untuk instalasi versi sebelumnya.
await pool.query('ALTER TABLE cloud_files ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ');
await pool.query(`CREATE TABLE IF NOT EXISTS activity_logs (id BIGSERIAL PRIMARY KEY, action TEXT NOT NULL, file_id BIGINT, file_name TEXT, details JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW())`);
await pool.query('CREATE INDEX IF NOT EXISTS idx_activity_logs_created ON activity_logs(created_at DESC)');
await pool.query(`CREATE TABLE IF NOT EXISTS nexora_devices (id BIGSERIAL PRIMARY KEY, device_name TEXT NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), revoked_at TIMESTAMPTZ)`);
await pool.query(`CREATE TABLE IF NOT EXISTS nexora_pairings (id UUID PRIMARY KEY, secret_hash CHAR(64) NOT NULL, device_name TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), expires_at TIMESTAMPTZ NOT NULL, approved_at TIMESTAMPTZ, consumed_at TIMESTAMPTZ)`);
await pool.query('CREATE INDEX IF NOT EXISTS idx_nexora_devices_active ON nexora_devices(created_at DESC) WHERE revoked_at IS NULL');
await pool.query('CREATE INDEX IF NOT EXISTS idx_nexora_pairings_expiry ON nexora_pairings(expires_at)');
const server = app.listen(config.port, () => console.log(`Nexora backend aktif pada port ${config.port}`));
async function shutdown() { server.close(); await pool.end(); process.exit(0); }
process.on('SIGTERM', shutdown); process.on('SIGINT', shutdown);
