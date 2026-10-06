# Этап 3 — отложенные мелкие замечания ревью

- Task 1: minor (deferred): no leap-year/year-boundary period tests; Month/Week data-class constructors don't enforce the first-day invariant (only periodOf does).
- Task 3: minor (deferred): percentages can sum to 99/101% (no remainder compensation); value label sits below the bar row rather than inline.
- Финальное ревью: проценты могут давать 99/101 % (нет компенсации остатка); значение столбика подписано под строкой, а не в одной строке со столбиком; «Месяц»/«Неделя» всегда перескакивают на текущий период, не сохраняя контекст листания; иконка вкладки «i» — BarChart нет в material-icons-core; сегменты удалённых категорий не попадают в сумму (ситуация недостижима — категорию с записями удалить нельзя).
