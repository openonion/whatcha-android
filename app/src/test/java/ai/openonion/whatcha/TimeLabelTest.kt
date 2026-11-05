package ai.openonion.whatcha

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TimeLabelTest {
    private fun at(day: Int, hour: Int, minute: Int) =
        Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, day, hour, minute, 0) }.timeInMillis

    @Test
    fun todayShowsOnlyThePartOfDayAndClock() {
        assertEquals("晚上 8:35", timeLabel(at(3, 20, 35), now = at(3, 21, 0)))
        assertEquals("上午 9:05", timeLabel(at(3, 9, 5), now = at(3, 21, 0)))
    }

    @Test
    fun anEarlierDayAddsTheDate() {
        assertEquals("11月2日 下午 3:00", timeLabel(at(2, 15, 0), now = at(3, 21, 0)))
    }
}
