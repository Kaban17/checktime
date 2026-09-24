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
