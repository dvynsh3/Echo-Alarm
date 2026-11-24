# Echo

An Android alarm clock where you can automate snoozes to leave no way to half-wake yourself back to sleep. Echo started life as Alarm - No Snooze and keeps that spirit. Snooze and auto-repeat are choices you make per alarm, and an auto-repeating alarm can only be ended deliberately by openeing the app itself..

Built with Kotlin, Jetpack Compose (Material 3), Room, DataStore and Glance. Package name: `com.blehmen.alarmnosnooze`.

> **Status:** a personal project, iterated release by release from real use on a physical Samsung phone. See [CHANGELOG.md](CHANGELOG.md) for exactly what changed in each version.

## Why

The third-party alarm app I used had the right idea (auto-repeat instead of snooze) but a UI and workflow I disliked. Echo keeps the idea and redesigns the rest around a Samsung Clock look, with rounded cards and a floating bottom bar, plus the reliability options needed to stop Android from disabling alarms in the background.

## Install

1. Open the [**Releases**](../../releases) page and download `Echo-vX.Y.Z.apk` from the newest release.
2. Open the file on your phone and allow "Install unknown apps" for your browser or file manager when asked.
3. Each APK is signed with the same key, so a newer version installs **over** an older one and keeps your alarms and settings.
4. Requires **Android 8.0 (API 26) or newer**. Built against Android 15 (API 35).

> v1.0.0 has no APK because it does not compile. Use v1.1.1 or later.

### First-launch setup (important)

Alarm apps are the first thing Android's battery savers try to stop. On first launch Echo asks for what it needs; then open **Settings → Reliability checklist** and fix every row marked "Fix":

| Setting | Why |
|---|---|
| Notifications | Ring notifications, the 15-minute notice and the bedtime reminder |
| Exact alarms | So alarms ring at the exact minute |
| Full-screen notifications (Android 14+) | So the ring screen appears over the lock screen. Often off for apps installed outside the Play Store |
| Display over other apps | The ring pop-up while you are using the phone |
| Battery: unrestricted / exempt | Stops the system killing the app in the background |
| Auto-start (Samsung and other OEMs) | Lets the app restart after a reboot |

Then set an alarm 2 minutes ahead and test it with the screen off before you trust it.

## Features

### Alarms
- Samsung-style cards with large times and a toggle per alarm
- Per alarm: label, repeat days, sound, volume, vibration, gradual volume ramp
- **Snooze** (2 / 5 / 10 / 30 minutes or custom 1 to 720) or **Auto repeat** (same durations). The two are mutually exclusive
- Per-alarm **end time**; if blank, the app-wide max repeats applies (default 60)
- An auto-repeating alarm ends only by toggling it off in the app, reaching its end time or hitting max repeats
- A ring left alone for 10 minutes counts as snooze, silence or turn-off depending on the alarm type
- A toast tells you how long until it rings: "Alarm set for 2 hours and 3 minutes from now"

### Managing the list
- Floating **+** (bottom right), plus **+** and **pencil** above the first alarm. As you scroll they slide into a dock beside the gear: **+, Edit, gear**
- **Edit mode** (pencil, or press and hold a card; Back leaves it): **Pin**, **Duplicate** ("Name (2)", "Name (3)"…) and **Delete**. Press and hold a card, then drag, to reorder
- Pinned alarms stay above unpinned ones
- Optional **Warning before deletion** (Settings, off by default)
- An accent-coloured edge on the left of a card, curving with its corners, shows the alarm is on

### Skip one ring without touching the alarm
- The banner at the top shows the next ring (or the one ringing right now) with its name and time
- **Turn off upcoming alarm instance** dismisses only that ring. A Mon to Fri alarm skipped on Friday rings next on Monday, with its time and days untouched. **Undo** is one tap away
- While an alarm is ringing, the banner offers **Turn off ringing alarm**, which ends this ring without turning the alarm off

### When an alarm rings
- **Swipe to turn off** or **swipe to silence** (Samsung-style growing circle with a haptic tick). Snooze and **Delay 5 mins** are taps
- Screen on and unlocked: a full-screen pop-up. Home and Back leave it and the alarm keeps ringing; stop it from the notification or the app
- Screen off or locked: the full-screen notification over the lock screen
- **Pill mode:** during phone and VoIP calls, microphone use, casting or external display, car mode, and (best effort) full-screen apps, a compact notification is shown instead of the pop-up. Tap it for the full screen
- The sound always plays, whichever form the alert takes

### Notifications
- **Upcoming alarm**, 15 minutes before any enabled alarm: live countdown, **Turn off** (skips that ring) and **Snooze 5 mins**
- **Bedtime reminder**, 10:30 pm: "No alarm for tomorrow morning", or the earliest alarm's name and time. It removes itself at 6 am, or as soon as your first alarm rings

### Timer and stopwatch
- Timer: Samsung-style wheels, three editable presets, pause/resume, +1:00, rings with the alarm sound, survives the app being closed and reboots
- Stopwatch: dial and digital pages, laps with split and total
- Live status-bar chip on Android 16+ (a normal ongoing notification on older versions) and a **picture-in-picture** mini countdown

### Widgets
Alarm list (toggle per alarm, + opens the new-alarm editor), timer and stopwatch.

### Settings and look
- Dark, light or system theme
- Accent colour: six presets (cyan, purple, green, orange, pink, red) or a custom hex
- Defaults for new alarms: sound, volume, gradual ramp
- Max repeats, resume repeating after reboot, display over other apps, use pill when busy, bedtime reminder, upcoming alarm notice
- **Backup and restore** everything as one JSON file (alarms, timer presets, all settings). The file is validated before anything is changed

### After a reboot
Alarms that were mid-ring or repeating are switched off with a notification explaining why (unless "Resume repeating after reboot" is on). Upcoming alarms are kept. A one-time alarm that came due while the phone was off is switched off with a "Missed alarm" notice.

## Build from source

Requirements: **Android Studio Ladybug or newer**, **JDK 17** (bundled with Android Studio) and the **Android 15 (API 35)** SDK platform.

1. Clone the repo and choose **File → Open** on the repo folder.
2. Let Gradle sync. The Gradle wrapper JAR is not included, so Android Studio fetches **Gradle 8.9** from `gradle/wrapper/gradle-wrapper.properties`.
3. Run on a **real phone**. Alarms, status-bar chips and widgets do not behave properly on an emulator.

Or from the command line, with Gradle 8.9 installed:

```
gradle assembleDebug
```

### Project layout

```
app/src/main/java/com/blehmen/alarmnosnooze/
  data/      Room entities and DAO, DataStore settings, timer storage, JSON backup
  engine/    scheduling (AlarmManager), reboot handling, notifications, bedtime reminder, timer engine
  service/   foreground ringing service, ring overlay, busy-state detector, timer service
  ui/        Compose screens: alarms list, editor, settings, timer and stopwatch, wheel picker, theme
  widget/    Glance widgets
```

Each alarm keeps two pending intents: one for the scheduled time and one for the snooze or auto-repeat re-ring.

## Releases and CI

Pushing a tag such as `v1.6.0` runs [`.github/workflows/release.yml`](.github/workflows/release.yml), which builds the APK on GitHub Actions and publishes a Release with the matching [CHANGELOG](CHANGELOG.md) section as its notes. The version name and code are taken from the tag. All builds are signed with the shared debug key in `ci/debug.keystore` so versions can update over each other. That key is public, so this is for personal use, not a Play Store build.

To rebuild a release: **Actions → Build and release APK → Run workflow**, then enter the tag.

## Known limitations

- **Keyboard-open detection does not work** on my Galaxy S24-class phone (Android does not let one app see another's keyboard); you get the pop-up instead of the pill. The only reliable fix is an optional accessibility permission, which is not included. Full-screen app detection is best effort.
- Not yet verified on a device: the Android 16 status-bar chip, alarms surviving aggressive OEM battery killers over days, and some widget tap behaviour.
- The upcoming-alarm "N mins" text can lag in Doze (the countdown clock stays accurate).
- Custom alarm sounds are stored as references. If the sound is missing after a restore, the default alarm tone plays.
- The timer ends via `setAlarmClock`, so Android may show a running timer as the "next alarm" on the lock screen.

## Permissions

`POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE` (+ media playback), `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. There is no internet permission and no analytics; everything stays on the device.

## Built with AI assistance

Echo was designed, directed and tested by me on a physical device, and the code was written with AI assistance (Claude) over many iterations. Every version's requests and results are recorded in the [CHANGELOG](CHANGELOG.md).

## Credits

- Interface inspired by the **Samsung Clock** app and by [**Mihon**](https://github.com/mihonapp/mihon)
- Behaviour inspired by the [Repeat Alarm](https://play.google.com/store/apps/details?id=com.kittoboy.repeatalarm) app

## License

No license has been chosen yet. Add a `LICENSE` file (for example MIT) to say how others may use the code.
