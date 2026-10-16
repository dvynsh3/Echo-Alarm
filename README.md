# Alarm - No Snooze (`com.blehmen.alarmnosnooze`)

Kotlin, Jetpack Compose (Material 3), Room, DataStore, Glance.
Open this folder in Android Studio (Ladybug or newer), let Gradle sync, run on a device.
The Gradle wrapper JAR is not included: Android Studio fetches Gradle 8.9 from `gradle/wrapper/gradle-wrapper.properties`.

## Tabs
Alarms, Stopwatch, Timer (bottom bar, three items). Settings is the gear in the top corner of the Alarms tab.

## Alarms screen
- **+** (floating button, bottom right) and **+ / pencil** just above the first alarm add or edit alarms.
- **Edit mode** (pencil, or press and hold any alarm): each card shows Pin, Delete and a reorder handle.
  The system Back gesture (or the tick) leaves edit mode. Reordering is saved the moment you drop a card.
  Pinned alarms stay above unpinned ones; use Pin to move an alarm between the two groups.
- A small accent-coloured tab on the left edge of a card means that alarm is on.
- The alarm editor has a trash icon top right and a Delete pill above the Cancel | Save pill.

## Behaviour notes
- Each alarm has two pending intents: REGULAR (scheduled time) and AGAIN (snooze / auto-repeat re-ring).
- Snooze and Auto repeat are mutually exclusive.
- Auto-repeat ends only by: toggling the alarm off in the app, reaching its end time, or hitting max repeats.
- An unattended ring for 10 minutes counts as snooze / silence / turn-off depending on the alarm type.
- Reboot: ringing/repeating alarms are turned off with a notification unless "Resume repeating after reboot" is on.
- The alarm widget's + opens the normal new-alarm editor. There is no separate quick-alarm feature any more.

## Lock-screen ringing
The ringing screen opens through a full-screen notification, plus a screen wake lock and a direct launch attempt.
On Android 14+ the "full-screen notifications" permission must be on. The app asks for it on launch, and
Settings > Reliability checklist has the same shortcut.

## Timer, stopwatch, widgets, backup
- Timer: wheels, 3 editable presets (long-press), pause/resume/cancel, +1:00, rings with the alarm sound, survives reboot.
- Stopwatch: dial and digital pages, laps with split and total.
- Status bar pill: ongoing notifications with a native chronometer, promoted on Android 16+ where supported.
- Picture-in-picture: button on the running timer, or press Home on the Timer tab while counting down.
- Widgets: alarm list (toggle per alarm, + opens the new-alarm editor), timer, stopwatch.
- Backup/restore: Settings > Backup writes/reads one JSON file (alarms, timer presets, settings).

## Not yet verified
This code was written without being compiled or run. Expect to fix a few compile errors on first sync.
