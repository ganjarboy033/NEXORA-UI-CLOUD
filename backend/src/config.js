export function loadConfig(env = process.env) {
  const required = ['NEXORA_API_KEY', 'DATABASE_URL', 'TELEGRAM_BOT_TOKEN', 'TELEGRAM_STORAGE_CHAT_ID'];
  const missing = required.filter((key) => !env[key]);
  if (missing.length) throw new Error(`Konfigurasi server belum lengkap: ${missing.join(', ')}`);
  if (env.NEXORA_API_KEY.length < 32) throw new Error('NEXORA_API_KEY wajib minimal 32 karakter.');
  return {
    port: Number(env.PORT || 8080),
    apiKey: env.NEXORA_API_KEY,
    databaseUrl: env.DATABASE_URL,
    databaseSsl: env.DATABASE_SSL === 'true',
    telegramToken: env.TELEGRAM_BOT_TOKEN,
    telegramChatId: env.TELEGRAM_STORAGE_CHAT_ID,
    // Telegram Client API credentials are optional until account-login is enabled.
    telegramApiId: env.TELEGRAM_API_ID ? Number(env.TELEGRAM_API_ID) : null,
    telegramApiHash: env.TELEGRAM_API_HASH || '',
    telegramAccountAuthConfigured: Boolean(env.TELEGRAM_API_ID && env.TELEGRAM_API_HASH),
    telegramApiBase: (env.TELEGRAM_API_BASE || 'https://api.telegram.org').replace(/\/$/, ''),
    telegramFileBase: (env.TELEGRAM_FILE_BASE || 'https://api.telegram.org/file/bot').replace(/\/$/, ''),
    maxUploadBytes: Math.max(1, Number(env.MAX_UPLOAD_MB || 50)) * 1024 * 1024
  };
}
