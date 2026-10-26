# Tiko

A study-and-work accountability pet for Android. A small 3D-style tiger cub, **Pawfessor**, lives on your phone.

- **The gate.** Open an app you chose to guard (Instagram, Pinterest, games...) and Pawfessor takes the whole screen first with a silent speech bubble, then offers three choices: **Close the app**, **Just 5 minutes**, **I need a break for half an hour**. Close goes home; the other two let you stay, then he comes back when time is up.
- **Companion.** While you are allowed to stay, and on the home screen, he walks along the very bottom edge of the screen, sits, looks around, grooms, rolls. After you unlock the phone he walks in from the left and sits in the right corner. He stretches only after all goals are done or the night talk is finished.
- **Goals.** Each goal has the reason you want it done early. Tick it when done. Pending goals show up in his messages.
- **Night check-in.** At the hour you choose (default 12 am) a chat opens after you unlock. Pawfessor asks why each goal is unfinished and states how many hours you gave to your distracting apps, even when your reason is genuine.
- **Chat** with him any time, for example when you feel low.

Everything personal is typed into the app (Settings > Your details). Nothing personal is in this repository.

## Install
1. Upload this repo to GitHub (branch `main`). Open **Actions**, run **Build Tiko APK**, download the artifact `Tiko-debug-apk`, install `app-debug.apk`. Or open the folder in Android Studio and press Run.
2. Open Tiko > Settings. Allow: display over other apps, usage access, notifications. Switch Pawfessor on duty. Tap the pencil on the home screen to add goals; tap "Watching" to choose the guarded apps.

Requires Android 9 or newer.

## Make it yours
- **Messages**: `app/src/main/assets/messages.txt`, one per line as `category|mood|text`. Placeholders such as `{exam}` or `{she}` are filled from Settings > Your details; a line is skipped if one is empty. Live placeholders: `{minutes} {count} {n} {app} {days_left} {done} {total} {left} {goal} {reason}`. Add your own lines in Settings, or tap **Generate more messages with Claude** (needs an Anthropic API key).
- **Animations**: transparent animated WebP clips in `app/src/main/assets/clips/` (see `clips/README.txt`). Make your own from green-screen videos: copy `tools/clips.example.json`, edit it, run `python3 tools/make_clips.py clips.json app/src/main/assets/clips` (needs ffmpeg, Pillow, numpy, scipy). Any shade of green works, and all clips are put on one common scale so the pet never changes size between clips. Missing clips fall back to still images in `res/drawable-nodpi`.
- **Size and timing**: `tp` (tiger box, px) in `PetService.kt`; `SPLASH_MS` in `SplashActivity.kt`; the 5-minute and half-hour allowances in `PetService.choose()`; the check-in hour in Settings.

## Privacy
Goals, details and your API key stay on the phone. The key is sent only to `api.anthropic.com`, and only for chat, check-ins and message generation. Screen time is read locally with Usage Access.

## Notes
- The night check-in uses an exact alarm (shown as a clock in the status bar) and waits for you to unlock.
- Some phone makers (Xiaomi, Oppo, Vivo, Samsung) stop background apps. Turn off battery optimisation for Tiko.

MIT licensed.
