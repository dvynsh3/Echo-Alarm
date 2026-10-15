# Alarm - No Snooze (`com.blehmen.alarmnosnooze`)

Kotlin, Jetpack Compose (Material 3), Room, DataStore.
Open this folder in Android Studio (Ladybug or newer), let Gradle sync, run on a device.
Gradle wrapper JAR is not included: Android Studio will fetch Gradle 8.9 from
`gradle/wrapper/gradle-wrapper.properties`.

## Stage status
1. Data layer + scheduling engine ........ DONE (Room, AlarmManager.setAlarmClock, snooze, auto repeat, quick, reboot)
2. Ringing service + ringing screen ...... DONE
3. List, editor, pin, reorder, theme ..... DONE
4. Date reminders ........................ DONE
5. Timer + stopwatch, status pill, PiP ... DONE
6. Settings extras, backup/restore ....... DONE
7. Widgets ............................... DONE

## Behaviour notes
- Each alarm has two pending intents: REGULAR (scheduled time) and AGAIN (snooze / auto-repeat re-ring).
- Snooze and Auto repeat are mutually exclusive (turning one on turns the other off).
- Auto-repeat ends only by: toggling the alarm off in the app, reaching its end time, or hitting max repeats.
- Unattended ring for 10 min counts as snooze / silence / turn-off depending on the alarm type.
- Reboot: ringing/repeating alarms are turned off with a notification unless "Resume repeating after reboot" is on.
  Upcoming alarms are kept. One-time alarms that were due while the phone was off are switched off with a "Missed" notice.

## Date reminders
- A reminder has a date and an optional time. With no time it fires at the default time (Settings, default 9:00 am).
- Fires with a normal notification sound on its own channel ("Date reminders"), as a persistent notification.
- Clearing the notification or tapping Turn off marks it done: it stays in the list, greyed out, until you delete it.
- Reboot: pending reminders are re-armed, overdue ones fire late, active ones are re-posted.
- Database is now version 2 (migration included, existing alarms are kept).

## Timer and stopwatch
- Timer: wheels, 3 editable presets (long-press to edit), pause/resume/cancel, +1:00. Ends with the alarm sound and a
  Stop / +1:00 notification. Survives the app being closed (exact alarm + persisted state) and is re-armed after reboot.
- Stopwatch: two pages (dial and plain digits), laps with split and total, Lap becomes Reset while paused.
- Status bar pill: running timer/stopwatch are ongoing notifications with a native chronometer. They request promotion to
  the Android 16+ status bar chip through the documented extras; older versions show a normal ongoing notification.
- PiP: button on the running timer, or press Home while on the Timer tab with a running countdown.
- The timer end uses setAlarmClock for reliability, so Android may show a running timer as the "next alarm" on the lock screen.

## Settings and backup
- Defaults for new alarms (sound, volume, gradual ramp) are applied when you create an alarm or a quick alarm.
- Backup writes one JSON file (alarms, reminders, timer presets, all settings) through the system file picker.
  Restore replaces everything. The file is validated before anything is touched, so a bad file changes nothing.
- Ringing sessions, the running timer and the stopwatch are not backed up.
- Restored reminders that are already past come back greyed out. Custom alarm sounds are stored as URIs, so a sound
  that doesn't exist on the new phone falls back to the default alarm tone when it rings.

## Widgets (Glance)
- Alarm widget (4x3, resizable): next alarm, scrollable alarm list with a toggle per alarm, and + which opens the app
  with the quick-alarm dialog. Tapping an alarm's text opens the Alarms tab.
- Timer widget (2x2): idle shows your three presets (tap to start); running shows a live countdown with Pause/Cancel;
  paused shows Resume/Cancel; finished shows Stop.
- Stopwatch widget (2x2): live count-up, Start/Stop, Lap, Reset.
- Live counting uses a real Chronometer, so widgets don't need constant updates. Widgets refresh whenever data changes
  (the app process observes alarms, timer, stopwatch and accent colour).
- Widgets use a fixed dark card with your accent colour on the buttons.

## Not yet verified
This code was written without being compiled or run. Expect to fix a few compile errors on first sync.
