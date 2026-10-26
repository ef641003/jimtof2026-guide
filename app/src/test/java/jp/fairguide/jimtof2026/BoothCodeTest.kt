package jp.fairguide.jimtof2026

import jp.fairguide.jimtof2026.logic.BoothCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoothCodeTest {

    @Test
    fun eastHall() {
        val loc = BoothCode.locate("E1024")!!
        assertEquals("E1", loc.hallId)
        assertEquals("東1ホール", loc.hallName)
        assertEquals("1F", loc.floor)
        assertEquals("東7ホール", BoothCode.locate("E7002")!!.hallName)
    }

    @Test
    fun fullWidthLowercaseAndHyphen() {
        assertEquals("E7", BoothCode.locate("ｅ７００２")!!.hallId)
        assertEquals("W3", BoothCode.locate(" w3-104 ")!!.hallId)
        assertEquals("S4029", BoothCode.normalize("s 4029"))
        assertEquals("E1024", BoothCode.normalize("E-1024"))
        // パビリオン内の小間は「-番号」を残す
        assertEquals("E1009-2", BoothCode.normalize("ｅ１００９－２"))
        assertEquals("E1", BoothCode.locate("E1009-2")!!.hallId)
        assertEquals("E1009", BoothCode.normalize("E1009-"))
    }

    @Test
    fun atrium() {
        val loc = BoothCode.locate("WA015")!!
        assertEquals("WA", loc.hallId)
        assertEquals("西アトリウム", loc.hallName)
    }

    @Test
    fun upperFloors() {
        assertEquals("4F", BoothCode.locate("W4090")!!.floor)
        assertEquals("4F", BoothCode.locate("S3061")!!.floor)
        assertEquals("1F", BoothCode.locate("S1023")!!.floor)
        assertEquals("1F", BoothCode.locate("W2032")!!.floor)
    }

    @Test
    fun closedOrInvalid() {
        assertNull(BoothCode.locate("E5001")) // 東5ホールは休館
        assertNull(BoothCode.locate("X123"))
        assertNull(BoothCode.locate(""))
        assertNull(BoothCode.locate("S5001"))
    }

    @Test
    fun hallOrderEastWestSouth() {
        assertTrue(BoothCode.hallRank("E8001") < BoothCode.hallRank("WA001"))
        assertTrue(BoothCode.hallRank("W4001") < BoothCode.hallRank("S1001"))
        assertEquals(BoothCode.HALL_ORDER.size, BoothCode.hallRank(""))
    }
}
