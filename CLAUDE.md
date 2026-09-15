# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Android time tracker (Kotlin + Jetpack Compose). Every N minutes a full-screen
`AllocationActivity` pops up over other apps and asks the user to split the
unaccounted time since the last allocation between categories. The whole day
must be accounted for — there is one "unaccounted tail" `[accountedUntil, now)`,
never gaps. The design spec lives in `docs/superpowers/specs/` and is the
source of truth for behaviour; update it when behaviour changes.

Communicate with the user in Russian. UI strings are Russian, in `strings.xml`.

## Toolchain

- Android SDK at `~/Android/Sdk` (platform 36 + 37.2, build-tools 36.0.0); `local.properties`
  points there and is gitignored. `/opt/android-sdk` is root-owned — don't use it.
- Build JDK is pinned to `/usr/lib/jvm/java-21-openjdk` via `org.gradle.java.home` in
  `gradle.properties`; no need to export `JAVA_HOME`. The system default JDK 26 does not work.
- Gradle 9.6.0 wrapper, AGP 9.4.0 with built-in Kotlin 2.2.21 (no `kotlin-android` plugin;
  `org.jetbrains.kotlin.plugin.compose` and KSP are applied). `compileSdk 37.2` because
  Compose BOM 2026.09 requires it; `targetSdk 36`.
- Robolectric on JDK 21 needs the `--add-opens/--add-exports` jvmArgs already in `app/build.gradle.kts`.
- Test device is a physical phone on Android 16 (`adb devices`); there is no emulator.

## Commands

```sh
./gradlew :app:assembleDebug              # build
./gradlew :app:installDebug               # install on the connected phone
./gradlew :app:testDebugUnitTest          # JVM unit tests (Robolectric + in-memory Room)
./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.domain.TimelineRepositoryTest'
./gradlew :app:testDebugUnitTest --tests '*TimelineRepositoryTest.allocate*'   # single test
./gradlew :app:connectedDebugAndroidTest  # instrumented tests on the phone
./gradlew :app:lint
adb shell monkey -p dev.boar.checktime -c android.intent.category.LAUNCHER 1   # launch the app
```

## Architecture

Single module `app`, package `dev.boar.checktime`. Manual DI via `AppContainer`
in the `Application` class — no Hilt.

- `data/` — Room entities/DAOs (`Group`, `Category`, `Segment`, and a `tracking_state`
  table holding `trackingStart`/`accountedUntil`), `AppDatabase`,
  `SettingsRepository` (DataStore: only `intervalMinutes`, `snoozeMinutes`, `stepMinutes`).
- `domain/` — `TimelineRepository` is the **only** code that writes segments or
  moves `accountedUntil`. It owns the invariants: segments never overlap and
  cover `[trackingStart, accountedUntil)` with no gaps. Operations (`allocate`,
  `changeCategory`, `moveBoundary`, `split`, `merge`) are single Room
  transactions; `allocate` re-checks `accountedUntil` inside the transaction
  so a stale popup cannot double-write. Pure stats/day-slicing functions live
  here too.
- `scheduler/` — `AlarmScheduler` keeps exactly one exact alarm
  (`accountedUntil + N` after a save, `now + snooze` after a postpone).
  `AlarmReceiver` posts the ongoing "Не расписано: X мин" notification
  (with `fullScreenIntent`) and directly starts `AllocationActivity` — this
  relies on the user having granted "display over other apps"
  (`SYSTEM_ALERT_WINDOW`), which is what exempts background activity starts.
  `BootReceiver` re-arms the alarm.
- `ui/` — Compose screens + ViewModels. Bottom nav: Day · Settings (Stats is stage 3)
  (settings includes the group/category editor and the permissions checklist).
  `AllocationActivity` is a separate `singleTask`, `showWhenLocked` activity,
  not part of the nav graph; back gesture = postpone.

Testing note: Robolectric picks up `TestCheckTimeApp` (in `app/src/test`) as the
`Application` class instead of the real one, which wires `MainActivity` to an
in-memory Room DB — this is why JVM unit tests can drive `MainActivity` directly
without touching a real database.

Time semantics worth remembering:

- Segments are stored as UTC epoch millis; day views cut at local midnight.
- Allocation works in whole minutes: `total = floor((end - accountedUntil)/60s)`,
  `end` is frozen when the popup opens; the sub-minute remainder rolls into the
  next tail. A tail < 1 minute never shows a popup.
- Segments are ordered within an allocation (written in list order) so a
  sequential "first X minutes → category" entry mode can be added later
  without changing storage.
- Time is assigned to categories only, never to groups. Categories with
  segments can only be archived, not deleted.
