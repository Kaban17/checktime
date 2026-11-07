package dev.boar.checktime.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val sortOrder: Int,
)

@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(entity = Group::class, parentColumns = ["id"], childColumns = ["groupId"])],
    indices = [Index("groupId")],
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    val color: Int,
    val sortOrder: Int,
    val archived: Boolean = false,
)

/** Непрерывный отрезок времени [startAt, endAt), unix-мс UTC. */
@Entity(
    tableName = "segments",
    foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"])],
    indices = [Index("categoryId"), Index("startAt"), Index("endAt")],
)
data class Segment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startAt: Long,
    val endAt: Long,
    val categoryId: Long,
)

/**
 * Цель «не меньше [minutes] минут за неделю» на категорию или группу — заполнено ровно одно из двух.
 * Действует с недели [validFrom] (epochDay понедельника) до следующей записи того же объекта;
 * `minutes = 0` — цель снята (спека 2.1.1).
 */
@Entity(
    tableName = "weekly_goals",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Group::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId"), Index("groupId")],
)
data class WeeklyGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null,
    val groupId: Long? = null,
    val minutes: Int,
    val validFrom: Long,
)

/** Единственная строка (id = 1). Хвост нерасписанного времени = [accountedUntil, now). */
@Entity(tableName = "tracking_state")
data class TrackingState(
    @PrimaryKey val id: Int = 1,
    val trackingStart: Long,
    val accountedUntil: Long,
)
