# CheckTime

[Русская версия](README.ru.md)

An Android time tracker that accounts for all 24 hours of the day. Every N minutes
a screen pops up over other apps and asks where the time went, and you split it
between your categories. You can postpone it: unlogged time accumulates into a
single tail that is logged in one go next time, so there are never any gaps.

The UI is available in English and Russian and follows the system language
(on Android 13+ it can also be set per app in system settings).

## Features

- **Logging popup**: one slider per category. The slider snaps to the step
  set in Settings and stops at the remaining time. Tapping a category name gives
  it all the remaining time. The back gesture postpones it.
- **Day**: the day's records and per-category totals. Tap a record to change its
  category, split it, move the boundary with a neighbour, or merge them.
- **Stats**: day, week, month or a custom range, shown as a "group → categories"
  table and a bar chart.
- **Goals**: a weekly minimum for a group or a category, with a progress bar.
  A goal stays in effect until you change it; past weeks keep the goals they had.
- **Settings**: interval, postpone time, slider step, the group and category
  editor, and a permissions checklist.

The popup never wakes the screen. The alarm posts an "Unlogged: X min"
notification, and the screen opens after you unlock the phone.

## Permissions

- "Display over other apps" is required. Without it there is no popup, only the
  notification.
- Notifications (Android 13+), exact alarms, and disabling battery optimization.

All of them are listed in the checklist on the Settings screen.

## Building

Requires JDK 21 and the Android SDK (compileSdk 37.2). minSdk 26, targetSdk 36.

```sh
./gradlew :app:assembleDebug        # build
./gradlew :app:installDebug         # install on a connected phone
./gradlew :app:testDebugUnitTest    # tests (Robolectric + in-memory Room)
```

The JDK path is set in `gradle.properties` (`org.gradle.java.home`); change it if
your JDK lives elsewhere. The SDK path goes in `local.properties` (`sdk.dir=...`).

## Under the hood

Kotlin, Jetpack Compose (Material 3), Room, DataStore, manual DI without Hilt.
The detailed behaviour is described in the spec
[`docs/superpowers/specs/2026-09-15-checktime-design.md`](docs/superpowers/specs/2026-09-15-checktime-design.md)
(in Russian); development notes are in [`CLAUDE.md`](CLAUDE.md).
