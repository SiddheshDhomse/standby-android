# Security audit — StandBy for Android (v1.1, updated for v1.2)

Scope: all app code, the manifest, build config and the GitHub Actions pipeline.

## Findings and fixes

| # | Severity | Finding (v1.0) | Fix (v1.1) |
|---|---|---|---|
| 1 | **High** | The release signing key **and its password were committed to this public repo**. Anyone could sign a modified APK that Android would accept as an *update* to the installed app. | Key rotated (new 3072-bit key). It now lives only in GitHub **Secrets** (`SIGNING_KEYSTORE_B64`, `SIGNING_PASSWORD`). It is written to a temp file during the build and deleted afterwards. The old key is no longer trusted (it's still in git history, so it must never be reused). |
| 2 | **Medium** | Photos were shown over the **lock screen** (activity `showWhenLocked`, and the screen saver). Anyone who picked up the locked phone could browse the gallery. | New setting **"Hide photos on the lock screen"** (on by default). While the phone is locked, photos are neither decoded nor shown. |
| 3 | Medium | New internet feature (wallpapers) could open the app to cleartext/MITM traffic, redirects to other hosts, or very large or malicious images. | HTTPS only: cleartext is blocked app-wide and only system CAs are trusted (`network_security_config`). There is a fixed **host allow-list**, re-checked after redirects. The Bing JSON may only supply a site-relative path. Limits: 15 MB per image, 256 KB per JSON, 10 s/20 s timeouts, `image/*` content type required, and image dimensions checked (≤ 40 MP) before decoding. Downsampled decoding is used. The cache is in app-private storage and written atomically. |
| 4 | Medium | A "Now Playing" feature requires **Notification access**, a powerful permission. | The listener service ignores every notification (no reading, storing or sending). It is used only to unlock the MediaSession API. It is protected by `BIND_NOTIFICATION_LISTENER_SERVICE`, so only the system can bind to it. Access is opt-in from Settings. |
| 5 | Low | `allowBackup="true"`: app data could be copied via backup/device transfer. | `allowBackup="false"` plus `dataExtractionRules` that exclude everything. |
| 6 | Low | CI used mutable action tags (`@v4`) with a broad token. | All actions are pinned to commit SHAs. The workflow defaults to `contents: read`, with write access only on the build job (for the release). `persist-credentials: false`, and concurrency control is set. |
| 7 | Info | Exported components | Launcher activity (required). `SettingsActivity` (required by the system Screen-saver page, reads no intent data). Dream service (protected by `BIND_DREAM_SERVICE`). Listener service (protected by `BIND_NOTIFICATION_LISTENER_SERVICE`). Broadcast receivers are registered at runtime with `RECEIVER_NOT_EXPORTED`. |
| 8 | Info | No WebViews, no dynamic code, no `PendingIntent`s, no file sharing, no logging of user data, no analytics, no ads. | — |

## v1.2 additions

| Area | Risk | Mitigation |
|---|---|---|
| Calendar / Teams (`READ_CALENDAR`) | Work meeting titles visible on a locked phone; malicious links in event text | Read-only; nothing stored or sent. **Hide details when locked** is on by default. The Join button only opens `https://teams.microsoft.com/l/meetup-join/…` or `https://teams.live.com/meet/…`, and asks you to unlock first. |
| To-do | Personal tasks on a locked phone | Stored only in app-private storage (backups off); only a count is shown while locked. Input is length-limited. |
| Weather (Open-Meteo) | Location privacy, network | No location permission. Only the chosen city's coordinates, rounded to about 1 km, are sent over HTTPS to an allow-listed host. Responses are size-limited and parsed as JSON. |
| Pomodoro alarm | Hijackable broadcasts or PendingIntents | Receiver is not exported. All PendingIntents are explicit and `FLAG_IMMUTABLE`. `USE_EXACT_ALARM` is used because StandBy is a clock app. |
| Quick Settings tiles | Other apps toggling settings | Protected by `BIND_QUICK_SETTINGS_TILE`, so only the system can bind. |
| Settings change listener | — | Only re-reads the app's own private preferences. |

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` | Photos view (Android 14 "selected photos" access supported) |
| `INTERNET` | Weather (Open-Meteo) and optional wallpapers (Bing / picsum.photos) |
| `VIBRATE`, `POST_NOTIFICATIONS`, `USE_EXACT_ALARM` | Pomodoro alerts, even when StandBy is closed |
| `READ_CALENDAR` | Up Next / Teams meetings (optional) |
| Notification access (user-granted) | Now Playing / Spotify (optional) |

## Reporting
Open an issue in this repo.
