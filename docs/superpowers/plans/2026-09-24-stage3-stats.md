# CheckTime — этап 3 (статистика): план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Третья вкладка «Статистика»: выбор периода (день / неделя / месяц с листанием назад, произвольный диапазон), таблица «группа → категории: часы, %» с раскрытием групп и горизонтальная столбчатая диаграмма по категориям.

**Architecture:** Чистые функции периода и агрегации — в `domain` (`StatsPeriod`, `periodRange`, `aggregate`). `StatsViewModel` держит выбранный период, наблюдает сегменты этого диапазона и дерево категорий, отдаёт `StatsUiState`. UI — `StatsScreen` (селектор периода + таблица) и `CategoryBarChart` (Compose `Canvas`, без внешних библиотек). Вкладка добавляется в существующую нижнюю навигацию.

**Tech Stack:** как в этапах 1–2 — Kotlin 2.2.21, Compose BOM 2026.09 (Material 3 `FilterChip`, `DateRangePicker`), Room 2.8.5, Robolectric 4.17.

**Spec:** `docs/superpowers/specs/2026-09-15-checktime-design.md` — §4.1 «Статистика», §8 пункт 3. Уточнение, согласованное с пользователем: периоды календарные (неделя с понедельника, месяц с 1-го числа) со стрелками ‹ › для листания назад; плюс произвольный диапазон.

## Global Constraints

- Пакет `dev.boar.checktime`; `minSdk 26`, `targetSdk 36`, `compileSdk 37.2`. Никаких новых библиотек.
- Статистика ничего не пишет: только чтение сегментов и категорий. Сегменты режутся по границам периода (`clipSegments`), границы периода — локальная полночь.
- Архивные категории учитываются в статистике (их сегменты остаются) — фильтрация архивных только на экране распределения.
- Проценты считаются от суммы расписанного времени за период; при нулевой сумме — экран «за период записей нет».
- Диаграмма: горизонтальные столбики по категориям, отсортированы по убыванию, цвет — цвет самой категории (цвет привязан к сущности, не к рангу), рядом с каждым столбиком подпись «категория» и значение — идентичность никогда не передаётся одним цветом; одна шкала (максимум периода), никаких двойных осей; закруглённые концы 4dp, зазор между столбиками 2dp, оси/сетка не рисуются.
- Видимые строки — в `res/values/strings.xml`; язык русский.
- Тесты: чистые функции — обычный JVM, ViewModel и Compose — Robolectric (`MainDispatcherRule`, `testContainer(context) { now }`, `createComposeRule()` + `@GraphicsMode(GraphicsMode.Mode.NATIVE)`).
- Каждая задача заканчивается зелёными `./gradlew :app:testDebugUnitTest` и `./gradlew :app:assembleDebug` и коммитом (trailer `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`). Команды — из `/home/boar/dev/projects/checktime`.

---

## Карта файлов

```
app/src/main/java/dev/boar/checktime/
  domain/StatsPeriod.kt        НОВЫЙ: StatsPeriod, periodRange, shift, label-данные
  domain/Stats.kt              НОВЫЙ: CategoryStat, GroupStat, StatsResult, aggregate()
  ui/stats/StatsViewModel.kt   НОВЫЙ: StatsUiState, выбор/листание периода
  ui/stats/StatsScreen.kt      НОВЫЙ: селектор периода, таблица, пустое состояние
  ui/stats/CategoryBarChart.kt НОВЫЙ: Canvas-диаграмма
  ui/MainActivity.kt           + вкладка «Статистика» в навигацию
app/src/main/res/values/strings.xml
CLAUDE.md, docs/superpowers/specs/2026-09-15-checktime-design.md
app/src/test/java/dev/boar/checktime/
  domain/StatsPeriodTest.kt, domain/StatsTest.kt
  ui/stats/StatsViewModelTest.kt, ui/stats/StatsScreenTest.kt
```

Порядок: 1 период + агрегация → 2 ViewModel → 3 экран и диаграмма → 4 навигация, документация, проверка на телефоне.

---

### Task 1: Периоды и агрегация (чистые функции)

**Files:**
- Create: `app/src/main/java/dev/boar/checktime/domain/StatsPeriod.kt`, `app/src/main/java/dev/boar/checktime/domain/Stats.kt`
- Test: `app/src/test/java/dev/boar/checktime/domain/StatsPeriodTest.kt`, `app/src/test/java/dev/boar/checktime/domain/StatsTest.kt`

**Interfaces:**
- Consumes: `Segment`, `Category`, `Group`, `GroupWithCategories`, `dayRange`, `clipSegments`, `totalsByCategory`.
- Produces:
  - `sealed interface StatsPeriod { data class Day(date: LocalDate); data class Week(monday: LocalDate); data class Month(firstDay: LocalDate); data class Custom(from: LocalDate, to: LocalDate) }` (`to` включительно)
  - `fun StatsPeriod.range(zone: ZoneId): DayRange`
  - `fun StatsPeriod.shift(days: Long /* -1 назад, +1 вперёд */): StatsPeriod` — для `Custom` сдвигает обе даты на длину диапазона
  - `fun StatsPeriod.firstDate(): LocalDate`, `fun StatsPeriod.lastDate(): LocalDate` (включительно)
  - `fun StatsPeriod.dayCount(): Int`
  - `fun periodOf(kind: PeriodKind, anchor: LocalDate): StatsPeriod`, `enum class PeriodKind { DAY, WEEK, MONTH }`
  - `data class CategoryStat(category: Category, group: Group, millis: Long, share: Float)`
  - `data class GroupStat(group: Group, millis: Long, share: Float, categories: List<CategoryStat>)`
  - `data class StatsResult(totalMillis: Long, groups: List<GroupStat>, categories: List<CategoryStat>)` — `categories` отсортированы по убыванию `millis` (для диаграммы), `groups` тоже по убыванию, внутри группы категории по убыванию
  - `fun aggregate(segments: List<Segment>, tree: List<GroupWithCategories>): StatsResult`

- [ ] **Step 1: Падающие тесты**

`app/src/test/java/dev/boar/checktime/domain/StatsPeriodTest.kt`:
```kotlin
package dev.boar.checktime.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsPeriodTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val wed = LocalDate.of(2026, 9, 16) // среда

    @Test fun dayPeriodIsOneLocalDay() {
        val p = periodOf(PeriodKind.DAY, wed)
        assertEquals(StatsPeriod.Day(wed), p)
        assertEquals(dayRange(wed, zone), p.range(zone))
        assertEquals(1, p.dayCount())
    }

    @Test fun weekStartsOnMonday() {
        val p = periodOf(PeriodKind.WEEK, wed)
        assertEquals(StatsPeriod.Week(LocalDate.of(2026, 9, 14)), p)
        assertEquals(LocalDate.of(2026, 9, 14), p.firstDate())
        assertEquals(LocalDate.of(2026, 9, 20), p.lastDate())
        assertEquals(7, p.dayCount())
        assertEquals(dayRange(LocalDate.of(2026, 9, 14), zone).start, p.range(zone).start)
        assertEquals(dayRange(LocalDate.of(2026, 9, 20), zone).end, p.range(zone).end)
    }

    @Test fun monthStartsOnFirstDay() {
        val p = periodOf(PeriodKind.MONTH, wed)
        assertEquals(StatsPeriod.Month(LocalDate.of(2026, 9, 1)), p)
        assertEquals(LocalDate.of(2026, 9, 30), p.lastDate())
        assertEquals(30, p.dayCount())
    }

    @Test fun shiftMovesWholePeriods() {
        assertEquals(StatsPeriod.Day(wed.minusDays(1)), periodOf(PeriodKind.DAY, wed).shift(-1))
        assertEquals(StatsPeriod.Week(LocalDate.of(2026, 9, 7)), periodOf(PeriodKind.WEEK, wed).shift(-1))
        assertEquals(StatsPeriod.Month(LocalDate.of(2026, 8, 1)), periodOf(PeriodKind.MONTH, wed).shift(-1))
        assertEquals(StatsPeriod.Month(LocalDate.of(2026, 10, 1)), periodOf(PeriodKind.MONTH, wed).shift(1))
    }

    @Test fun customRangeIsInclusiveAndShiftsByItsLength() {
        val p = StatsPeriod.Custom(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12))
        assertEquals(3, p.dayCount())
        assertEquals(dayRange(LocalDate.of(2026, 9, 12), zone).end, p.range(zone).end)
        assertEquals(
            StatsPeriod.Custom(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 9)),
            p.shift(-1),
        )
    }
}
```

`app/src/test/java/dev/boar/checktime/domain/StatsTest.kt`:
```kotlin
package dev.boar.checktime.domain

import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.data.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    private val m = TimeMath.MINUTE_MS
    private val personal = Group(id = 1, name = "Личное", color = 1, sortOrder = 0)
    private val workGroup = Group(id = 2, name = "Работа", color = 2, sortOrder = 1)
    private val sleep = Category(id = 1, groupId = 1, name = "Сон", color = 10, sortOrder = 0)
    private val food = Category(id = 2, groupId = 1, name = "Еда", color = 11, sortOrder = 1)
    private val work = Category(id = 3, groupId = 2, name = "Работа", color = 12, sortOrder = 0)
    private val tree = listOf(
        GroupWithCategories(personal, listOf(sleep, food)),
        GroupWithCategories(workGroup, listOf(work)),
    )

    private fun seg(from: Long, to: Long, category: Long) = Segment(startAt = from * m, endAt = to * m, categoryId = category)

    @Test fun emptyInputGivesZeroTotal() {
        val r = aggregate(emptyList(), tree)
        assertEquals(0L, r.totalMillis)
        assertTrue(r.groups.isEmpty())
        assertTrue(r.categories.isEmpty())
    }

    @Test fun categoriesAreSortedByDurationWithShares() {
        val r = aggregate(listOf(seg(0, 60, sleep.id), seg(60, 90, work.id), seg(90, 100, food.id)), tree)
        assertEquals(100 * m, r.totalMillis)
        assertEquals(listOf("Сон", "Работа", "Еда"), r.categories.map { it.category.name })
        assertEquals(listOf(60 * m, 30 * m, 10 * m), r.categories.map { it.millis })
        assertEquals(0.6f, r.categories[0].share, 0.0001f)
        assertEquals("Личное", r.categories[0].group.name)
    }

    @Test fun groupsSumTheirCategoriesAndSortByDuration() {
        val r = aggregate(listOf(seg(0, 60, sleep.id), seg(60, 90, work.id), seg(90, 100, food.id)), tree)
        assertEquals(listOf("Личное", "Работа"), r.groups.map { it.group.name })
        assertEquals(70 * m, r.groups[0].millis)
        assertEquals(0.7f, r.groups[0].share, 0.0001f)
        assertEquals(listOf("Сон", "Еда"), r.groups[0].categories.map { it.category.name })
    }

    @Test fun categoriesWithoutTimeAreOmitted() {
        val r = aggregate(listOf(seg(0, 10, work.id)), tree)
        assertEquals(listOf("Работа"), r.categories.map { it.category.name })
        assertEquals(listOf("Работа"), r.groups.map { it.group.name })
    }

    @Test fun segmentsOfUnknownCategoryAreIgnoredButNotCounted() {
        val r = aggregate(listOf(seg(0, 10, work.id), seg(10, 20, 999)), tree)
        assertEquals(10 * m, r.totalMillis)
        assertEquals(1, r.categories.size)
    }
}
```

- [ ] **Step 2: Прогон — не компилируется**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.domain.Stats*'`
Expected: ошибки компиляции (`StatsPeriod`, `aggregate` не найдены).

- [ ] **Step 3: `StatsPeriod.kt`**

```kotlin
package dev.boar.checktime.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class PeriodKind { DAY, WEEK, MONTH }

/** Период статистики. Границы — локальные сутки; [Custom.to] включительно. */
sealed interface StatsPeriod {
    data class Day(val date: LocalDate) : StatsPeriod
    data class Week(val monday: LocalDate) : StatsPeriod
    data class Month(val firstDay: LocalDate) : StatsPeriod
    data class Custom(val from: LocalDate, val to: LocalDate) : StatsPeriod
}

fun periodOf(kind: PeriodKind, anchor: LocalDate): StatsPeriod = when (kind) {
    PeriodKind.DAY -> StatsPeriod.Day(anchor)
    PeriodKind.WEEK -> StatsPeriod.Week(anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    PeriodKind.MONTH -> StatsPeriod.Month(anchor.withDayOfMonth(1))
}

fun StatsPeriod.firstDate(): LocalDate = when (this) {
    is StatsPeriod.Day -> date
    is StatsPeriod.Week -> monday
    is StatsPeriod.Month -> firstDay
    is StatsPeriod.Custom -> from
}

fun StatsPeriod.lastDate(): LocalDate = when (this) {
    is StatsPeriod.Day -> date
    is StatsPeriod.Week -> monday.plusDays(6)
    is StatsPeriod.Month -> firstDay.with(TemporalAdjusters.lastDayOfMonth())
    is StatsPeriod.Custom -> to
}

fun StatsPeriod.dayCount(): Int = (lastDate().toEpochDay() - firstDate().toEpochDay() + 1).toInt()

/** [первая локальная полночь периода, полночь после последнего дня). */
fun StatsPeriod.range(zone: ZoneId): DayRange = DayRange(
    start = dayRange(firstDate(), zone).start,
    end = dayRange(lastDate(), zone).end,
)

/** Листание: ±1 период того же вида; для произвольного диапазона — на его длину. */
fun StatsPeriod.shift(periods: Long): StatsPeriod = when (this) {
    is StatsPeriod.Day -> StatsPeriod.Day(date.plusDays(periods))
    is StatsPeriod.Week -> StatsPeriod.Week(monday.plusWeeks(periods))
    is StatsPeriod.Month -> StatsPeriod.Month(firstDay.plusMonths(periods))
    is StatsPeriod.Custom -> {
        val days = dayCount().toLong() * periods
        StatsPeriod.Custom(from.plusDays(days), to.plusDays(days))
    }
}
```

- [ ] **Step 4: `Stats.kt`**

```kotlin
package dev.boar.checktime.domain

import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.data.Segment

data class CategoryStat(val category: Category, val group: Group, val millis: Long, val share: Float)

data class GroupStat(val group: Group, val millis: Long, val share: Float, val categories: List<CategoryStat>)

/** Итоги периода: всё расписанное время, разбивка по группам и плоский список категорий (для диаграммы). */
data class StatsResult(val totalMillis: Long, val groups: List<GroupStat>, val categories: List<CategoryStat>)

/**
 * Считает итоги по уже обрезанным по периоду [segments].
 * Сегменты неизвестных (удалённых) категорий отбрасываются и в сумму не попадают.
 * Категории без времени в списке не показываем.
 */
fun aggregate(segments: List<Segment>, tree: List<GroupWithCategories>): StatsResult {
    val index = tree.flatMap { g -> g.categories.map { it.id to (it to g.group) } }.toMap()
    val byCategory = totalsByCategory(segments).mapNotNull { (id, ms) ->
        index[id]?.let { (category, group) -> Triple(category, group, ms) }
    }
    val total = byCategory.sumOf { it.third }
    if (total == 0L) return StatsResult(0L, emptyList(), emptyList())

    val categories = byCategory
        .map { (category, group, ms) -> CategoryStat(category, group, ms, ms.toFloat() / total) }
        .sortedByDescending { it.millis }

    val groups = categories
        .groupBy { it.group.id }
        .map { (_, stats) ->
            val ms = stats.sumOf { it.millis }
            GroupStat(stats.first().group, ms, ms.toFloat() / total, stats.sortedByDescending { it.millis })
        }
        .sortedByDescending { it.millis }

    return StatsResult(total, groups, categories)
}
```

- [ ] **Step 5: Прогон — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.domain.Stats*'` затем `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS (5 + 5 новых тестов), всё остальное зелёное.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/boar/checktime/domain app/src/test/java/dev/boar/checktime/domain
git commit -m "Add stats periods and aggregation"
```

---

### Task 2: `StatsViewModel`

**Files:**
- Create: `app/src/main/java/dev/boar/checktime/ui/stats/StatsViewModel.kt`
- Test: `app/src/test/java/dev/boar/checktime/ui/stats/StatsViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1 (`StatsPeriod`, `PeriodKind`, `periodOf`, `range`, `shift`, `firstDate`, `lastDate`, `dayCount`, `aggregate`, `StatsResult`), `AppContainer` (`timeline.observeSegments`, `categories.observeTree`, `now`), `clipSegments`.
- Produces:
  - `data class StatsUiState(period: StatsPeriod, kind: PeriodKind?, result: StatsResult, canGoForward: Boolean, dayCount: Int)` — `kind` = null для произвольного диапазона
  - `class StatsViewModel(container: AppContainer, val zone: ZoneId = ZoneId.systemDefault())`: `state: StateFlow<StatsUiState>`, `fun selectKind(kind: PeriodKind)`, `fun selectCustom(from: LocalDate, to: LocalDate)`, `fun previous()`, `fun next()`, `companion fun factory(container)`

- [ ] **Step 1: Падающий тест**

`app/src/test/java/dev/boar/checktime/ui/stats/StatsViewModelTest.kt`:
```kotlin
package dev.boar.checktime.ui.stats

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.boar.checktime.AppContainer
import dev.boar.checktime.MainDispatcherRule
import dev.boar.checktime.domain.Allocation
import dev.boar.checktime.domain.PeriodKind
import dev.boar.checktime.domain.StatsPeriod
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import dev.boar.checktime.domain.dayRange
import dev.boar.checktime.testContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class StatsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 9, 16) // среда
    private val dayStart = dayRange(today, zone).start
    private var now = dayStart + 12 * 60 * MINUTE_MS
    private lateinit var container: AppContainer
    private var sleep = 0L
    private var work = 0L

    @Before fun setUp() = runTest {
        container = testContainer(context) { now }
        val personal = container.categories.addGroup("Личное", 1)
        sleep = container.categories.addCategory(personal, "Сон", 2)
        val job = container.categories.addGroup("Работа", 3)
        work = container.categories.addCategory(job, "Работа", 4)
    }

    @After fun tearDown() = container.db.close()

    private fun vm() = StatsViewModel(container, zone)

    /** 60 мин сна и 30 мин работы сегодня, плюс 20 мин работы вчера. */
    private suspend fun seed() {
        val yesterday = dayRange(today.minusDays(1), zone).start + 10 * 60 * MINUTE_MS
        container.timeline.startTracking(yesterday)
        container.timeline.allocate(yesterday, listOf(Allocation(work, 20)))
        val todayStart = dayStart + 8 * 60 * MINUTE_MS
        container.db.segmentDao().insertAll(
            listOf(
                dev.boar.checktime.data.Segment(startAt = todayStart, endAt = todayStart + 60 * MINUTE_MS, categoryId = sleep),
                dev.boar.checktime.data.Segment(startAt = todayStart + 60 * MINUTE_MS, endAt = todayStart + 90 * MINUTE_MS, categoryId = work),
            ),
        )
    }

    @Test fun defaultPeriodIsToday() = runTest {
        seed()
        val s = vm().state.first { it.result.totalMillis > 0 }
        assertEquals(StatsPeriod.Day(today), s.period)
        assertEquals(PeriodKind.DAY, s.kind)
        assertEquals(90 * MINUTE_MS, s.result.totalMillis)
        assertEquals(listOf("Сон", "Работа"), s.result.categories.map { it.category.name })
        assertFalse(s.canGoForward)
        assertEquals(1, s.dayCount)
    }

    @Test fun weekIncludesYesterday() = runTest {
        seed()
        val vm = vm()
        vm.state.first { it.result.totalMillis > 0 }
        vm.selectKind(PeriodKind.WEEK)
        val s = vm.state.first { it.kind == PeriodKind.WEEK && it.result.totalMillis > 0 }
        assertEquals(StatsPeriod.Week(LocalDate.of(2026, 9, 14)), s.period)
        assertEquals(110 * MINUTE_MS, s.result.totalMillis)
        assertEquals(7, s.dayCount)
    }

    @Test fun previousDayShowsItsOwnDataAndAllowsGoingForward() = runTest {
        seed()
        val vm = vm()
        vm.state.first { it.result.totalMillis > 0 }
        vm.previous()
        val s = vm.state.first { it.period == StatsPeriod.Day(today.minusDays(1)) }
        assertEquals(20 * MINUTE_MS, s.result.totalMillis)
        assertTrue(s.canGoForward)
        vm.next()
        assertEquals(StatsPeriod.Day(today), vm.state.first { it.period == StatsPeriod.Day(today) }.period)
    }

    @Test fun nextIsIgnoredWhenPeriodAlreadyCoversToday() = runTest {
        seed()
        val vm = vm()
        vm.state.first { it.result.totalMillis > 0 }
        vm.next()
        assertEquals(StatsPeriod.Day(today), vm.state.value.period)
    }

    @Test fun customRangeAggregatesBothDays() = runTest {
        seed()
        val vm = vm()
        vm.state.first { it.result.totalMillis > 0 }
        vm.selectCustom(today.minusDays(1), today)
        val s = vm.state.first { it.kind == null }
        assertEquals(StatsPeriod.Custom(today.minusDays(1), today), s.period)
        assertEquals(110 * MINUTE_MS, s.result.totalMillis)
        assertEquals(2, s.dayCount)
    }
}
```

- [ ] **Step 2: Прогон — не компилируется**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.ui.stats.*'`
Expected: ошибка компиляции.

- [ ] **Step 3: Реализация**

`app/src/main/java/dev/boar/checktime/ui/stats/StatsViewModel.kt`:
```kotlin
package dev.boar.checktime.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.boar.checktime.AppContainer
import dev.boar.checktime.domain.PeriodKind
import dev.boar.checktime.domain.StatsPeriod
import dev.boar.checktime.domain.StatsResult
import dev.boar.checktime.domain.aggregate
import dev.boar.checktime.domain.clipSegments
import dev.boar.checktime.domain.dayCount
import dev.boar.checktime.domain.firstDate
import dev.boar.checktime.domain.lastDate
import dev.boar.checktime.domain.periodOf
import dev.boar.checktime.domain.range
import dev.boar.checktime.domain.shift
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StatsUiState(
    val period: StatsPeriod,
    /** null — произвольный диапазон. */
    val kind: PeriodKind?,
    val result: StatsResult,
    val canGoForward: Boolean,
    val dayCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val container: AppContainer,
    val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private data class Selection(val period: StatsPeriod, val kind: PeriodKind?)

    private val selection = MutableStateFlow(Selection(StatsPeriod.Day(currentDate()), PeriodKind.DAY))

    private val periodSegments = selection.flatMapLatest { sel ->
        val range = sel.period.range(zone)
        container.timeline.observeSegments(range.start, range.end)
            .map { sel to clipSegments(it, range.start, range.end) }
    }

    val state: StateFlow<StatsUiState> = combine(
        periodSegments,
        container.categories.observeTree(),
    ) { (sel, segments), tree ->
        StatsUiState(
            period = sel.period,
            kind = sel.kind,
            result = aggregate(segments, tree),
            canGoForward = sel.period.lastDate() < currentDate(),
            dayCount = sel.period.dayCount(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyState())

    fun selectKind(kind: PeriodKind) = selection.update { Selection(periodOf(kind, currentDate()), kind) }

    fun selectCustom(from: LocalDate, to: LocalDate) =
        selection.update { Selection(StatsPeriod.Custom(minOf(from, to), maxOf(from, to)), null) }

    fun previous() = selection.update { it.copy(period = it.period.shift(-1)) }

    /** Вперёд только пока период не накрыл сегодняшний день. */
    fun next() = selection.update {
        if (it.period.lastDate() < currentDate()) it.copy(period = it.period.shift(1)) else it
    }

    private fun currentDate(): LocalDate = Instant.ofEpochMilli(container.now()).atZone(zone).toLocalDate()

    private fun emptyState() = StatsUiState(
        period = selection.value.period,
        kind = selection.value.kind,
        result = StatsResult(0L, emptyList(), emptyList()),
        canGoForward = false,
        dayCount = selection.value.period.dayCount(),
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { StatsViewModel(container) }
        }
    }
}
```
Замечание: `firstDate` импортируется для будущего использования в UI — если компилятор ругается на неиспользуемый импорт (это лишь предупреждение), убрать его.

- [ ] **Step 4: Прогон — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.ui.stats.*'` затем полный `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS (5 новых тестов).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/boar/checktime/ui/stats app/src/test/java/dev/boar/checktime/ui/stats
git commit -m "Add StatsViewModel with calendar periods"
```

---

### Task 3: Экран статистики и диаграмма

**Files:**
- Create: `app/src/main/java/dev/boar/checktime/ui/stats/CategoryBarChart.kt`, `app/src/main/java/dev/boar/checktime/ui/stats/StatsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/dev/boar/checktime/ui/stats/StatsScreenTest.kt`

**Interfaces:**
- Consumes: `StatsUiState`, `StatsResult`, `CategoryStat`, `GroupStat`, `PeriodKind`, `StatsPeriod`, `firstDate`, `lastDate`, `formatDurationMs`.
- Produces:
  - `@Composable fun CategoryBarChart(categories: List<CategoryStat>, modifier: Modifier = Modifier)`
  - `@Composable fun StatsScreen(state: StatsUiState, onSelectKind: (PeriodKind) -> Unit, onSelectCustom: (LocalDate, LocalDate) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit)`
  - `@Composable fun StatsRoute(viewModel: StatsViewModel)`

- [ ] **Step 1: Падающий тест**

`app/src/test/java/dev/boar/checktime/ui/stats/StatsScreenTest.kt`:
```kotlin
package dev.boar.checktime.ui.stats

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.domain.CategoryStat
import dev.boar.checktime.domain.GroupStat
import dev.boar.checktime.domain.PeriodKind
import dev.boar.checktime.domain.StatsPeriod
import dev.boar.checktime.domain.StatsResult
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val personal = Group(id = 1, name = "Личное", color = 0xFF43A047.toInt(), sortOrder = 0)
    private val sleep = Category(id = 1, groupId = 1, name = "Сон", color = 0xFF00ACC1.toInt(), sortOrder = 0)
    private val food = Category(id = 2, groupId = 1, name = "Еда", color = 0xFF1E88E5.toInt(), sortOrder = 1)
    private val sleepStat = CategoryStat(sleep, personal, 60 * MINUTE_MS, 0.75f)
    private val foodStat = CategoryStat(food, personal, 20 * MINUTE_MS, 0.25f)
    private val result = StatsResult(
        totalMillis = 80 * MINUTE_MS,
        groups = listOf(GroupStat(personal, 80 * MINUTE_MS, 1f, listOf(sleepStat, foodStat))),
        categories = listOf(sleepStat, foodStat),
    )

    private fun state(result: StatsResult = this.result, canGoForward: Boolean = false) = StatsUiState(
        period = StatsPeriod.Day(LocalDate.of(2026, 9, 16)),
        kind = PeriodKind.DAY,
        result = result,
        canGoForward = canGoForward,
        dayCount = 1,
    )

    @Test fun showsTotalGroupsAndChart() {
        compose.setContent { StatsScreen(state(), onSelectKind = {}, onSelectCustom = { _, _ -> }, onPrevious = {}, onNext = {}) }
        compose.onNodeWithText("Всего: 1 ч 20 мин").assertExists()
        compose.onNodeWithText("Личное").assertExists()
        compose.onNodeWithText("100%").assertExists()
        compose.onNodeWithTag("stats-chart").assertExists()
    }

    @Test fun groupExpandsToCategories() {
        compose.setContent { StatsScreen(state(), onSelectKind = {}, onSelectCustom = { _, _ -> }, onPrevious = {}, onNext = {}) }
        // «Сон» уже есть один раз — как подпись столбика диаграммы; раскрытие группы добавляет строку таблицы.
        assertEquals(1, compose.onAllNodesWithText("Сон").fetchSemanticsNodes().size)
        compose.onNodeWithText("Личное").performClick()
        assertEquals(2, compose.onAllNodesWithText("Сон").fetchSemanticsNodes().size)
        compose.onNodeWithText("75%").assertExists()
    }

    @Test fun periodChipsAndArrowsCallBack() {
        var kind: PeriodKind? = null
        var prev = 0
        compose.setContent {
            StatsScreen(state(canGoForward = false), onSelectKind = { kind = it }, onSelectCustom = { _, _ -> }, onPrevious = { prev++ }, onNext = {})
        }
        compose.onNodeWithText("Неделя").performClick()
        assertEquals(PeriodKind.WEEK, kind)
        compose.onNodeWithTag("stats-prev").performClick()
        assertEquals(1, prev)
        compose.onNodeWithTag("stats-next").assertIsNotEnabled()
    }

    @Test fun forwardArrowEnabledForPastPeriods() {
        compose.setContent { StatsScreen(state(canGoForward = true), onSelectKind = {}, onSelectCustom = { _, _ -> }, onPrevious = {}, onNext = {}) }
        compose.onNodeWithTag("stats-next").assertIsEnabled()
    }

    @Test fun emptyPeriodShowsHint() {
        val empty = StatsResult(0L, emptyList(), emptyList())
        compose.setContent { StatsScreen(state(empty), onSelectKind = {}, onSelectCustom = { _, _ -> }, onPrevious = {}, onNext = {}) }
        compose.onNodeWithText("За этот период записей нет").assertExists()
        compose.onNodeWithTag("stats-chart").assertDoesNotExist()
    }
}
```
`assertDoesNotExist`/`assertExists` — методы `SemanticsNodeInteraction`, отдельного импорта не требуют.

- [ ] **Step 2: Прогон — не компилируется**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.ui.stats.StatsScreenTest'`
Expected: ошибка компиляции.

- [ ] **Step 3: Диаграмма**

`app/src/main/java/dev/boar/checktime/ui/stats/CategoryBarChart.kt`:
```kotlin
package dev.boar.checktime.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.boar.checktime.domain.CategoryStat
import dev.boar.checktime.ui.common.formatDurationMs

/**
 * Горизонтальные столбики по категориям, отсортированным по убыванию.
 * Цвет — цвет категории (привязан к сущности, не к рангу); рядом всегда есть название и значение,
 * поэтому идентичность не передаётся одним цветом. Одна шкала — максимум периода; осей и сетки нет.
 */
@Composable
fun CategoryBarChart(categories: List<CategoryStat>, modifier: Modifier = Modifier) {
    if (categories.isEmpty()) return
    val max = categories.maxOf { it.millis }.coerceAtLeast(1L)
    Column(modifier) {
        categories.forEach { stat ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    stat.category.name,
                    Modifier.weight(0.45f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Canvas(Modifier.weight(0.55f).height(18.dp)) {
                    val width = size.width * (stat.millis.toFloat() / max)
                    if (width > 0f) {
                        drawRoundRect(
                            color = Color(stat.category.color),
                            topLeft = Offset(0f, size.height / 4f),
                            size = Size(width.coerceAtLeast(4.dp.toPx()), size.height / 2f),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                    }
                }
            }
            Text(
                formatDurationMs(stat.millis),
                Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
```

- [ ] **Step 4: Экран**

`app/src/main/java/dev/boar/checktime/ui/stats/StatsScreen.kt`:
```kotlin
package dev.boar.checktime.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.boar.checktime.R
import dev.boar.checktime.domain.PeriodKind
import dev.boar.checktime.domain.StatsPeriod
import dev.boar.checktime.domain.firstDate
import dev.boar.checktime.domain.lastDate
import dev.boar.checktime.ui.common.formatDurationMs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
private val shortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))

@Composable
fun StatsRoute(viewModel: StatsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsScreen(
        state = state,
        onSelectKind = viewModel::selectKind,
        onSelectCustom = viewModel::selectCustom,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
    )
}

@Composable
fun StatsScreen(
    state: StatsUiState,
    onSelectKind: (PeriodKind) -> Unit,
    onSelectCustom: (LocalDate, LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    Column(Modifier.fillMaxSize()) {
        PeriodChips(state.kind, onSelectKind, onOpenPicker = { showPicker = true })
        PeriodHeader(state, onPrevious, onNext)
        HorizontalDivider()

        if (state.result.totalMillis == 0L) {
            Text(
                stringResource(R.string.stats_empty),
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Text(
                        stringResource(R.string.stats_total, formatDurationMs(state.result.totalMillis)),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                state.result.groups.forEach { group ->
                    item(key = "g${group.group.id}") {
                        val open = expanded[group.group.id] == true
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { expanded[group.group.id] = !open },
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(group.group.color)))
                                Text(
                                    group.group.name,
                                    Modifier.weight(1f).padding(start = 12.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(formatDurationMs(group.millis), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    percent(group.share),
                                    Modifier.width(48.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            if (open) {
                                group.categories.forEach { stat ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 40.dp, end = 16.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(stat.category.color)))
                                        Text(
                                            stat.category.name,
                                            Modifier.weight(1f).padding(start = 12.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(formatDurationMs(stat.millis), style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            percent(stat.share),
                                            Modifier.width(48.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item {
                    Text(
                        stringResource(R.string.stats_by_category),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item {
                    CategoryBarChart(
                        state.result.categories,
                        Modifier.padding(horizontal = 16.dp).testTag("stats-chart"),
                    )
                }
            }
        }
    }

    if (showPicker) {
        CustomRangeDialog(
            onDismiss = { showPicker = false },
            onConfirm = { from, to ->
                onSelectCustom(from, to)
                showPicker = false
            },
        )
    }
}

@Composable
private fun PeriodChips(kind: PeriodKind?, onSelectKind: (PeriodKind) -> Unit, onOpenPicker: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = kind == PeriodKind.DAY, onClick = { onSelectKind(PeriodKind.DAY) }, label = { Text(stringResource(R.string.stats_day)) })
        FilterChip(selected = kind == PeriodKind.WEEK, onClick = { onSelectKind(PeriodKind.WEEK) }, label = { Text(stringResource(R.string.stats_week)) })
        FilterChip(selected = kind == PeriodKind.MONTH, onClick = { onSelectKind(PeriodKind.MONTH) }, label = { Text(stringResource(R.string.stats_month)) })
        FilterChip(selected = kind == null, onClick = onOpenPicker, label = { Text(stringResource(R.string.stats_custom)) })
    }
}

@Composable
private fun PeriodHeader(state: StatsUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.testTag("stats-prev")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.stats_previous))
        }
        Text(
            periodTitle(state.period),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onNext, enabled = state.canGoForward, modifier = Modifier.testTag("stats-next")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.stats_next))
        }
    }
}

private fun periodTitle(period: StatsPeriod): String = when (period) {
    is StatsPeriod.Day -> period.date.format(dayFormatter)
    else -> "${period.firstDate().format(shortFormatter)} – ${period.lastDate().format(shortFormatter)}"
}

private fun percent(share: Float): String = "${(share * 100).roundToInt()}%"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate) -> Unit) {
    val pickerState = rememberDateRangePickerState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val from = pickerState.selectedStartDateMillis?.toLocalDateUtc()
                    val to = pickerState.selectedEndDateMillis?.toLocalDateUtc() ?: from
                    if (from != null && to != null) onConfirm(from, to)
                },
                enabled = pickerState.selectedStartDateMillis != null,
            ) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        text = { DateRangePicker(state = pickerState) },
    )
}

/** DateRangePicker отдаёт полночь UTC — переводим в дату без сдвига часовым поясом. */
private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.of("UTC")).toLocalDate()
```

- [ ] **Step 5: Строки**

Добавить в `strings.xml`:
```xml
    <string name="tab_stats">Статистика</string>
    <string name="stats_day">День</string>
    <string name="stats_week">Неделя</string>
    <string name="stats_month">Месяц</string>
    <string name="stats_custom">Период</string>
    <string name="stats_previous">Предыдущий период</string>
    <string name="stats_next">Следующий период</string>
    <string name="stats_total">Всего: %1$s</string>
    <string name="stats_by_category">По категориям</string>
    <string name="stats_empty">За этот период записей нет</string>
```

- [ ] **Step 6: Прогон — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.ui.stats.*'` затем `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS (5 новых тестов экрана). Если `DateRangePicker` требует другого API в этой версии Compose — использовать эквивалент и записать отклонение в отчёт.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/boar/checktime/ui/stats app/src/main/res/values/strings.xml app/src/test/java/dev/boar/checktime/ui/stats
git commit -m "Add stats screen with period selector, table and bar chart"
```

---

### Task 4: Вкладка в навигации, документация, проверка на телефоне

**Files:**
- Modify: `app/src/main/java/dev/boar/checktime/ui/MainActivity.kt`, `CLAUDE.md`, `docs/superpowers/specs/2026-09-15-checktime-design.md`
- Test: `app/src/test/java/dev/boar/checktime/ui/MainActivityTest.kt`

**Interfaces:**
- Consumes: `StatsRoute`, `StatsViewModel.factory(container)`.
- Produces: вкладка «Статистика» между «День» и «Настройки».

- [ ] **Step 1: Падающий тест**

Добавить в `MainActivityTest`:
```kotlin
    @Test fun statsTabIsReachable() {
        waitForText("Учёт не начат — откройте «Настройки»")
        compose.onNodeWithText("Статистика").performClick()
        waitForText("За этот период записей нет")
        compose.onNodeWithText("День").performClick()
        waitForText("Учёт не начат — откройте «Настройки»")
    }
```

- [ ] **Step 2: Прогон — падает**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.ui.MainActivityTest'`
Expected: FAIL — узла «Статистика» нет.

- [ ] **Step 3: Навигация**

В `MainActivity.kt`:
1. В `Routes` добавить `const val STATS = "stats"`.
2. В `MainActivity` добавить поле:
```kotlin
    private val statsViewModel: StatsViewModel by viewModels { StatsViewModel.factory(appContainer) }
```
   и передать его в `MainScreen(dayViewModel, statsViewModel, settingsViewModel, categoriesViewModel)`; в сигнатуру `MainScreen` добавить параметр `statsViewModel: StatsViewModel` вторым.
3. `showBar` дополнить: `route == Routes.DAY || route == Routes.STATS || route == Routes.SETTINGS`.
4. В `NavigationBar` между «День» и «Настройки»:
```kotlin
                    NavigationBarItem(
                        selected = route == Routes.STATS,
                        onClick = { navController.switchTab(Routes.STATS) },
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_stats)) },
                    )
```
   (импорт `androidx.compose.material.icons.filled.Info`).
5. В `NavHost` добавить `composable(Routes.STATS) { StatsRoute(statsViewModel) }` (импорты `dev.boar.checktime.ui.stats.StatsRoute`, `dev.boar.checktime.ui.stats.StatsViewModel`).

- [ ] **Step 4: Прогон**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug && ./gradlew :app:lint`
Expected: все тесты PASS; lint без новых ошибок (две ошибки `LocalContextGetResourceValueCall` известны).

- [ ] **Step 5: Документация**

- `CLAUDE.md`, bullet `ui/`: заменить «Bottom nav: Day · Settings (Stats is stage 3)» на «Bottom nav: Day · Stats · Settings»; добавить, что статистика только читает данные (`StatsViewModel` + чистые `aggregate`/`periodOf` в `domain`).
- Спека §4.1 «Статистика»: зафиксировать реализованное — периоды календарные (день / неделя с понедельника / месяц с 1-го числа) с листанием ‹ ›, «вперёд» недоступно, когда период уже накрывает сегодня; произвольный диапазон через `DateRangePicker`; таблица «группа → категории: часы, %» с раскрытием группы по тапу; горизонтальная столбчатая диаграмма по категориям (цвет категории, подписи названием и значением, одна шкала).

- [ ] **Step 6: Проверка на телефоне**

`./gradlew :app:installDebug` (если `adb devices` показывает device), затем: вкладка «Статистика» открывается на сегодняшнем дне; «Неделя»/«Месяц» меняют заголовок и цифры; ‹ листает назад, › недоступна на текущем периоде и активна на прошлом; «Период» открывает календарь и применяет выбранный диапазон; тап по группе раскрывает категории; диаграмма рисуется, сумма процентов ≈ 100%.

- [ ] **Step 7: Commit**

```bash
git add app/src/main app/src/test CLAUDE.md docs/superpowers/specs
git commit -m "Add stats tab to the bottom navigation"
```
