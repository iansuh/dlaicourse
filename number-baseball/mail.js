// Sends the password-reset code by email through an HTTP mail API (no SMTP library needed).
//
// Environment variables:
//   MAIL_PROVIDER  "resend" (default) or "brevo"
//   MAIL_API_KEY   API key from that provider
//   MAIL_FROM      sender address the provider has verified, e.g. "noreply@example.com"
//   SITE_NAME      name shown in the email (default "Number Baseball")
//   MAIL_API_URL   optional override of the provider endpoint (used by tests)
//
// Without MAIL_API_KEY nothing is sent: the code is printed to the server log instead,
// which is enough for local testing.
const PROVIDER = (process.env.MAIL_PROVIDER || 'resend').toLowerCase();
const KEY = process.env.MAIL_API_KEY || '';
const FROM = process.env.MAIL_FROM || '';
const SITE = process.env.SITE_NAME || 'Number Baseball';

const TEXT = {
  en: ['Password reset code', 'Your password reset code is {code}. It expires in 15 minutes. If you did not ask for it, you can ignore this email.'],
  ko: ['비밀번호 재설정 코드', '비밀번호 재설정 코드는 {code} 입니다. 15분 동안 유효합니다. 요청한 적이 없다면 이 메일을 무시하세요.'],
  ja: ['パスワード再設定コード', 'パスワード再設定コードは {code} です。有効期限は15分です。心当たりがない場合は、このメールを無視してください。'],
  zh: ['密码重设验证码', '你的密码重设验证码是 {code}，15分钟内有效。如果不是你本人操作，请忽略此邮件。'],
  es: ['Código para restablecer la contraseña', 'Tu código para restablecer la contraseña es {code}. Caduca en 15 minutos. Si no lo has pedido, ignora este correo.'],
  fr: ['Code de réinitialisation du mot de passe', 'Votre code de réinitialisation est {code}. Il expire dans 15 minutes. Si vous n’êtes pas à l’origine de cette demande, ignorez cet e-mail.'],
  de: ['Code zum Zurücksetzen des Passworts', 'Dein Code zum Zurücksetzen des Passworts lautet {code}. Er ist 15 Minuten gültig. Falls du ihn nicht angefordert hast, ignoriere diese E-Mail.'],
  pt: ['Código para redefinir a senha', 'Seu código para redefinir a senha é {code}. Ele expira em 15 minutos. Se você não pediu, ignore este e-mail.'],
  ru: ['Код для сброса пароля', 'Ваш код для сброса пароля: {code}. Он действует 15 минут. Если вы его не запрашивали, просто проигнорируйте это письмо.'],
  vi: ['Mã đặt lại mật khẩu', 'Mã đặt lại mật khẩu của bạn là {code}. Mã hết hạn sau 15 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.'],
};

const configured = () => !!(KEY && FROM);

async function sendResetCode(to, code, lang) {
  const [subject, body] = TEXT[lang] || TEXT.en;
  const mail = { subject: `[${SITE}] ${subject}`, text: body.replace('{code}', code) };
  if (!configured()) {
    console.log(`[mail not configured] reset code for ${to}: ${code}`);
    return true;
  }
  let url, headers, payload;
  if (PROVIDER === 'brevo') {
    url = process.env.MAIL_API_URL || 'https://api.brevo.com/v3/smtp/email';
    headers = { 'api-key': KEY };
    payload = { sender: { email: FROM, name: SITE }, to: [{ email: to }], subject: mail.subject, textContent: mail.text };
  } else {
    url = process.env.MAIL_API_URL || 'https://api.resend.com/emails';
    headers = { Authorization: 'Bearer ' + KEY };
    payload = { from: `${SITE} <${FROM}>`, to: [to], subject: mail.subject, text: mail.text };
  }
  try {
    const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(payload), signal: AbortSignal.timeout(10e3) });
    if (!res.ok) console.error('Mail provider refused the message:', res.status, (await res.text()).slice(0, 200));
    return res.ok;
  } catch (e) {
    console.error('Mail could not be sent:', e.message);
    return false;
  }
}

module.exports = { sendResetCode, configured };
