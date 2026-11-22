# StandBy for Android

iPhone-style **StandBy** for Android phones (built for the Galaxy A53, works on Android 9+).
Turn your phone sideways while it charges and it becomes a bedside clock / smart display.

## Features
| iPhone StandBy | This app |
|---|---|
| Widgets view (two stacks) | 8 widgets: Analog, Digital, World clock, Calendar, Battery, Date & alarm, **Now Playing**, **Pomodoro**. You choose which go in each stack and in what order |
| Photos view | Recent gallery photos with slow zoom and cross-fade. Clock in any corner. Hidden while the phone is locked |
| Clock view | **13 faces**: Digital, Analog, Flip, Stacked, Float, Neon, Retro LED, Rings, Words, World, Solar, **Pomodoro**, **Now Playing** |
| Live Activities | **Spotify / any music**: Now Playing face, widget and mini-player with play/pause/skip |
| Wallpapers | Optional internet wallpapers (Bing wallpaper of the day or random photos) behind faces/widgets, with adjustable darkening |
| Night mode | Ambient light sensor: everything turns dim red in the dark |
| Always on | Keep screen on: Always / While charging / System. Own brightness slider |
| Charging | Charging animation when plugged in. Battery level badge |
| Starts when charging | Registers as an Android **Screen saver** |
| Customise | Long-press for colours, or **All settings** for everything else, incl. the order of views and faces |

## Install
1. Open the latest release on your phone and download **StandBy.apk**.
2. Tap it → allow "Install unknown apps" for your browser if asked → **Install**.
   (If Play Protect warns, tap *More details → Install anyway* — it's just an app not from the Play Store.)
3. Open StandBy once, tap **Allow photos**, then **Set up auto-start**.

## Auto-start while charging (Samsung)
Settings → Display → **Screen saver** → choose **StandBy** → set it to start *While charging*.
(On some One UI versions it's under Settings → Display → Screen saver, or search "Screen saver" in Settings.)

## Spotify / Now Playing
Settings → **Allow notification access** → turn on *StandBy Now Playing*.
If the switch is greyed out ("Restricted setting"): App info → ⋮ → **Allow restricted settings**, then try again. Android does this for apps installed outside the Play Store.

## Security
See [SECURITY.md](SECURITY.md) for the audit and fixes.

## Build
Every push to `main` builds a signed APK with GitHub Actions and publishes it as a release.
The signing key comes from the repo secrets `SIGNING_KEYSTORE_B64` and `SIGNING_PASSWORD`; it is never committed.
Locally: `gradle :app:assembleRelease` (Android SDK required).
