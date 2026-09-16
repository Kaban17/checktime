# CheckTime — этап 2 (правка прошлого): план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** На экране «День» по тапу на запись открывается шторка, в которой можно сменить категорию, разбить запись, сдвинуть границу с соседом и слить с соседом — так, чтобы инвариант «без дыр и пересечений» не нарушался.

**Architecture:** Четыре операции добавляются в `TimelineRepository` как транзакции Room с проверками смежности и диапазона (`EditResult.Done | Rejected`). `DayViewModel` получает состояние редактора (`EditorState`: запись, соседи, категории) и методы-действия; `DayScreen` — нижнюю шторку `SegmentEditSheet` и контрол времени `TimeStepperField` (HH:mm с кнопками ±шаг из настроек и `TimePicker` по тапу). Строки дня несут `id` исходной записи, поэтому обрезанная по полуночи строка редактирует настоящую запись.

**Tech Stack:** как в этапе 1 — Kotlin 2.2.21, Compose BOM 2026.09 (Material 3 `ModalBottomSheet`, `TimePicker`), Room 2.8.5, Robolectric 4.17.

**Spec:** `docs/superpowers/specs/2026-09-15-checktime-design.md` — §2.4 (операции), §4.1 «День» (шторка), §8 пункт 2. Уточнения, принятые с пользователем: время выбирается контролом «HH:mm ± шаг» + `TimePicker` по тапу; слить можно с любым соседом, категория текущей записи побеждает.

## Global Constraints

- Пакет `dev.boar.checktime`; `minSdk 26`, `targetSdk 36`, `compileSdk 37.2`. Никаких новых библиотек.
- `TimelineRepository` — единственное место записи сегментов. Все операции правки — `db.withTransaction`, перечитывают записи по id внутри транзакции и отклоняют (`Rejected`) несмежные/невалидные запросы; UI никогда не пишет в DAO напрямую.
- Инвариант: после любой операции сегменты не пересекаются и покрывают `[trackingStart, accountedUntil)` без дыр. Начало первой записи и конец последней (`accountedUntil`) не двигаются (нет соседа).
- Границы времени — целые минуты (контрол шагает минутами; `TimePicker` даёт HH:mm).
- Видимые строки — в `res/values/strings.xml`; язык русский.
- Robolectric 4.17: `peekNextScheduledAlarm()!!`; `TestCheckTimeApp` даёт in-memory контейнер; тесты ViewModel — с `MainDispatcherRule` и `testContainer(context) { now }`.
- Каждая задача заканчивается зелёными `./gradlew :app:testDebugUnitTest` и `./gradlew :app:assembleDebug` и коммитом (trailer `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`). Все команды — из `/home/boar/dev/projects/checktime`.

---

## Карта файлов

```
app/src/main/java/dev/boar/checktime/
  data/SegmentDao.kt                 + byId, previousOf, nextOf, update, delete
  domain/TimelineRepository.kt       + EditResult, segment(), neighbours(), changeCategory, split, moveBoundary, merge
  ui/common/TimeStepperField.kt      НОВЫЙ: контрол времени (HH:mm, ±шаг, TimePicker), pickerToEpoch()
  ui/day/DayViewModel.kt             + SegmentRow.id, EditorState, editor: StateFlow, messages, действия
  ui/day/SegmentEditSheet.kt         НОВЫЙ: ModalBottomSheet с меню действий
  ui/day/DayScreen.kt                тап по записи → openEditor; шторка; snackbar
app/src/main/res/values/strings.xml  + строки шторки
CLAUDE.md                            domain/: операции этапа 2 реализованы
app/src/test/java/dev/boar/checktime/
  domain/TimelineRepositoryTest.kt   + тесты операций
  ui/common/TimeStepperFieldTest.kt  НОВЫЙ: pickerToEpoch + Compose-тест ±
  ui/day/DayViewModelTest.kt         + тесты редактора
```

Порядок: 1 репозиторий → 2 ViewModel → 3 контрол времени → 4 шторка + экран + проверка на телефоне.

---

### Task 1: Операции правки в `TimelineRepository`

**Files:**
- Modify: `app/src/main/java/dev/boar/checktime/data/SegmentDao.kt`, `app/src/main/java/dev/boar/checktime/domain/TimelineRepository.kt`
- Test: `app/src/test/java/dev/boar/checktime/domain/TimelineRepositoryTest.kt` (добавить тесты)

**Interfaces:**
- Consumes: `Segment(id, startAt, endAt, categoryId)`, `inMemoryDb()`, `Allocation`.
- Produces:
  - `SegmentDao`: `suspend fun byId(id: Long): Segment?`, `suspend fun previousOf(startAt: Long): Segment?`, `suspend fun nextOf(endAt: Long): Segment?`, `@Update suspend fun update(segment: Segment)`, `@Delete suspend fun delete(segment: Segment)`
  - `sealed interface EditResult { data object Done; data object Rejected }`
  - `TimelineRepository`: `suspend fun segment(id: Long): Segment?`, `suspend fun neighbours(segment: Segment): Pair<Segment?, Segment?>` (смежные предыдущий/следующий или null), `suspend fun changeCategory(segmentId: Long, categoryId: Long): EditResult`, `suspend fun split(segmentId: Long, at: Long): EditResult`, `suspend fun moveBoundary(leftId: Long, rightId: Long, at: Long): EditResult`, `suspend fun merge(keepId: Long, otherId: Long): EditResult`

- [ ] **Step 1: Падающие тесты**

Добавить в конец класса `TimelineRepositoryTest` (внутри класса, после `emptyAllocationChangesNothing`):
```kotlin
    /** 0–30 work, 30–45 rest, 45–75 work; accountedUntil = 75 мин. */
    private suspend fun threeSegments(): List<Segment> {
        repo.startTracking(now = 0)
        repo.allocate(0, listOf(Allocation(work, 30), Allocation(rest, 15), Allocation(work, 30)))
        return db.segmentDao().all()
    }

    @Test fun changeCategoryRewritesOnlyThatSegment() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Done, repo.changeCategory(segs[1].id, work))
        val after = db.segmentDao().all()
        assertEquals(listOf(work, work, work), after.map { it.categoryId })
        assertContiguous(after, 0, 75 * m)
    }

    @Test fun changeCategoryOfMissingSegmentIsRejected() = runTest {
        threeSegments()
        assertEquals(EditResult.Rejected, repo.changeCategory(999, work))
    }

    @Test fun splitCreatesTwoContiguousPartsWithSameCategory() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Done, repo.split(segs[0].id, 10 * m))
        val after = db.segmentDao().all()
        assertEquals(4, after.size)
        assertEquals(listOf(0L, 10 * m, 30 * m, 45 * m), after.map { it.startAt })
        assertEquals(work, after[1].categoryId)
        assertContiguous(after, 0, 75 * m)
    }

    @Test fun splitOutsideOrOnBoundaryIsRejected() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Rejected, repo.split(segs[0].id, 0))
        assertEquals(EditResult.Rejected, repo.split(segs[0].id, 30 * m))
        assertEquals(EditResult.Rejected, repo.split(segs[0].id, 31 * m))
        assertEquals(3, db.segmentDao().all().size)
    }

    @Test fun moveBoundaryShiftsBothNeighbours() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Done, repo.moveBoundary(segs[0].id, segs[1].id, 20 * m))
        val after = db.segmentDao().all()
        assertEquals(20 * m, after[0].endAt)
        assertEquals(20 * m, after[1].startAt)
        assertContiguous(after, 0, 75 * m)
    }

    @Test fun moveBoundaryRejectsNonAdjacentAndOutOfRange() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Rejected, repo.moveBoundary(segs[0].id, segs[2].id, 20 * m)) // не смежные
        assertEquals(EditResult.Rejected, repo.moveBoundary(segs[0].id, segs[1].id, 0))      // = start левого
        assertEquals(EditResult.Rejected, repo.moveBoundary(segs[0].id, segs[1].id, 45 * m)) // = end правого
        assertEquals(EditResult.Rejected, repo.moveBoundary(segs[1].id, segs[0].id, 20 * m)) // перепутан порядок
        assertContiguous(db.segmentDao().all(), 0, 75 * m)
        assertEquals(30 * m, db.segmentDao().all()[0].endAt)
    }

    @Test fun mergeAbsorbsNeighbourAndKeepsOwnCategory() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Done, repo.merge(keepId = segs[1].id, otherId = segs[2].id))
        val after = db.segmentDao().all()
        assertEquals(2, after.size)
        assertEquals(rest, after[1].categoryId)
        assertEquals(30 * m, after[1].startAt)
        assertEquals(75 * m, after[1].endAt)
        assertContiguous(after, 0, 75 * m)
    }

    @Test fun mergeWithPreviousWorksToo() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Done, repo.merge(keepId = segs[1].id, otherId = segs[0].id))
        val after = db.segmentDao().all()
        assertEquals(listOf(rest, work), after.map { it.categoryId })
        assertEquals(0L, after[0].startAt)
        assertEquals(45 * m, after[0].endAt)
        assertContiguous(after, 0, 75 * m)
    }

    @Test fun mergeRejectsNonAdjacent() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Rejected, repo.merge(segs[0].id, segs[2].id))
        assertEquals(3, db.segmentDao().all().size)
    }

    @Test fun neighboursAreAdjacentOnly() = runTest {
        val segs = threeSegments()
        val (p0, n0) = repo.neighbours(segs[0])
        assertNull(p0)
        assertEquals(segs[1].id, n0!!.id)
        val (p2, n2) = repo.neighbours(segs[2])
        assertEquals(segs[1].id, p2!!.id)
        assertNull(n2)
    }
```
Импорты, которые нужно добавить в тест: `dev.boar.checktime.data.Segment` уже есть; `org.junit.Assert.assertNull` уже есть.

- [ ] **Step 2: Прогон — не компилируется**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.domain.TimelineRepositoryTest'`
Expected: ошибки компиляции (`EditResult`, `changeCategory` и т.д. не найдены).

- [ ] **Step 3: DAO**

Добавить в `SegmentDao`:
```kotlin
    @Query("SELECT * FROM segments WHERE id = :id")
    suspend fun byId(id: Long): Segment?

    /** Ближайший сегмент, заканчивающийся не позже startAt (смежный — если endAt == startAt). */
    @Query("SELECT * FROM segments WHERE endAt <= :startAt ORDER BY endAt DESC LIMIT 1")
    suspend fun previousOf(startAt: Long): Segment?

    /** Ближайший сегмент, начинающийся не раньше endAt (смежный — если startAt == endAt). */
    @Query("SELECT * FROM segments WHERE startAt >= :endAt ORDER BY startAt LIMIT 1")
    suspend fun nextOf(endAt: Long): Segment?

    @Update suspend fun update(segment: Segment)
    @Delete suspend fun delete(segment: Segment)
```
(импорты `androidx.room.Update`, `androidx.room.Delete`).

- [ ] **Step 4: Репозиторий**

Добавить в `TimelineRepository.kt` рядом с `AllocateResult`:
```kotlin
sealed interface EditResult {
    data object Done : EditResult
    /** Запись исчезла, соседи не смежны или время вне допустимого диапазона. */
    data object Rejected : EditResult
}
```
и в класс `TimelineRepository` (после `observeSegments`):
```kotlin
    suspend fun segment(id: Long): Segment? = segmentDao.byId(id)

    /** Смежные соседи (предыдущий, следующий) или null, если границы не с кем делить. */
    suspend fun neighbours(segment: Segment): Pair<Segment?, Segment?> {
        val prev = segmentDao.previousOf(segment.startAt)?.takeIf { it.endAt == segment.startAt }
        val next = segmentDao.nextOf(segment.endAt)?.takeIf { it.startAt == segment.endAt }
        return prev to next
    }

    suspend fun changeCategory(segmentId: Long, categoryId: Long): EditResult = db.withTransaction {
        val s = segmentDao.byId(segmentId) ?: return@withTransaction EditResult.Rejected
        segmentDao.update(s.copy(categoryId = categoryId))
        EditResult.Done
    }

    /** Режет [segmentId] в точке [at] (строго внутри); вторая часть наследует категорию. */
    suspend fun split(segmentId: Long, at: Long): EditResult = db.withTransaction {
        val s = segmentDao.byId(segmentId) ?: return@withTransaction EditResult.Rejected
        if (at <= s.startAt || at >= s.endAt) return@withTransaction EditResult.Rejected
        segmentDao.update(s.copy(endAt = at))
        segmentDao.insertAll(listOf(Segment(startAt = at, endAt = s.endAt, categoryId = s.categoryId)))
        EditResult.Done
    }

    /** Двигает общую границу смежных [leftId] и [rightId] в [at] (строго между их внешними концами). */
    suspend fun moveBoundary(leftId: Long, rightId: Long, at: Long): EditResult = db.withTransaction {
        val l = segmentDao.byId(leftId) ?: return@withTransaction EditResult.Rejected
        val r = segmentDao.byId(rightId) ?: return@withTransaction EditResult.Rejected
        if (l.endAt != r.startAt) return@withTransaction EditResult.Rejected
        if (at <= l.startAt || at >= r.endAt) return@withTransaction EditResult.Rejected
        segmentDao.update(l.copy(endAt = at))
        segmentDao.update(r.copy(startAt = at))
        EditResult.Done
    }

    /** [keepId] поглощает смежный [otherId]; категория keep побеждает. */
    suspend fun merge(keepId: Long, otherId: Long): EditResult = db.withTransaction {
        val k = segmentDao.byId(keepId) ?: return@withTransaction EditResult.Rejected
        val o = segmentDao.byId(otherId) ?: return@withTransaction EditResult.Rejected
        val adjacent = k.endAt == o.startAt || o.endAt == k.startAt
        if (!adjacent) return@withTransaction EditResult.Rejected
        segmentDao.delete(o)
        segmentDao.update(k.copy(startAt = minOf(k.startAt, o.startAt), endAt = maxOf(k.endAt, o.endAt)))
        EditResult.Done
    }
```

- [ ] **Step 5: Прогон — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.domain.TimelineRepositoryTest'`
Expected: PASS (6 старых + 10 новых). Затем `./gradlew :app:testDebugUnitTest :app:assembleDebug` — всё зелёное.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/boar/checktime/data/SegmentDao.kt app/src/main/java/dev/boar/checktime/domain/TimelineRepository.kt app/src/test/java/dev/boar/checktime/domain/TimelineRepositoryTest.kt
git commit -m "Add segment editing operations to TimelineRepository"
```

---

### Task 2: Состояние редактора в `DayViewModel`

**Files:**
- Modify: `app/src/main/java/dev/boar/checktime/ui/day/DayViewModel.kt`
- Test: `app/src/test/java/dev/boar/checktime/ui/day/DayViewModelTest.kt` (добавить тесты)
- Modify: `app/src/main/res/values/strings.xml` (одна строка сообщения)

**Interfaces:**
- Consumes: Task 1 (`segment`, `neighbours`, `changeCategory`, `split`, `moveBoundary`, `merge`, `EditResult`), `CategoryRepository.observeTree()`, `Settings.stepMinutes`.
- Produces:
  - `data class SegmentRow(id: Long, startAt, endAt, category, group)` — добавлено поле `id` первым
  - `data class EditorState(segment: Segment, previous: Segment?, next: Segment?, category: Category?, groups: List<GroupWithCategories>, stepMinutes: Int)` — `groups` только с неархивными категориями
  - `DayViewModel`: `val editor: StateFlow<EditorState?>`, `val messages: Flow<Int>`, `fun openEditor(segmentId: Long)`, `fun closeEditor()`, `fun changeCategory(categoryId: Long)`, `fun split(at: Long)`, `fun moveStart(at: Long)`, `fun moveEnd(at: Long)`, `fun mergeWithPrevious()`, `fun mergeWithNext()` — все действия работают с текущим `editor`, после `Done` закрывают редактор, после `Rejected` шлют `R.string.edit_rejected` и тоже закрывают (данные перечитаются из Room).

- [ ] **Step 1: Падающие тесты**

Добавить в `DayViewModelTest` (внутри класса):
```kotlin
    private suspend fun seedThree(): List<dev.boar.checktime.data.Segment> {
        // 08:00–08:30 сон, 08:30–08:45 еда, 08:45–09:15 сон
        val start = dayStart + 8 * 60 * MINUTE_MS
        container.timeline.startTracking(start)
        container.timeline.allocate(start, listOf(Allocation(sleep, 30), Allocation(food, 15), Allocation(sleep, 30)))
        return container.db.segmentDao().all()
    }

    @Test fun rowsCarrySegmentIds() = runTest {
        val segs = seedThree()
        val s = vm().state.first { it.segments.size == 3 }
        assertEquals(segs.map { it.id }, s.segments.map { it.id })
    }

    @Test fun openEditorLoadsSegmentNeighboursAndCategories() = runTest {
        val segs = seedThree()
        val vm = vm()
        vm.openEditor(segs[1].id)
        val e = vm.editor.first { it != null }!!
        assertEquals(segs[1].id, e.segment.id)
        assertEquals(segs[0].id, e.previous!!.id)
        assertEquals(segs[2].id, e.next!!.id)
        assertEquals("Еда", e.category!!.name)
        assertEquals(listOf("Сон", "Еда"), e.groups.flatMap { g -> g.categories.map { it.name } })
        assertEquals(5, e.stepMinutes)
        vm.closeEditor()
        assertEquals(null, vm.editor.value)
    }

    @Test fun firstSegmentHasNoPrevious() = runTest {
        val segs = seedThree()
        val vm = vm()
        vm.openEditor(segs[0].id)
        val e = vm.editor.first { it != null }!!
        assertEquals(null, e.previous)
        assertEquals(segs[1].id, e.next!!.id)
    }

    @Test fun changeCategoryAppliesAndCloses() = runTest {
        val segs = seedThree()
        val vm = vm()
        vm.openEditor(segs[1].id)
        vm.editor.first { it != null }
        vm.changeCategory(sleep)
        vm.editor.first { it == null }
        assertEquals(sleep, container.timeline.segment(segs[1].id)!!.categoryId)
    }

    @Test fun splitAndMoveUseEditorSegment() = runTest {
        val segs = seedThree()
        val vm = vm()
        vm.openEditor(segs[0].id)
        vm.editor.first { it != null }
        vm.split(segs[0].startAt + 10 * MINUTE_MS)
        vm.editor.first { it == null }
        assertEquals(4, container.db.segmentDao().all().size)

        vm.openEditor(segs[1].id)
        vm.editor.first { it != null }
        vm.moveEnd(segs[1].endAt + 5 * MINUTE_MS) // граница с segs[2]
        vm.editor.first { it == null }
        assertEquals(segs[1].endAt + 5 * MINUTE_MS, container.timeline.segment(segs[1].id)!!.endAt)
        assertEquals(segs[1].endAt + 5 * MINUTE_MS, container.timeline.segment(segs[2].id)!!.startAt)

        vm.openEditor(segs[1].id)
        vm.editor.first { it != null }
        vm.moveStart(segs[1].startAt - 5 * MINUTE_MS) // граница с левым соседом (второй половиной разбитого)
        vm.editor.first { it == null }
        assertEquals(segs[1].startAt - 5 * MINUTE_MS, container.timeline.segment(segs[1].id)!!.startAt)
    }

    @Test fun mergeWithNextKeepsOwnCategory() = runTest {
        val segs = seedThree()
        val vm = vm()
        vm.openEditor(segs[1].id)
        vm.editor.first { it != null }
        vm.mergeWithNext()
        vm.editor.first { it == null }
        val all = container.db.segmentDao().all()
        assertEquals(2, all.size)
        assertEquals(food, all[1].categoryId)
        assertEquals(null, container.timeline.segment(segs[2].id))
    }

    @Test fun rejectedEditReportsMessageAndCloses() = runTest {
        val segs = seedThree()
        val vm = vm()
        vm.openEditor(segs[0].id)
        vm.editor.first { it != null }
        vm.split(segs[0].startAt) // на границе — отказ
        assertEquals(R.string.edit_rejected, vm.messages.first())
        vm.editor.first { it == null }
        assertEquals(3, container.db.segmentDao().all().size)
    }

    @Test fun moveStartWithoutPreviousIsIgnored() = runTest {
        val segs = seedThree()
        val vm = vm()
        vm.openEditor(segs[0].id)
        vm.editor.first { it != null }
        vm.moveStart(segs[0].startAt + MINUTE_MS)
        assertEquals(R.string.edit_rejected, vm.messages.first())
        assertEquals(segs[0].startAt, container.timeline.segment(segs[0].id)!!.startAt)
    }
```
Добавить импорт `dev.boar.checktime.R`.

- [ ] **Step 2: Прогон — не компилируется**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.ui.day.DayViewModelTest'`
Expected: ошибка компиляции.

- [ ] **Step 3: ViewModel**

В `DayViewModel.kt`:

1. `SegmentRow` → `data class SegmentRow(val id: Long, val startAt: Long, val endAt: Long, val category: Category?, val group: Group?)`; в `build()` создавать `SegmentRow(s.id, s.startAt, s.endAt, hit?.first, hit?.second)`.

2. Добавить после `DayUiState`:
```kotlin
/** Что открыто в шторке правки: запись, её смежные соседи и данные для меню. */
data class EditorState(
    val segment: Segment,
    val previous: Segment?,
    val next: Segment?,
    val category: Category?,
    /** Группы только с неархивными категориями — для смены категории. */
    val groups: List<GroupWithCategories>,
    val stepMinutes: Int,
)
```

3. В класс добавить поля и методы:
```kotlin
    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    /** Id строкового ресурса для snackbar. */
    val messages: Flow<Int> = _messages.receiveAsFlow()

    fun openEditor(segmentId: Long) {
        viewModelScope.launch {
            val segment = container.timeline.segment(segmentId) ?: return@launch
            val (prev, next) = container.timeline.neighbours(segment)
            val tree = container.categories.observeTree().first()
            val groups = tree
                .map { g -> g.copy(categories = g.categories.filter { !it.archived }) }
                .filter { it.categories.isNotEmpty() }
            val category = tree.flatMap { it.categories }.firstOrNull { it.id == segment.categoryId }
            val step = container.settings.settings.first().stepMinutes
            _editor.value = EditorState(segment, prev, next, category, groups, step)
        }
    }

    fun closeEditor() {
        _editor.value = null
    }

    fun changeCategory(categoryId: Long) = edit { e -> container.timeline.changeCategory(e.segment.id, categoryId) }
    fun split(at: Long) = edit { e -> container.timeline.split(e.segment.id, at) }
    fun moveStart(at: Long) = edit { e ->
        val prev = e.previous ?: return@edit EditResult.Rejected
        container.timeline.moveBoundary(prev.id, e.segment.id, at)
    }
    fun moveEnd(at: Long) = edit { e ->
        val next = e.next ?: return@edit EditResult.Rejected
        container.timeline.moveBoundary(e.segment.id, next.id, at)
    }
    fun mergeWithPrevious() = edit { e ->
        val prev = e.previous ?: return@edit EditResult.Rejected
        container.timeline.merge(keepId = e.segment.id, otherId = prev.id)
    }
    fun mergeWithNext() = edit { e ->
        val next = e.next ?: return@edit EditResult.Rejected
        container.timeline.merge(keepId = e.segment.id, otherId = next.id)
    }

    /** Общий каркас действия: выполнить над текущей записью, при отказе — сообщить; в любом случае закрыть. */
    private fun edit(action: suspend (EditorState) -> EditResult) {
        val e = _editor.value ?: return
        viewModelScope.launch {
            val result = action(e)
            _editor.value = null // сначала закрываем, потом сообщаем — тесты ждут сообщение
            if (result == EditResult.Rejected) _messages.send(R.string.edit_rejected)
        }
    }
```
Импорты: `dev.boar.checktime.R`, `dev.boar.checktime.domain.EditResult`, `kotlinx.coroutines.channels.Channel`, `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.asStateFlow`, `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.flow.receiveAsFlow`, `kotlinx.coroutines.launch`.

4. В `strings.xml`:
```xml
    <string name="edit_rejected">Не удалось изменить запись — данные уже поменялись</string>
```

- [ ] **Step 4: Прогон — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.ui.day.*'`
Expected: PASS (5 старых + 8 новых). Затем `./gradlew :app:testDebugUnitTest :app:assembleDebug` — `DayScreen.kt` ещё компилируется, т.к. использует `SegmentRow` только по именам полей; если `key = { "s${it.startAt}" }` — оставить пока.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/boar/checktime/ui/day/DayViewModel.kt app/src/main/res/values/strings.xml app/src/test/java/dev/boar/checktime/ui/day/DayViewModelTest.kt
git commit -m "Add segment editor state and actions to DayViewModel"
```

---

### Task 3: Контрол времени `TimeStepperField`

**Files:**
- Create: `app/src/main/java/dev/boar/checktime/ui/common/TimeStepperField.kt`
- Test: `app/src/test/java/dev/boar/checktime/ui/common/TimeStepperFieldTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `RepeatButton(onClick, modifier, content)`, `formatTime(epochMs, zone)`, `TimeMath.MINUTE_MS`.
- Produces:
  - `fun pickerToEpoch(current: Long, hour: Int, minute: Int, range: LongRange, zone: ZoneId): Long` — HH:mm на дате `current`; если результат вне `range`, пробует ±1 день и берёт попавший в диапазон; иначе возвращает вариант на дате `current`.
  - `@Composable fun TimeStepperField(value: Long, range: LongRange, stepMinutes: Int, onValueChange: (Long) -> Unit, modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault())` — `range` — допустимые значения включительно (вызывающий передаёт `(min+1мин)..(max−1мин)`); кнопки `−`/`+` (testTag `time-dec`/`time-inc`) шагают на `stepMinutes` и зажимают в `range`; текст времени (testTag `time-value`) — тап открывает `TimePicker`; если `value !in range` — текст красный.

- [ ] **Step 1: Падающие тесты**

`app/src/test/java/dev/boar/checktime/ui/common/TimeStepperFieldTest.kt`:
```kotlin
package dev.boar.checktime.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimeStepperFieldTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Moscow")
    private fun at(date: LocalDate, h: Int, m: Int) = date.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
    private val day = LocalDate.of(2026, 9, 15)

    @Test fun pickerKeepsDateWhenInRange() {
        val current = at(day, 14, 0)
        val range = at(day, 12, 0)..at(day, 18, 0)
        assertEquals(at(day, 15, 30), pickerToEpoch(current, 15, 30, range, zone))
    }

    @Test fun pickerRollsToNextDayAcrossMidnight() {
        // запись 23:00 (15-го) – 02:00 (16-го); текущее значение 23:30; выбрали 01:00 → это уже 16-е
        val current = at(day, 23, 30)
        val range = at(day, 23, 1)..at(day.plusDays(1), 1, 59)
        assertEquals(at(day.plusDays(1), 1, 0), pickerToEpoch(current, 1, 0, range, zone))
    }

    @Test fun pickerRollsToPreviousDay() {
        val current = at(day.plusDays(1), 0, 30)
        val range = at(day, 23, 1)..at(day.plusDays(1), 1, 59)
        assertEquals(at(day, 23, 30), pickerToEpoch(current, 23, 30, range, zone))
    }

    @Test fun pickerOutOfRangeReturnsSameDateCandidate() {
        val current = at(day, 14, 0)
        val range = at(day, 12, 0)..at(day, 18, 0)
        assertEquals(at(day, 9, 0), pickerToEpoch(current, 9, 0, range, zone))
    }

    @Test fun stepButtonsMoveByStepAndClamp() {
        val range = at(day, 12, 0)..at(day, 12, 12)
        var value by mutableStateOf(at(day, 12, 5))
        compose.setContent {
            TimeStepperField(value = value, range = range, stepMinutes = 5, onValueChange = { value = it }, zone = zone)
        }
        compose.onNodeWithTag("time-value").assertTextEquals("12:05")
        compose.onNodeWithTag("time-inc").performClick()
        compose.onNodeWithTag("time-value").assertTextEquals("12:10")
        compose.onNodeWithTag("time-inc").performClick() // 12:15 > 12:12 → зажим
        compose.onNodeWithTag("time-value").assertTextEquals("12:12")
        compose.onNodeWithTag("time-dec").performClick()
        compose.onNodeWithTag("time-dec").performClick()
        compose.onNodeWithTag("time-dec").performClick() // 12:12 → 12:07 → 12:02 → 12:00 (зажим)
        compose.onNodeWithTag("time-value").assertTextEquals("12:00")
        assertEquals(at(day, 12, 0), value)
    }
}
```

- [ ] **Step 2: Прогон — не компилируется**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.ui.common.TimeStepperFieldTest'`
Expected: ошибка компиляции.

- [ ] **Step 3: Реализация**

`app/src/main/java/dev/boar/checktime/ui/common/TimeStepperField.kt`:
```kotlin
package dev.boar.checktime.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.boar.checktime.R
import dev.boar.checktime.domain.TimeMath
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * HH:mm на дате [current]; если вне [range] — пробуем соседние сутки (запись может переходить полночь).
 * Если ничего не попало в диапазон, возвращаем вариант на дате current — UI подсветит ошибку.
 */
fun pickerToEpoch(current: Long, hour: Int, minute: Int, range: LongRange, zone: ZoneId): Long {
    val date = Instant.ofEpochMilli(current).atZone(zone).toLocalDate()
    val sameDay = date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    if (sameDay in range) return sameDay
    for (shift in listOf(1L, -1L)) {
        val candidate = date.plusDays(shift).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        if (candidate in range) return candidate
    }
    return sameDay
}

/** Время с кнопками ±[stepMinutes] и TimePicker по тапу; значение зажимается в [range] (включительно). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeStepperField(
    value: Long,
    range: LongRange,
    stepMinutes: Int,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var showPicker by remember { mutableStateOf(false) }
    val step = stepMinutes * TimeMath.MINUTE_MS
    val inRange = value in range

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RepeatButton(
            onClick = { onValueChange((value - step).coerceIn(range)) },
            modifier = Modifier.testTag("time-dec"),
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            formatTime(value, zone),
            modifier = Modifier
                .clickable { showPicker = true }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("time-value"),
            style = MaterialTheme.typography.headlineSmall,
            color = if (inRange) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        RepeatButton(
            onClick = { onValueChange((value + step).coerceIn(range)) },
            modifier = Modifier.testTag("time-inc"),
        ) { Icon(Icons.Default.Add, contentDescription = null) }
    }

    if (showPicker) {
        val local = Instant.ofEpochMilli(value).atZone(zone).toLocalTime()
        val pickerState = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(pickerToEpoch(value, pickerState.hour, pickerState.minute, range, zone))
                    showPicker = false
                }) { Text(stringResource(R.string.dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}
```
Добавить в `strings.xml`: `<string name="dialog_ok">ОК</string>` (строка `dialog_cancel` уже есть).

- [ ] **Step 4: Прогон — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.ui.common.TimeStepperFieldTest' && ./gradlew :app:assembleDebug`
Expected: PASS (5 тестов). Если `LongRange.coerceIn` не резолвится — использовать `value.coerceIn(range.first, range.last)`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/boar/checktime/ui/common/TimeStepperField.kt app/src/main/res/values/strings.xml app/src/test/java/dev/boar/checktime/ui/common/TimeStepperFieldTest.kt
git commit -m "Add TimeStepperField with step buttons and time picker"
```

---

### Task 4: Шторка правки и подключение к экрану «День»

**Files:**
- Create: `app/src/main/java/dev/boar/checktime/ui/day/SegmentEditSheet.kt`
- Modify: `app/src/main/java/dev/boar/checktime/ui/day/DayScreen.kt`, `app/src/main/res/values/strings.xml`, `CLAUDE.md`, `docs/superpowers/specs/2026-09-15-checktime-design.md` (§4.1 — уточнить механику выбора времени и слияния)
- Test: `app/src/test/java/dev/boar/checktime/ui/day/SegmentEditSheetTest.kt`

**Interfaces:**
- Consumes: `EditorState`, `DayViewModel.{openEditor, closeEditor, changeCategory, split, moveStart, moveEnd, mergeWithPrevious, mergeWithNext, editor, messages}`, `TimeStepperField`, `formatTime`, `formatDurationMs`, `GroupWithCategories`.
- Produces: `@Composable SegmentEditSheet(state: EditorState, onDismiss, onChangeCategory: (Long) -> Unit, onSplit: (Long) -> Unit, onMoveStart: (Long) -> Unit, onMoveEnd: (Long) -> Unit, onMergePrevious: () -> Unit, onMergeNext: () -> Unit, zone: ZoneId = systemDefault())`; `DayScreen` получает `onSegmentClick: (Long) -> Unit`; `DayRoute` рисует шторку и snackbar.

- [ ] **Step 1: Падающий тест**

`app/src/test/java/dev/boar/checktime/ui/day/SegmentEditSheetTest.kt` — тестируется внутреннее содержимое шторки `SegmentEditContent` (та же разметка без `ModalBottomSheet`, чтобы Robolectric не боролся с анимацией):
```kotlin
package dev.boar.checktime.ui.day

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.data.Segment
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentEditSheetTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("UTC")
    private val h = 60 * MINUTE_MS
    private val sleep = Category(id = 1, groupId = 1, name = "Сон", color = 0, sortOrder = 0)
    private val food = Category(id = 2, groupId = 1, name = "Еда", color = 0, sortOrder = 1)
    private val groups = listOf(GroupWithCategories(Group(1, "Личное", 0, 0), listOf(sleep, food)))
    private val prev = Segment(id = 10, startAt = 8 * h, endAt = 9 * h, categoryId = 1)
    private val cur = Segment(id = 11, startAt = 9 * h, endAt = 10 * h, categoryId = 2)
    private val next = Segment(id = 12, startAt = 10 * h, endAt = 11 * h, categoryId = 1)

    private fun state(previous: Segment? = prev, nxt: Segment? = next) =
        EditorState(cur, previous, nxt, food, groups, stepMinutes = 5)

    @Test fun changeCategoryFlow() {
        var chosen = -1L
        compose.setContent {
            SegmentEditContent(state(), onChangeCategory = { chosen = it }, onSplit = {}, onMoveStart = {}, onMoveEnd = {}, onMergePrevious = {}, onMergeNext = {}, zone = zone)
        }
        compose.onNodeWithText("09:00 – 10:00 · 1 ч").assertExists()
        compose.onNodeWithText("Категория").performClick()
        compose.onNodeWithText("Сон").performClick()
        assertEquals(1L, chosen)
    }

    @Test fun splitDefaultsToMidpointAndConfirms() {
        var splitAt = -1L
        compose.setContent {
            SegmentEditContent(state(), onChangeCategory = {}, onSplit = { splitAt = it }, onMoveStart = {}, onMoveEnd = {}, onMergePrevious = {}, onMergeNext = {}, zone = zone)
        }
        compose.onNodeWithText("Разбить").performClick()
        compose.onNodeWithTag("time-value").assertExists()
        compose.onNodeWithText("09:30").assertExists()
        compose.onNodeWithTag("time-inc").performClick()
        compose.onNodeWithText("Готово").performClick()
        assertEquals(9 * h + 35 * MINUTE_MS, splitAt)
    }

    @Test fun moveEndStartsAtCurrentBoundary() {
        var moved = -1L
        compose.setContent {
            SegmentEditContent(state(), onChangeCategory = {}, onSplit = {}, onMoveStart = {}, onMoveEnd = { moved = it }, onMergePrevious = {}, onMergeNext = {}, zone = zone)
        }
        compose.onNodeWithText("Сдвинуть конец").performClick()
        compose.onNodeWithText("10:00").assertExists()
        compose.onNodeWithTag("time-dec").performClick()
        compose.onNodeWithText("Готово").performClick()
        assertEquals(10 * h - 5 * MINUTE_MS, moved)
    }

    @Test fun neighbourActionsDisabledWithoutNeighbours() {
        compose.setContent {
            SegmentEditContent(state(previous = null, nxt = null), onChangeCategory = {}, onSplit = {}, onMoveStart = {}, onMoveEnd = {}, onMergePrevious = {}, onMergeNext = {}, zone = zone)
        }
        compose.onNodeWithText("Сдвинуть начало").assertIsNotEnabled()
        compose.onNodeWithText("Сдвинуть конец").assertIsNotEnabled()
        compose.onNodeWithText("Слить с предыдущей").assertIsNotEnabled()
        compose.onNodeWithText("Слить со следующей").assertIsNotEnabled()
    }

    @Test fun mergeNextCallsBack() {
        var merged = false
        compose.setContent {
            SegmentEditContent(state(), onChangeCategory = {}, onSplit = {}, onMoveStart = {}, onMoveEnd = {}, onMergePrevious = {}, onMergeNext = { merged = true }, zone = zone)
        }
        compose.onNodeWithText("Слить со следующей").performClick()
        assertEquals(true, merged)
    }
}
```

- [ ] **Step 2: Прогон — не компилируется**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.boar.checktime.ui.day.SegmentEditSheetTest'`
Expected: ошибка компиляции.

- [ ] **Step 3: Шторка**

`app/src/main/java/dev/boar/checktime/ui/day/SegmentEditSheet.kt`:
```kotlin
package dev.boar.checktime.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.boar.checktime.R
import dev.boar.checktime.domain.TimeMath
import dev.boar.checktime.ui.common.TimeStepperField
import dev.boar.checktime.ui.common.formatDurationMs
import dev.boar.checktime.ui.common.formatTime
import java.time.ZoneId

/** Режимы шторки: меню действий, выбор категории, выбор времени для одной из операций. */
private sealed interface Mode {
    data object Menu : Mode
    data object PickCategory : Mode
    data class PickTime(val kind: TimeKind) : Mode
}

private enum class TimeKind { Split, MoveStart, MoveEnd }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentEditSheet(
    state: EditorState,
    onDismiss: () -> Unit,
    onChangeCategory: (Long) -> Unit,
    onSplit: (Long) -> Unit,
    onMoveStart: (Long) -> Unit,
    onMoveEnd: (Long) -> Unit,
    onMergePrevious: () -> Unit,
    onMergeNext: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SegmentEditContent(state, onChangeCategory, onSplit, onMoveStart, onMoveEnd, onMergePrevious, onMergeNext, zone)
    }
}

/** Содержимое шторки отдельно от ModalBottomSheet — так его можно тестировать под Robolectric. */
@Composable
internal fun SegmentEditContent(
    state: EditorState,
    onChangeCategory: (Long) -> Unit,
    onSplit: (Long) -> Unit,
    onMoveStart: (Long) -> Unit,
    onMoveEnd: (Long) -> Unit,
    onMergePrevious: () -> Unit,
    onMergeNext: () -> Unit,
    zone: ZoneId,
) {
    var mode by remember { mutableStateOf<Mode>(Mode.Menu) }
    val s = state.segment

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(state.category?.color ?: 0xFF9E9E9E.toInt())))
            Spacer(Modifier.width(12.dp))
            Text(
                state.category?.name ?: stringResource(R.string.day_unknown_category),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            "${formatTime(s.startAt, zone)} – ${formatTime(s.endAt, zone)} · ${formatDurationMs(s.endAt - s.startAt)}",
            Modifier.padding(top = 4.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )

        when (val m = mode) {
            Mode.Menu -> Menu(
                state,
                onPickCategory = { mode = Mode.PickCategory },
                onSplit = { mode = Mode.PickTime(TimeKind.Split) },
                onMoveStart = { mode = Mode.PickTime(TimeKind.MoveStart) },
                onMoveEnd = { mode = Mode.PickTime(TimeKind.MoveEnd) },
                onMergePrevious = onMergePrevious,
                onMergeNext = onMergeNext,
            )
            Mode.PickCategory -> CategoryList(state, onChangeCategory, onBack = { mode = Mode.Menu })
            is Mode.PickTime -> TimeEditor(
                state, m.kind, zone,
                onConfirm = { at ->
                    when (m.kind) {
                        TimeKind.Split -> onSplit(at)
                        TimeKind.MoveStart -> onMoveStart(at)
                        TimeKind.MoveEnd -> onMoveEnd(at)
                    }
                },
                onBack = { mode = Mode.Menu },
            )
        }
    }
}

@Composable
private fun Menu(
    state: EditorState,
    onPickCategory: () -> Unit,
    onSplit: () -> Unit,
    onMoveStart: () -> Unit,
    onMoveEnd: () -> Unit,
    onMergePrevious: () -> Unit,
    onMergeNext: () -> Unit,
) {
    val hasPrev = state.previous != null
    val hasNext = state.next != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPickCategory, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_category)) }
        OutlinedButton(onClick = onSplit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_split)) }
        OutlinedButton(onClick = onMoveStart, enabled = hasPrev, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_move_start)) }
        OutlinedButton(onClick = onMoveEnd, enabled = hasNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_move_end)) }
        OutlinedButton(onClick = onMergePrevious, enabled = hasPrev, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_merge_previous)) }
        OutlinedButton(onClick = onMergeNext, enabled = hasNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_merge_next)) }
    }
}

@Composable
private fun CategoryList(state: EditorState, onChangeCategory: (Long) -> Unit, onBack: () -> Unit) {
    Column {
        state.groups.forEach { g ->
            Text(g.group.name, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = Color(g.group.color))
            g.categories.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChangeCategory(c.id) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color)))
                    Spacer(Modifier.width(12.dp))
                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.dialog_cancel)) }
    }
}

/**
 * Диапазон допустимых значений — строго внутри внешних границ (шаг = минута):
 * split — внутри записи; moveStart — (prev.startAt, segment.endAt); moveEnd — (segment.startAt, next.endAt).
 */
@Composable
private fun TimeEditor(state: EditorState, kind: TimeKind, zone: ZoneId, onConfirm: (Long) -> Unit, onBack: () -> Unit) {
    val s = state.segment
    val minute = TimeMath.MINUTE_MS
    val (range, initial, title) = when (kind) {
        TimeKind.Split -> Triple(
            (s.startAt + minute)..(s.endAt - minute),
            s.startAt + ((s.endAt - s.startAt) / 2 / minute) * minute,
            R.string.edit_split,
        )
        TimeKind.MoveStart -> Triple(
            ((state.previous?.startAt ?: s.startAt) + minute)..(s.endAt - minute),
            s.startAt,
            R.string.edit_move_start,
        )
        TimeKind.MoveEnd -> Triple(
            (s.startAt + minute)..((state.next?.endAt ?: s.endAt) - minute),
            s.endAt,
            R.string.edit_move_end,
        )
    }
    var value by remember(kind) { mutableStateOf(initial) }
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
        TimeStepperField(
            value = value,
            range = range,
            stepMinutes = state.stepMinutes,
            onValueChange = { value = it },
            modifier = Modifier.padding(vertical = 8.dp),
            zone = zone,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dialog_cancel)) }
            Button(onClick = { onConfirm(value) }, enabled = value in range, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.allocation_done))
            }
        }
    }
}
```
Добавить в `strings.xml`:
```xml
    <string name="edit_category">Категория</string>
    <string name="edit_split">Разбить</string>
    <string name="edit_move_start">Сдвинуть начало</string>
    <string name="edit_move_end">Сдвинуть конец</string>
    <string name="edit_merge_previous">Слить с предыдущей</string>
    <string name="edit_merge_next">Слить со следующей</string>
```

- [ ] **Step 4: Подключение в `DayScreen.kt`**

1. `DayScreen` получает новый параметр `onSegmentClick: (Long) -> Unit` (после `onAllocate`). В списке записей: `items(state.segments, key = { "s${it.id}" })` и `Row(Modifier.fillMaxWidth().clickable { onSegmentClick(s.id) }.padding(horizontal = 16.dp, vertical = 6.dp), …)` (импорт `androidx.compose.foundation.clickable`).
2. `DayRoute`:
```kotlin
@Composable
fun DayRoute(viewModel: DayViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    Box(Modifier.fillMaxSize()) {
        DayScreen(
            state = state,
            onPrevious = viewModel::previousDay,
            onNext = viewModel::nextDay,
            onToday = viewModel::today,
            onAllocate = { context.startActivity(PendingNotification.allocationIntent(context)) },
            onSegmentClick = viewModel::openEditor,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    editor?.let { e ->
        SegmentEditSheet(
            state = e,
            onDismiss = viewModel::closeEditor,
            onChangeCategory = viewModel::changeCategory,
            onSplit = viewModel::split,
            onMoveStart = viewModel::moveStart,
            onMoveEnd = viewModel::moveEnd,
            onMergePrevious = viewModel::mergeWithPrevious,
            onMergeNext = viewModel::mergeWithNext,
        )
    }
}
```
(импорты: `androidx.compose.material3.SnackbarHost`, `SnackbarHostState`, `androidx.compose.runtime.LaunchedEffect`, `remember`). Убедиться, что методы VM возвращают `Unit` (все `edit {}`-обёртки — `Unit`), иначе обернуть в лямбды.

3. `CLAUDE.md`, bullet `domain/`: заменить фразу про этап 2 на «`changeCategory`, `split`, `moveBoundary`, `merge` — тоже единичные транзакции с проверкой смежности/диапазона, возвращают `EditResult`». В bullet `ui/` добавить: «тап по записи на экране «День» открывает `SegmentEditSheet`».

4. Спека §4.1 «День»: дополнить абзац про шторку: «время для разбиения/сдвига выбирается контролом HH:mm с кнопками ±шаг и `TimePicker` по тапу; слить можно с любым соседом — категория текущей записи побеждает».

- [ ] **Step 5: Прогон — проходит**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug && ./gradlew :app:lint`
Expected: все тесты PASS (в т.ч. 5 новых `SegmentEditSheetTest`, `MainActivityTest` не сломан), lint без новых ошибок.

- [ ] **Step 6: Проверка на телефоне**

`./gradlew :app:installDebug`, затем на экране «День»: тап по записи → шторка с реальными границами; «Категория» → выбор → запись перекрасилась, итоги пересчитались; «Разбить» → ±, тап по времени открывает часы, «Готово» → две записи; «Сдвинуть конец» → соседняя запись подвинулась; «Слить со следующей» → одна запись с категорией исходной; у первой записи дня кнопки «Сдвинуть начало»/«Слить с предыдущей» неактивны, если предыдущего дня нет.

- [ ] **Step 7: Commit**

```bash
git add app/src/main app/src/test CLAUDE.md docs/superpowers/specs
git commit -m "Add segment edit sheet to the day screen"
```
