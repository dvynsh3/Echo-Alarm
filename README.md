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
- Edit mode buttons per card: Pin, Duplicate, Delete, Reorder. Duplicate keeps every setting and renames the copy
  "Name (2)", then "Name (3)" and so on, placing it right after the original.
- Settings > Warning before deletion (off by default) asks for confirmation before any trash button deletes an alarm.
- An accent edge on the left of a card, full height and curving with the card corners, means that alarm is on.
- In the alarm editor the bottom row is: Delete (red circle, existing alarms only), Save (accent pill), Cancel (X circle). In edit mode, tapping a card does nothing.

## Turning off one ring without touching the alarm
- The banner at the top is the next ring across all alarms (a pending repeat counts too), with the alarm name and time,
  and a **Turn off upcoming alarm instance** button. It behaves as if you toggled the alarm off for that ring only:
  a weekday alarm skips to its next day, with its time, days and settings untouched. During a snooze/repeat session it
  stops that day's remaining repeats. A one-time alarm has no later ring, so it switches off.
- A snackbar offers **Undo**, and the card shows "Skipping Fri · next Mon". Editing or toggling the alarm clears the skip.
- The upcoming-alarm notification's Turn off does the same skip, and its Snooze 5 mins delays only that ring.
- The banner fills the top half of the screen and stays put: the cards slide up over it while it zooms out.

## Ringing: pop-up or pill
- Screen on and unlocked: a full-screen pop-up (swipe to turn off or silence, plus Delay 5 mins or the alarm's own Snooze).
  It is an overlay window, so Settings > Display over other apps must be on (asked the first time the app opens).
- Screen off or locked: the full-screen notification, as before.
- Pill (small notification with name, time, "rings again" and Turn off or Silence plus Delay) instead of the pop-up when the
  phone looks busy and Settings > Use pill when busy is on, or when the overlay setting is off:
  phone or VoIP call, microphone in use, full-screen app, keyboard open, casting or external display, car mode.
  While the pill shows it rechecks every 2 seconds and the pop-up takes over once the phone is free.
- Full-screen app and keyboard detection are best effort and unverified. When the phone gives no answer, it counts as not busy.
- Delay only moves the coming ring; the alarm's time and days stay as they are.

## Behaviour notes
- Each alarm has two pending intents: REGULAR (scheduled time) and AGAIN (snooze / auto-repeat re-ring).
- Snooze and Auto repeat are mutually exclusive.
- Auto-repeat ends only by: toggling the alarm off in the app, reaching its end time, or hitting max repeats.
- An unattended ring for 10 minutes counts as snooze / silence / turn-off depending on the alarm type.
- Reboot: ringing/repeating alarms are turned off with a notification unless "Resume repeating after reboot" is on.
- The alarm widget's + opens the normal new-alarm editor. There is no separate quick-alarm feature any more.

## Lock-screen ringing
Turn off and Silence are swipes: the X stays put and a translucent circle stays centred on it, growing outward as you pull;
let go past the threshold to fire. At rest the circle slowly breathes out from behind the X (1 s out, 2 s hold, 1 s back, 2 s hidden), pausing
while you touch and resuming about 2 seconds after you let go. Snooze stays a tap. The notification also has plain buttons as a fallback.

The ringing screen opens through a full-screen notification, plus a screen wake lock and a direct launch attempt.
On Android 14+ the "full-screen notifications" permission must be on. The app asks for it on launch, and
Settings > Reliability checklist has the same shortcut.

## Notifications
- Bedtime reminder, every night at 10:30 pm: "No alarm for tomorrow morning" if nothing rings between 4:00 and 11:00 am
  tomorrow, otherwise the earliest alarm's name and time as the heading, with "<name> rings tomorrow morning at <time>"
  when expanded. Switch it off in Settings.
- Upcoming alarm notice, 15 minutes before every enabled alarm (any snooze/repeat mode): name and time as the heading,
  a live countdown clock, and "<name> rings in N mins" when expanded (refreshed each minute, best effort in Doze).
  It has Turn off (switches the alarm off) and Snooze 5 mins (moves the alarm itself 5 minutes later and refreshes the
  notice immediately). Swiping it away stops the refreshes. Switch it off in Settings.
- A toast says "Alarm set for 2 hours and 3 minutes from now" whenever an alarm is saved or switched on.

## Timer, stopwatch, widgets, backup
- Timer: wheels, 3 editable presets (long-press), pause/resume/cancel, +1:00, rings with the alarm sound, survives reboot.
- Stopwatch: dial and digital pages, laps with split and total.
- Status bar pill: ongoing notifications with a native chronometer, promoted on Android 16+ where supported.
- Picture-in-picture: button on the running timer, or press Home on the Timer tab while counting down.
- Widgets: alarm list (toggle per alarm, + opens the new-alarm editor), timer, stopwatch.
- Backup/restore: Settings > Backup writes/reads one JSON file (alarms, timer presets, settings).

## Not yet verified
This code was written without being compiled or run. Expect to fix a few compile errors on first sync.
