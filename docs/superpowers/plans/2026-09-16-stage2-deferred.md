# Этап 2 — отложенные мелкие замечания ревью

- Task 3: minor (deferred): rememberTimePickerState keyed by slot only (fine for current usage); DST-gap behaviour of pickerToEpoch untested.
- Task 4: minor (deferred): lint LocalContextGetResourceValueCall in DayRoute (and pre-existing in CategoriesScreen) — context.getString inside LaunchedEffect; midpoint floors to minute.

Из финального ревью: DST-gap в pickerToEpoch без теста; lint LocalContextGetResourceValueCall в DayRoute и CategoriesRoute (context.getString внутри LaunchedEffect) — чинить вместе.
