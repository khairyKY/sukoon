# Accounts: confirming emails, resetting passwords, invite links

What the app and the web pages do, and the Supabase settings they need. Sign-in is email and
password only (no Google or Apple).

## The flows

**Creating an account (app).** Sign-up sends a PKCE challenge; the verifier stays on the phone
(`Supabase.signUp`). Sukoon shows *Check your inbox* with *Send it again* (a minute apart) and
*I've confirmed it*. The email's link goes through Supabase to `web/auth.html?code=…`, whose
*Open Sukoon* button hands the code to the app (`sukoon://auth?code=…`). The app trades it for a
session with its verifier, so tapping the link on the same phone signs in; on any other device the
page says to go back to the phone and tap *I've confirmed it*. Another app catching the link gets a
code it can't use without the verifier.

**Creating an account (follow page).** Same email; the link lands on `auth.html#access_token=…`,
and *Continue in the browser* keeps that session for the follow page.

**Forgot password.** *Forgot password?* (app sign-in, onboarding, follow page) emails a link to
`auth.html#…type=recovery`, where a new password is chosen on any device. Then sign in with it.

**Expired or used links** show *This link has run out* and say where to get a new one.

**Invite links.** An invite is shared as `https://khairyky.github.io/sukoon/join.html?c=CODE`
(also as a QR code on the invite sheet). On Android, *Open in Sukoon* opens the app with the code
(`sukoon://follow?c=CODE`): signed in, one tap follows; signed out, the code waits while you sign in
(onboarding's code step starts filled in). Without the app, or on an iPhone or computer, it opens
the follow page with the code, which follows by itself once signed in. Codes are typed in capitals
automatically; O, 0, I and 1 never appear in one, so they're ignored.

## Supabase settings (dashboard, once)

1. **Your own email provider first.** Supabase's built-in email only sends to members of your
   Supabase team, a few per hour. Anyone else would never get a confirmation or reset link.
   Authentication → Emails → SMTP Settings → *Enable custom SMTP*, with any provider's SMTP details
   (Resend, Brevo, Postmark… the free tiers are plenty). Sender name: Sukoon.
2. **Confirm email on.** Authentication → Sign In / Providers → Email → *Confirm email*. Accounts
   made before this are already confirmed and aren't affected.
3. **Where links may go.** Authentication → URL Configuration:
   - Site URL: `https://khairyky.github.io/sukoon/`
   - Redirect URLs: add `https://khairyky.github.io/sukoon/auth.html`
4. **Optional: the emails' words.** Authentication → Emails → Templates. The default templates work
   (they use `{{ .ConfirmationURL }}`); a line of Arabic under the English is a kind touch.

Nothing here needs the service_role key or the database password.

## New followers

Every 15 minutes the app checks who follows the account and notifies about anyone new: *Reema now
follows you*, with *Remove* on the notification. Under the parent lock (a PIN, under 18) there's no
Remove there: removing a follower stays behind the PIN in You → People. The first check on an
account only remembers who's there.
