# Этап 1 — отложенные мелкие замечания ревью

Собраны из пер-задачных и финального ревью; не блокируют этап 1, кандидаты на этап 2/3.

- Task 2: minor (deferred): FKs default to NO_ACTION — deleteGroup/deleteCategory rely on CategoryRepository (Task 6) checking counts first; no FK test.
- Task 3: minor (deferred): clamp test covers only two of six boundaries.
- Task 5: minor (deferred): no test for allocate() with non-positive minutes (require → IllegalArgumentException; callers only pass draft.allocations() which filters zeros).
- Task 6: minor (deferred): move* untested for delta≠±1 / unknown id; updateGroup/updateCategory passthroughs untested.
- Task 7: minor (deferred): platform Notification.Builder instead of NotificationCompat; request code 0 for both channels' content intent.
- Task 8: minor (deferred): RepeatButton has role=Button but no onClick semantics (TalkBack); no isSaving guard on «Готово» (double-tap → spurious Stale toast, no data harm).
- Task 9: minor (deferred): no debounce on per-keystroke commits (DataStore write + alarm re-arm per digit).
- Task 9: minor (deferred): on blur MinutesField may briefly show a stale value until the DataStore emission arrives.
- Task 10: minor (deferred): no confirmation before deleting empty group/category; no happy-path delete test.
- Task 11: minor (deferred): nextDay() not clamped in VM (only UI disables); totals drop unknown categoryId while rows show «Удалённая категория»; DayScreen formatTime uses system zone rather than VM zone.
- Task 12: minor (deferred): createAndroidComposeRule deprecation (v2 import) — migrate repo-wide later; inline FQN NavHostController in switchTab.
- Task 13: minor (deferred): service test polls looper (bounded 500 ms); «Готово» disabled without explanation when no categories.

Из финального ревью (не взяты в fix wave): DataStore без corruptionHandler — сделано; ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED не обрабатывается (Android 12, закрыто перестановкой будильника в onResume); DayViewModel захватывает ZoneId при создании; seedDefaultsIfEmpty вне транзакции.
