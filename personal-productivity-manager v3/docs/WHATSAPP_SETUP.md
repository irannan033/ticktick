# WhatsApp setup (needs your Meta account; I could not do or test this part)

Uses the official WhatsApp Cloud API. There is no unofficial scraping; sending from your personal number is not possible.

1. developers.facebook.com > create an app (Business) > add WhatsApp. Create/choose a WhatsApp Business Account.
2. Use a **dedicated sender number** (test number for development; a real number you register for production).
   Your personal number is the one that *receives* messages (set in the app under WhatsApp reminders).
3. Create a permanent access token (System User) and note: Phone number ID, App secret.
4. Fill the server `.env`: `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN`.
5. Webhook: Callback URL `https://your.domain/webhook/whatsapp`, Verify token = `WHATSAPP_VERIFY_TOKEN`; subscribe to `messages`.
6. Create and get these **message templates approved** (Category: Utility, language en). Body variables cannot contain line breaks:
   - `morning_tasks`: `Good morning {{1}}! Today's tasks: {{2}}. Reply "done 1" to complete, or "help".`
   - `task_followup`: `Hi {{1}}, still pending: {{2}}. Reply "done 1", "postpone 2 to tomorrow 5pm" or "help".`
   - `daily_report`: `Hi {{1}}, today's report: {{2}}. Reply "list" to see what's left.`
   Business-initiated messages MUST use approved templates; free-text replies only work within 24h of your last message.
7. In the app: Settings > Cloud sync (sign in), then Settings > WhatsApp reminders: number in international format, enable, save.

Commands you can send: `list`, `done 1,3`, `done <title>`, `postpone 2 to tomorrow 5pm`, `cancel 2` (asks YES/NO),
`add Buy milk tomorrow 6pm`, `help`. Ambiguous titles are never guessed: the bot lists matches and asks for a number.

Delivery failures: retried up to 3 times per scheduled message, then skipped (no spam). The app's own local
notifications keep working regardless, which is the fallback channel. Meta may charge per conversation; check current pricing.
