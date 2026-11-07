# CheckTime — этап 4 (цели на неделю): план реализации

**Goal:** Вкладка «Цели»: постоянные недельные цели «не меньше X» на группу или
категорию, прогресс по текущей и прошлым неделям, добавление/изменение/удаление
в текущей неделе.

**Architecture:** Таблица `weekly_goals` с историей по `validFrom` (понедельник,
epochDay); миграция 1 → 2 через Room `AutoMigration`. `GoalRepository` — единственное
место записи целей (одна транзакция на изменение). Чистые функции в `domain/WeeklyGoals.kt`:
`activeGoals` (действующие на неделю цели) и `goalProgress` (прогресс по обрезанным
сегментам и дереву категорий). `GoalsViewModel` повторяет схему `StatsViewModel`
(выбранная неделя → сегменты недели + цели + дерево + тикер). UI — `GoalsScreen` с
листанием недель, списком с полосами прогресса и диалогом цели.

**Spec:** `docs/superpowers/specs/2026-09-15-checktime-design.md` — §2.1.1, §4.1 «Цели», §8 п. 4.
Согласовано с пользователем: объект — группа или категория; только минимум;
цели постоянные; отдельная вкладка.

## Global Constraints

- Никаких новых библиотек. Миграционный тест — без `room-testing`: БД v1 создаётся
  SQL-запросами из `app/schemas/.../1.json`, затем открывается Room'ом v2.
- Изменение/удаление цели действует с текущего понедельника; прошлые недели не меняются.
  Снятие цели, созданной на этой же неделе, просто удаляет запись; иначе пишется
  запись с `minutes = 0`.
- Прогресс группы — сумма всех её категорий, включая архивные. Хвост не учитывается.
- Строки — в `strings.xml`, русский.

## Задачи

1. **Данные.** `WeeklyGoal` (FK на `categories`/`groups`, `onDelete = CASCADE`), `GoalDao`,
   `AppDatabase` v2 + `AutoMigration(1, 2)`, `GoalRepository.set`, `AppContainer.goals`.
   Тесты: `GoalRepositoryTest` (замена в пределах недели, снятие с историей и без, каскад),
   `MigrationTest` (данные v1 переживают миграцию, таблица целей доступна).
2. **Домен.** `GoalTarget`, `activeGoals`, `GoalProgress`, `goalProgress`. Тест `WeeklyGoalsTest`.
3. **ViewModel.** `GoalsUiState`, `GoalsViewModel` (неделя, листание, кандидаты, `setGoal`,
   `removeGoal`). Тест `GoalsViewModelTest`.
4. **UI.** `GoalsScreen` + диалог цели, вкладка в `MainActivity`, строки. Тесты
   `GoalsScreenTest`, `MainActivityTest.goalsTabIsReachable`.
5. **Финал.** `CLAUDE.md`, версия 0.4, `testDebugUnitTest`, `assembleDebug`, `lint`.
