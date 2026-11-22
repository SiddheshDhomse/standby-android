# StandBy for Android

iPhone-style **StandBy** for Android phones (built for the Galaxy A53, works on Android 9+).
Turn your phone sideways while it charges and it becomes a bedside clock / smart display.

## Features
| iPhone StandBy | This app |
|---|---|
| Widgets view (two stacks) | Left stack: Analog · Digital · World clock. Right stack: Calendar · Battery · Date & next alarm. Swipe each up/down independently |
| Photos view | Your recent gallery photos with slow zoom + cross-fade, time in the corner. Swipe up/down for the next photo |
| Clock view | 5 faces: Digital, Analog, World, Solar (sky follows the time of day), Float. Swipe up/down to change |
| Customise | Long-press → pick a colour for each clock face / the widgets, toggle night mode |
| Night mode | Ambient light sensor: in a dark room everything turns dim red |
| Starts when charging | Registers as an Android **Screen saver** – Android launches it automatically while charging |
| Always on | Screen stays on while charging; shows over the lock screen; subtle pixel shift against burn-in |

## Install
1. Open the latest release on your phone and download **StandBy.apk**.
2. Tap it → allow "Install unknown apps" for your browser if asked → **Install**.
   (If Play Protect warns, tap *More details → Install anyway* — it's just an app not from the Play Store.)
3. Open StandBy once, tap **Allow photos**, then **Set up auto-start**.

## Auto-start while charging (Samsung)
Settings → Display → **Screen saver** → choose **StandBy** → set it to start *While charging*.
(On some One UI versions it's under Settings → Display → Screen saver, or search "Screen saver" in Settings.)

## Build
Every push to `main` builds a signed APK with GitHub Actions and publishes it as a release.
Locally: `gradle :app:assembleRelease` (Android SDK required).
