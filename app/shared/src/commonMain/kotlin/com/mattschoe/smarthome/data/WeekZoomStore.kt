package com.mattschoe.smarthome.data

/**
 * How tall one hour row of the week grid may be, in dp: fully expanded, where a day is 576dp and
 * scrolls, down to the whole 24h in 144dp. It lives here rather than only as a
 * `Dimensions` token because it is also what a persisted level is validated against — the range and
 * the geometry are one fact, and `Dimensions.weekHourHeightMin`/`Max` are this range as Dp.
 */
val WeekHourHeightRange = 6f..24f

/**
 * A zoom level, clamped into the range the grid can actually draw. Non-finite values fall back to
 * the expanded end rather than clamping: a pinch's scale factor is a ratio of finger distances, and
 * both that and a persisted string can hand this a NaN, which every comparison would let through.
 */
fun clampWeekHourHeight(hourHeightDp: Float): Float =
    if (hourHeightDp.isFinite()) hourHeightDp.coerceIn(WeekHourHeightRange)
    else WeekHourHeightRange.endInclusive

/**
 * How tall one hour row of the week grid is, in dp — what pinching the grid sets. Kept between runs
 * so the phone and the wall tablet reopen at the level they were left at, since the pinch is as much
 * a choice about what the calendar is *for* (a day at a glance vs. the checklist) as a gesture.
 *
 * Reads and writes are best-effort, never fatal, and a read always lands inside
 * [WeekHourHeightRange] — a stale or garbage value can't produce an unusable grid.
 */
interface WeekZoomStore {
    fun read(): Float
    fun write(hourHeightDp: Float)
}

/** A [WeekZoomStore] over a platform [KeyValueStore], holding the level as one plain number. */
class KeyValueWeekZoomStore(private val store: KeyValueStore) : WeekZoomStore {

    override fun read(): Float {
        val raw = runCatching { store.get(Key) }.getOrNull()?.toFloatOrNull()
        // Nothing written, or something that isn't a number: open fully expanded, which is the view
        // the week grid was designed around.
        return clampWeekHourHeight(raw ?: WeekHourHeightRange.endInclusive)
    }

    override fun write(hourHeightDp: Float) {
        runCatching { store.put(Key, clampWeekHourHeight(hourHeightDp).toString()) }
    }

    private companion object {
        const val Key = "calendar.weekZoom"
    }
}

/** A level that lives only as long as the process — the fallback where no [KeyValueStore] exists. */
class InMemoryWeekZoomStore : WeekZoomStore {
    private var hourHeightDp = WeekHourHeightRange.endInclusive

    override fun read(): Float = hourHeightDp

    override fun write(hourHeightDp: Float) {
        this.hourHeightDp = clampWeekHourHeight(hourHeightDp)
    }
}
