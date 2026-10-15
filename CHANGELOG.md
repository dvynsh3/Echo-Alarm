# Changelog

All notable changes to **Echo** (originally *Alarm - No Snooze*, package `com.blehmen.alarmnosnooze`), newest first.
Every entry lists what was asked for and what was delivered. Dates are IST, taken from the build timestamps inside each zip.

**About the version numbers.** Releases follow the folder names you saved (`v0.1.0` … `v1.6.0`). Several chat deliveries were never saved as their own version, so their changes appear in the next saved release; each entry says which. `v0.7.0` is not listed because it is a duplicate of `v0.6.0` (see [Release notes](#release-notes)). Everything was written without being compiled in the chat sandbox, so the compile and device results below come from your own testing.

---

## [v1.6.0] - 2026-10-29
Rename to **Echo**, plus the bedtime notification now cleans up after itself. Rolls up two chat builds (the noon version and the 6 am version).
### Added
- **Bedtime notification auto-removal.** The 10:30 pm notification is cleared as soon as any alarm rings between midnight and 6 am (counts as "the first alarm in the morning"). It also removes itself at 6 am on its own, which covers the "No alarm for tomorrow morning" version that has no alarm to clear it. An alarm ringing the evening before (for example 11 pm) leaves it alone. The first build used noon; you changed it to 6 am, and only 6 am is in this release.
### Changed
- **App name is now Echo**: launcher label, widget picker and Settings text. The folder, package name, zip name and backup file marker keep the old names, so existing backups still restore. The "not an Echo backup" error message uses the new name.
- `update_project.bat` comments corrected to say `AlarmNoSnooze` (v1.0.0 to v1.5.0 carried comments copied from the NecroMergerEvents project; behaviour was already correct).

---

## [v1.5.0] - 2026-10-25
Card-edge polish, ring-notification text, and a new Undo layout.
### Fixed
- **Thin 1 px accent trim** on the top-right and bottom-right curved corners of an alarm that is turned on. The old edge painted the whole accent shape under the card and covered most of it, leaving an anti-aliased hairline. It now draws only the left crescent. The delete-warning card uses the same edge.
### Changed
- **Ring notification text.** "Ringing now" removed (obvious while the alarm sounds). It now shows only the rings-again line: "Rings again in 2 min after Silence", "Snooze rings again in N min" or "Delay rings again in 5 min".
- **Undo moved out of the toast.** The Snackbar is gone. After you turn off an upcoming instance, a small round **undo icon** appears to the right of the "Turn off upcoming alarm instance" button, which stays centred. The icon stays while you are on this screen and disappears when you open an alarm's details, switch tabs or leave the app; it is not restored afterwards.
### Added
- When **all alarms are off**, Undo appears as a full button in the same style as "Turn off upcoming alarm instance", with the undo icon and the word "Undo", sitting where the main button normally is.

---

## [v1.4.0] - 2026-10-25
Top buttons redesigned as a dock that opens as you scroll.
### Changed
- The black pill around the + and pencil icons, and the full-width bar idea that followed, are replaced by a dock modelled on the bottom navigation:
  - **Closed (top of list):** only the gear shows, alone in a grey circle in the top corner. The gear is the highlighted item but in greys, not the accent colour.
  - **Scrolling:** the dock opens to the left of the gear as a rounded pill. It starts when the + and pencil above the first alarm are about three rows from the top and finishes as they reach it. **Edit** slides in first, then **+**, so the final order is **+ Edit gear**.
  - The + and pencil above the first alarm still scroll with the list and fade out as the dock opens, so two sets of the same icons never show.
  - If the list is too short to scroll, the dock stays closed and the + and pencil above the first alarm remain visible.

---

## [v1.3.0] - 2026-10-25
The currently ringing alarm now shows in the top banner.
### Added
- **"Ringing now" banner.** While an alarm is ringing, the banner shows "Ringing now", the alarm's name and time (for example "Morning · 7:45 am") and a **Turn off ringing alarm** button. Tapping it ends this ring, the same as the Turn off swipe: a repeating alarm stays on for its next day, a one-time alarm switches off, and nothing about the alarm's details changes, so you never have to toggle the alarm itself off.
- The alarm's own card in the list gets a **"Ringing now"** tag.
- If an alarm is ringing while you edit, the ringing banner takes over so the button is never hidden.
### Fixed
- Previously the banner ignored a ringing alarm and showed the next day's ring (for example "Alarm in 23 hours" while the phone was going off). The between-repeats behaviour (countdown plus "Turn off upcoming alarm instance") already worked and is unchanged.

---

## [v1.2.0] - 2026-10-25
Edit mode, ring-screen navigation and the first version of the scrolling top buttons. Rolls up the "+ and Edit merge into the gear" fix and the edit-mode / Home-button build.
### Added
- **+ and Edit dock beside the gear** once you have scrolled, in the order **+, Edit, gear** (first version; reworked in [v1.4.0]).
- **Home, Recents and Back on the ring screen.** The pop-up opens as a real screen again, so pressing Home, Recents or Back leaves it like any app and the alarm keeps ringing; turn it off from the notification or by reopening the app. Android only allows launching it from the background while the app holds an overlay window, so a tiny invisible one is held for a moment during launch. If the screen has not appeared within about 0.8 s, the app falls back to the old overlay pop-up, where Back hides it without stopping the alarm. Home is best effort in that fallback.
- **Drag from anywhere.** Press and hold anywhere on a card in edit mode, then drag, with a haptic tick. A plain swipe still scrolls the list.
### Changed
- The Undo message sits above the floating + button instead of behind it.
### Removed
- The reorder handle icon (up/down arrows) from each card in edit mode.
### Fixed
- **Cards shrinking in edit mode.** The time text was animating from 44 to 38 to make room for the fourth icon; it now stays at 44.
- **Shrinking banner text peeking through the list.** The banner now reaches fully transparent and zero size before the cards get to it, so it cannot show through the gaps.

---

## [v1.1.3] - 2026-10-24
### Fixed
- **Banner displayed extremely small.** The zoom was reading each item's position, and Android reports that position without the list's half-screen top padding, so the app thought the first card was already near the top and shrank the banner to almost nothing at rest. The padding is now added back; the banner starts at full size and shrinks as cards slide over it.
- The same missing-padding mistake in the **drag-to-reorder auto-scroll** (scrolling when you drag near the top or bottom edge) is corrected.

---

## [v1.1.2] - 2026-10-24
Four fixes to the ring pop-up and banner.
### Fixed
- **Translucent pop-up.** The ring screen's gradient had a see-through colour stop that let the app behind show in the pop-up. The background is now fully opaque. If the pill is not possible (for example notifications are blocked), the alarm goes straight to the full display instead.
- **Pill stays a pill.** The behaviour that swapped the pill for the pop-up when the phone became free is removed. Tapping the pill opens the full-screen, opaque ring screen.
- **Zoom-out stopped early.** The banner zoom was driven by a scroll value that jumps once the first row leaves view. It now follows the first alarm card's position, so the banner keeps shrinking smoothly until the card reaches the top, ending at nothing.
- **"Turn off upcoming alarm instance" did nothing.** The banner sat behind the list and the list swallowed every tap. The button now sits in a thin layer in front and fades out as the cards approach, so it cannot be tapped while hidden. The banner text still sits behind the list.
### Notes
- **Keyboard detection did not work** on your phone (it showed the pop-up when the keyboard was open). Android does not let one app see another app's keyboard. The only reliable fix is an optional accessibility permission, which was not added. With the keyboard open you now get the opaque pop-up. This is recorded in the README.

---

## [v1.1.1] - 2026-10-23
### Fixed
- **Six Android Studio build errors, all in `RingOverlay.kt`** (`Unresolved reference 'ViewTreeLifecycleOwner'`, `ViewTreeViewModelStoreOwner` and `ViewTreeSavedStateRegistryOwner`). In the newer lifecycle libraries these three helpers became extension functions, so the old imports no longer resolved. The file now uses `setViewTreeLifecycleOwner`, `setViewTreeViewModelStoreOwner` and `setViewTreeSavedStateRegistryOwner`. No behaviour change.

---

## [v1.0.0] - 2026-10-23
Skip a single ring, plus the overlay pop-up and busy-aware pill. This is the big build after the "off for today, back on tomorrow" discussion. It also includes the `update_project.bat` helper (first shipped in the dropped `v0.7.0`).
> Known issue at release: does not compile, because of the `RingOverlay.kt` errors fixed in [v1.1.1]. Build [v1.1.1] or later.
### Added
- **Turn off upcoming alarm instance.** The top banner (now half the screen tall, with the alarm's name and time) has a button that treats the alarm's next ring as dismissed, without touching the alarm itself. A Mon to Fri 7:45 alarm skipped on Thursday evening rings next on Monday, with its time, days and settings unchanged.
  - During a repeat session it ends that day's remaining repeats; a one-time alarm has no later ring, so it switches off.
  - The skip is stored per alarm (database version 4, migrated automatically), so a reboot or clock change does not quietly un-skip it. The card shows "Skipping Fri · next Mon". A Snackbar offers Undo, and editing or toggling the alarm clears the skip.
  - The banner now counts a pending repeat as the next alarm (before, it kept saying "18 hours" while an alarm repeated every 2 minutes). The 15-minute notice, bedtime reminder and widget all ignore a skipped ring.
  - The banner stays in place while cards slide up over it, zooming out as they cover it and back in as you scroll down.
  - The **upcoming-alarm notification's Turn off** now does the same skip. Its **Snooze 5 mins** delays only that ring, no longer moving the alarm's own time.
- **Full-screen pop-up over other apps** for all alarms. First launch asks for "Display over other apps"; Settings has a matching **Display over other apps** switch that reflects the real permission and says "needs permission" when it is off. Screen on and unlocked gives the pop-up; screen off or locked keeps the existing full-screen notification.
- **Pill instead of the pop-up when the phone is busy**, controlled by a new **Use pill when busy** switch (on by default). The pill shows the alarm name and time, says it rings again, and has Turn off or Silence plus Delay.
  - Reliable detection: phone or VoIP call, microphone in use (meetings, recorders, dictation), casting or external display, car mode.
  - Best-effort detection: full-screen or immersive apps and keyboard open. If the phone gives no answer it counts as not busy.
  - While the pill is showing, the app rechecks every 2 seconds. The alarm sound always plays, pill or pop-up.
- **Delay button.** A one-off delay of the coming ring only, never the alarm's time or days. It reads "Snooze N mins" for alarms that have snooze, otherwise "Delay 5 mins".
- `update_project.bat` in every zip, as you asked: extract it into the `vX` folder, then it deletes `app\src` in the project one level up and copies the new files over it. Close Android Studio first. In this release it also offers to create the project on the very first run.
### Fixed
- Drag-to-reorder auto-scroll measured edges from the wrong origin now that the list starts half-way down the screen.

---

## [v0.6.0] - 2026-10-17
Swipe finished, toast, banner flicker and notification buttons. Contains the swipe fix that was missing from the previous build.
### Fixed
- **Swipe to dismiss, from your screenshot.** The translucent circle now stays centred on the X and only its radius grows as your finger moves away (it was moving with your finger). The breathing animation always starts from hidden behind the X, both on first show and every time it resumes after a touch.
- **Banner flicker.** The "Alarm in 1 hour 16 minutes" banner cross-faded its text every time the minutes changed, so both versions were half transparent for a moment (the dip to black). The animation now plays only when the kind of message changes (edit mode, alarms off, next alarm); minute updates change the text in place.
### Added
- **Toast when an alarm is set**: "Alarm set for 2 hours and 3 minutes from now". Plurals and days work ("1 day, 2 hours and 5 minutes", "1 hour"). Shown when saving an alarm, switching one on, and switching one on from the widget.
- **Upcoming-alarm notification buttons.** **Turn off** switches the alarm off entirely and removes the notification. **Snooze 5 mins** moves the alarm itself 5 minutes later, so it changes in the app too, refreshes the notification immediately with the new time and countdown, works even when the new time is more than 15 minutes away, and shows the toast. For a repeating alarm this changes its time for every day. (Replaced in [v1.0.0].)
### Changed
- The **+ and pencil icons** use the text colour, so they are white in the dark theme (and do not vanish in the light theme).

---

## [v0.5.0] - 2026-10-17
Swipe animation timing and curved card edge.
### Changed
- **Swipe to dismiss.** X shake removed. Breathing is much slower, on a 6-second cycle: the layer expands for 1 s at the previous rate, holds for 2 s, tucks back over 1 s, then stays hidden behind the X for 2 s before starting again. It still pauses while you touch and resumes about 2 seconds after you let go.
- **Card edge.** The half-height tab on the left of an alarm that is on is replaced by an edge running the full height of the card, top to bottom. It follows the rounded corners and thins to a point where they flatten, so it is curved at both ends. Thickness unchanged (6 dp). The delete-warning dialog uses the same edge.
### Fixed
- **Swipe was getting stuck.** The drag code marked each touch as handled before reading how far the finger had moved, and Android reports zero movement for a handled touch, so the circle never followed your finger and you could not reach the full extent. It now reads the movement first. (You suggested an invisible drag handle; it was not needed.)

---

## [v0.4.0] - 2026-10-17
Duplicate, delete warning, notifications, new swipe visuals and the editor button row. Rolls up three chat builds (reorder/duplicate/notifications, the Trash-Save-X change, and Save text colour).
### Added
- **Duplicate button** between Pin and Trash in edit mode (copy icon). It keeps every setting, including whether the alarm is on, and names the copy "Name (2)", then "Name (3)" and so on. A blank name becomes "Alarm (2)". The copy sits right after the original.
- **Warning before deletion** setting, off by default. When on, either trash button opens a card in the alarm-card style with the accent edge, a **Cancel** button in your accent colour and a red **Delete** button with white text.
- **Bedtime reminder, 10:30 pm nightly.** With nothing ringing between 4 am and 11 am tomorrow it says "No alarm for tomorrow morning". With an alarm, the heading is "<name> · <time>" and, when expanded, "<name> rings tomorrow morning at <time>", with extra morning alarms listed too.
- **Upcoming alarm notification, 15 minutes before every enabled alarm** (snooze, auto repeat or plain). Same heading style, a live ticking countdown clock, and "<name> rings in N mins" when expanded. If you set an alarm less than 15 minutes ahead the notice appears straight away. Swiping it away stops its updates. The "N mins" wording refreshes each minute (Android may delay that while the phone sleeps; the countdown clock stays accurate).
- Two Settings switches you did not ask for, both on by default: **Bedtime reminder** and **Upcoming alarm notice**, so a nightly notification is never without an off switch. Both are included in backup and restore, as is Warning before deletion.
### Changed
- **Alarm details bottom row.** Left to right: Trash (red circle, unchanged, existing alarms only), **Save** as an accent-coloured pill, and **X** as a circle in the old pill's grey with the X in your accent colour (it does what Cancel did).
- **Save text colour switches between black and white by accent** using the app's existing black-or-white rule: black on cyan, green and orange; white on purple, pink and red. Custom hex follows the same rule. Purple sits right on the cutoff.
- **Swipe visuals.** The X stays still and a translucent layer follows your finger, growing as you pull. At rest the layer breathes in and out from behind the X and the X gives a small shake every few seconds. Both pause as soon as you touch and resume about 2 seconds after you let go. (Refined in [v0.5.0] and [v0.6.0].)
### Fixed
- **Drag-to-reorder glitching after two reorders.** The working list was re-created after each save, so the drag handlers kept writing to an old copy while the screen showed the new one. There is now one working list that lasts through the drag and hands back to the database list once the saved order matches.

---

## [v0.3.0] - 2026-10-16
Editor delete button, edit-mode taps, and swipe to dismiss.
### Changed
- **Alarm details: delete.** The top-right trash icon is gone. Delete is now a red circle with a trash icon, separate from the pill, to the left of Cancel | Save. It shows only for existing alarms.
- **Edit mode:** tapping a card now does nothing (you still open an alarm's details by tapping it in normal mode).
### Added
- **Swipe to dismiss on the ringing screen** in the Samsung style. Turn off and Silence are swipes, not taps: drag the circle in any direction and a translucent circle fills in behind it as you pull while the hint text fades. Past the threshold you feel a haptic tick; letting go floods the screen and fires the action; letting go earlier springs back.
  - A plain alarm shows "Swipe to turn off". An auto-repeat alarm shows "Swipe to silence" with "Rings again in X mins" underneath. **Snooze stays a tap** on its pill under the circle. The notification's plain buttons remain as a backup.
  - Threshold distance, circle growth and spring-back were tuned by eye and flagged for adjustment after phone testing.

---

## [v0.2.0] - 2026-10-16
Large UI pass after first real use. Rolls up the animations/lock-screen/layout build and the "widget + opens the normal editor" build.
### Added
- **Animations everywhere.** The alarm editor and Settings slide in from the right and slide back out when you leave; switching tabs slides left or right by direction; the timer, alarm cards, edit controls, headline, floating button and the editor's expanding rows are animated; dialogs use Android's normal pop-up animation.
- **Lock-screen ring fix.** The alarm screen did not appear and the screen did not wake when the phone was locked. Cause: the first notification the ringing service posted had no full-screen intent (the real one only arrived as an update), so Android never treated it as a lock-screen takeover. The first notification now carries the full-screen intent, with a screen wake lock and a direct launch attempt as backups. On Android 14+ the "full-screen notifications" permission is often off for apps installed outside the Play Store, so the app asks for it on launch with a button to the right settings page.
- **Floating "+" button** (circle with plus) bottom right. A **+** and a **pencil** sit just above the first alarm and scroll with the list (they are the first pinned item if any alarm is pinned).
- **Edit mode:** the pencil or press and hold on any alarm enters it; Back, or the pencil turning into a tick, leaves it. Each card shows **Pin**, **Delete** (trash) and a **Reorder** handle (up/down arrows). Pinned alarms stay above unpinned ones.
- **More accent colour:** a small curved accent tab on the left edge of a card when the alarm is on, the selected item of the bottom navigation, the timer wheel colons, timer presets, stopwatch splits and the +/pencil icons.
- **Alarm details:** a trash icon at the top right of the time wheels, and a **Delete pill**, narrower than the Cancel | Save pill, above it.
### Changed
- **Top buttons.** The top dropdown is removed; only the settings gear remains, alone in the corner.
- **Snooze duration pop-up** rebuilt with no extra gap between the options and Cancel. The custom field focuses itself, rejects values outside 1 to 720, and Done on the keyboard sets it.
- **Widget +** now opens the normal new-alarm editor (same slide-in, wheels and your default sound and volume), as you confirmed. If the app is already open it jumps to the Alarms tab and slides the editor in. The widget's empty state reads "Tap + to add one".
### Removed
- **Quick alarm**, button, dialog and widget flow. Quick alarms you had already created are untouched and keep a small "Quick" tag.
- **The entire Date reminders tab and its functionality** (reminder data, scheduler, editor, list screen and Settings default time). The bottom bar now has three items: **Alarms, Stopwatch, Timer**. The database moves to version 3 with migrations from versions 1 and 2, dropping the leftover reminders table and keeping your alarms.
### Fixed
- **Reorder not working sometimes.** The order was being written one alarm at a time, which made the list flicker; it is now a single write. Fast drags, drags near the screen edge and stale layouts are handled, and the new order is saved when you drop the card. A card only swaps with others in its own pinned/unpinned group; use Pin to move it between them.

---

## [v0.1.0] - 2026-10-15
First complete build: all seven planned stages from the original brief ("Alarm - No Snooze: final feature list v4"), delivered as one zip after you asked for the final version. Replaces five stage-by-stage drops.
### Added
- **Project:** Kotlin, Jetpack Compose Material 3, Room, DataStore and Glance; package `com.blehmen.alarmnosnooze`; Gradle 8.9 via `gradle-wrapper.properties` (wrapper JAR not included, Android Studio fetches it).
- **Scheduling engine.** Alarms use `AlarmManager.setAlarmClock`, so they ring exactly and survive Doze.
  - Per alarm: label, repeat days, sound, volume, vibration, gradual volume ramp.
  - **Snooze** toggle with duration 2 / 5 / 10 / 30 / custom.
  - **Auto repeat** toggle with its own duration (same picker), mutually exclusive with snooze.
  - **End time** per alarm; when blank the app-wide max repeats applies (default 60).
  - **Quick alarm:** set only a time, it rings once, then toggles itself off. (Removed in [v0.2.0].)
- **Reboot behaviour:** alarms that were mid-ring or repeating are turned off with a notification explaining why, unless "Resume repeating after reboot" is on; upcoming alarms are kept; a one-time alarm that came due while the phone was off is switched off with a "Missed alarm" notice.
- **Ringing service and screen.** A foreground service plays the sound, vibrates and ramps the volume. The screen shows Turn off (plain), Turn off + Snooze (snooze alarm) or Silence with "Rings again in X mins" (auto repeat); an auto-repeating alarm ends only when you toggle it off in the app. You approved four decisions: an alarm left ringing 10 minutes is treated as ignored (snooze, silence or turn off by type); Turn off is a single tap; saving an alarm always enables it; the date-reminder, timer and stopwatch tabs started as placeholders and were built in later stages.
- **Alarm list and editor.** Samsung-style cards with large times and a toggle per card; time wheels with a Cancel | Save pill; long-press a card to pin or delete; overflow menu then Reorder shows drag handles; a bolt button creates a quick alarm.
- **Date reminders tab.** A date plus an optional time (default 9:00 am, changeable in Settings; changing the default re-arms reminders that have no time of their own). Posts a persistent notification on its own channel with the normal notification sound. Clearing it or tapping Turn off marks it done: it stays in the list, greyed, until you delete it. Save is disabled with a warning if the chosen time has passed. Reminders are re-armed after reboot and overdue ones fire late. Database moved to version 2 with a migration so version 1 installs keep their alarms. (Removed in [v0.2.0].)
- **Timer.** Hours / Minutes / Seconds wheels, three presets (10, 15, 30 minutes) that load on tap and edit on long-press, a progress ring, Pause/Resume, Cancel and +1:00. At the end it plays the alarm sound and vibrates, shows "Time's up" with Stop and +1:00 (same buttons in the notification), and stops by itself after 5 minutes. Keeps running with the app closed and is re-armed after reboot. It ends via `setAlarmClock`, so Android may show a running timer as the "next alarm" on the lock screen.
- **Stopwatch.** Swipe between a dial page and a plain digital page; laps list split and running total, newest first; the left button is Lap while running and Reset while paused; state is saved so it survives the app being killed.
- **Status bar pill and mini window.** A running timer or stopwatch is an ongoing notification with a built-in chronometer so it ticks without being re-posted; it asks Android 16+ to show it as the status bar chip, older versions show a normal ongoing notification. Picture-in-picture countdown from a button on the running timer, or by pressing Home on the Timer tab while it counts down.
- **Settings.** Dark / light / system theme; accent colour with 6 presets (cyan, purple, green, orange, pink, red) plus custom hex; max repeats; resume after reboot; date-reminder default time; **defaults for new alarms** (sound, volume, gradual ramp); **JSON backup and restore** through the system file picker (alarms, reminders, timer presets and every setting; the whole file is validated before anything changes, out-of-range values are clamped, restored alarms are re-armed, past or turned-off reminders come back greyed; ringing sessions, the running timer and the stopwatch are not backed up; custom sounds are stored as references and fall back to the default alarm tone if missing); **Reliability checklist** (exact alarms, notifications, full-screen intent, battery exemption, OEM auto-start links).
- **Widgets (Glance).** Alarm widget: next alarm, scrollable list with a toggle per alarm, and + for a quick alarm. Timer widget: your three presets when idle, a live countdown with Pause and Cancel while running, Stop when finished. Stopwatch widget: live count-up with Start, Stop, Lap and Reset. Widgets refresh when alarms, the timer, the stopwatch or the accent colour change, and use a fixed dark card with your accent on the buttons.
### Known gaps at release
- Not compiled or run when delivered. The Mihon layout was not applied beyond rounded cards and the bottom pill; the Samsung screenshots drove the design.
- Unverified: the Android 16 status bar chip, alarms surviving Doze and OEM battery killers, and the widget tap actions.

---

## Release notes

**Removed duplicate:** `v0.7.0` has byte-identical app code to `v0.6.0`. The only difference is that its zip adds `update_project.bat` (the helper you asked for after v0.6.0). Releasing both would give two identical APKs, so it is dropped. Its helper script ships from [v1.0.0] onward.

**Skipped numbers are intentional.** The released versions are v0.1.0 to v0.6.0, then v1.0.0 to v1.6.0 (with v1.1.1 to v1.1.3 as small patches). There is no v0.7.0 as explained above.

**Housekeeping (not changed in the code):**
- `versionName` is `0.1.0` and `versionCode` is `1` inside every version's `app/build.gradle.kts`. The bulk-build script sets them per release when it builds the APKs.
- v1.0.0 does not compile ([v1.1.1] fixes it).
- The `update_project.bat` in v1.0.0 to v1.5.0 differs from the v0.7.0 one (it can create the project on first run and has the old NecroMergerEvents comments); v1.6.0 fixes only those comments.

[v1.6.0]: #v160---2026-10-29
[v1.5.0]: #v150---2026-10-25
[v1.4.0]: #v140---2026-10-25
[v1.3.0]: #v130---2026-10-25
[v1.2.0]: #v120---2026-10-25
[v1.1.3]: #v113---2026-10-24
[v1.1.2]: #v112---2026-10-24
[v1.1.1]: #v111---2026-10-23
[v1.0.0]: #v100---2026-10-23
[v0.6.0]: #v060---2026-10-17
[v0.5.0]: #v050---2026-10-17
[v0.4.0]: #v040---2026-10-17
[v0.3.0]: #v030---2026-10-16
[v0.2.0]: #v020---2026-10-16
[v0.1.0]: #v010---2026-10-15
