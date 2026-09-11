package com.mattschoe.smarthome.data

/** The smallest persisted week-grid scale, in dp per hour. */
const val WeekHourHeightMin = 6f

/** The fresh-install scale and event-block design baseline, in dp per hour. */
const val WeekHourHeightDefault = 24f

/**
 * A practical safety ceiling rather than a normal zoom target. At 480dp per hour the 24-hour grid
 * is 11,520dp tall and shows roughly one hour at a time on the target tablet.
 */
const val WeekHourHeightSafetyLimit = 480f

/** Persisted week-grid scales accepted across app restarts. */
val WeekHourHeightRange = WeekHourHeightMin..WeekHourHeightSafetyLimit

/**
 * A persisted zoom level clamped into the grid's safe range. Non-finite values fall back to the
 * normal 24dp default rather than either extreme: both gesture math and persisted strings can
 * produce NaN or infinity, which a plain comparison would let through.
 */
fun clampWeekHourHeight(hourHeightDp: Float): Float =
    if (hourHeightDp.isFinite()) hourHeightDp.coerceIn(WeekHourHeightRange)
    else WeekHourHeightDefault

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
        // Missing or malformed state is not a request for an extreme: open at the design baseline.
        return clampWeekHourHeight(raw ?: WeekHourHeightDefault)
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
    private var hourHeightDp = WeekHourHeightDefault

    override fun read(): Float = hourHeightDp

    override fun write(hourHeightDp: Float) {
        this.hourHeightDp = clampWeekHourHeight(hourHeightDp)
    }
}
