package one.tunkshif.swirlmusic.ui.home

/** Which greeting to show. Kept out of the composable so it can be tested at every hour. */
enum class DayPart { Morning, Afternoon, Evening, Night }

fun dayPartFor(hour: Int): DayPart = when (hour) {
    in 5..11 -> DayPart.Morning
    in 12..17 -> DayPart.Afternoon
    in 18..21 -> DayPart.Evening
    else -> DayPart.Night
}
