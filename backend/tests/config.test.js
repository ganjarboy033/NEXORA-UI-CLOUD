import test from 'node:test';
import assert from 'node:assert/strict';
import { loadConfig } from '../src/config.js';

test('konfigurasi menolak API key pendek', () => {
  assert.throws(() => loadConfig({ NEXORA_API_KEY: 'pendek', DATABASE_URL: 'postgres://x', TELEGRAM_BOT_TOKEN: 'x', TELEGRAM_STORAGE_CHAT_ID: '1' }), /minimal 32 karakter/);
});

test('konfigurasi menerima rahasia yang lengkap', () => {
  const config = loadConfig({ NEXORA_API_KEY: 'x'.repeat(40), DATABASE_URL: 'postgres://x', TELEGRAM_BOT_TOKEN: 'bot-token', TELEGRAM_STORAGE_CHAT_ID: '-1001' });
  assert.equal(config.port, 8080);
  assert.equal(config.telegramChatId, '-1001');
});

test('Telegram Client API bersifat opsional dan hanya aktif jika kedua kredensial tersedia', () => {
  const base = { NEXORA_API_KEY: 'x'.repeat(40), DATABASE_URL: 'postgres://x', TELEGRAM_BOT_TOKEN: 'bot-token', TELEGRAM_STORAGE_CHAT_ID: '-1001' };
  assert.equal(loadConfig(base).telegramAccountAuthConfigured, false);
  const configured = loadConfig({ ...base, TELEGRAM_API_ID: '12345', TELEGRAM_API_HASH: 'hash-rahasia' });
  assert.equal(configured.telegramApiId, 12345);
  assert.equal(configured.telegramAccountAuthConfigured, true);
});
