package app.tandemwear.watch.ui.music

import app.tandemwear.watch.ui.music.PlayerLayout.Row
import app.tandemwear.watch.ui.music.PlayerLayout.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLayoutTest {

    /** Every row of a fitting plan has room for its content and stays below the time text. */
    private fun assertConsistent(d: Float, plan: PlayerLayout.Plan) {
        val v = plan.variant
        plan.widths[Row.VOLUME]?.let { assertTrue("$d volume", it >= PlayerLayout.volumeWidth(v, plan.volumeText, 1f)) }
        assertTrue("$d controls", plan.widths.getValue(Row.CONTROLS) >= PlayerLayout.controlsWidth(v))
        assertTrue("$d progress", plan.widths.getValue(Row.PROGRESS) >= 100f)
    }

    @Test
    fun `450 px at density 2 (225 dp) shows everything without scrolling`() {
        listOf(225f, 227f).forEach { d ->
            val plan = PlayerLayout.plan(d, fontScale = 1f, hasArtist = true, hasVolume = true)
            assertTrue("$d dp: $plan", plan.fits)
            assertTrue("$d dp keeps the percentage", plan.volumeText)
            assertConsistent(d, plan)
        }
    }

    @Test
    fun `450 px at density 2_125 (212 dp) fits, percentage moves to the description`() {
        val plan = PlayerLayout.plan(212f, 1f, hasArtist = true, hasVolume = true)
        assertTrue(plan.fits)
        assertFalse(plan.volumeText)
        assertConsistent(212f, plan)
    }

    @Test
    fun `smaller screens and large fonts scroll instead of clipping`() {
        assertFalse(PlayerLayout.plan(192f, 1f, hasArtist = true, hasVolume = true).fits)
        // 1.3x still fits a 225 dp screen (dense, percentage in the description); much larger scrolls
        assertTrue(PlayerLayout.plan(225f, fontScale = 1.3f, hasArtist = true, hasVolume = true).fits)
        val large = PlayerLayout.plan(225f, fontScale = 2f, hasArtist = true, hasVolume = true)
        assertFalse(large.fits)
        assertEquals(225f * 0.7f, large.widths.getValue(Row.VOLUME), 0.01f)
    }

    @Test
    fun `the richest variant that fits is chosen`() {
        // Without volume a big screen keeps the regular buttons and the artist row
        val plan = PlayerLayout.plan(227f, 1f, hasArtist = true, hasVolume = false)
        assertTrue(plan.fits)
        assertFalse(plan.variant.artistInTimeRow)
    }

    @Test
    fun `buttons never go below 44 dp`() {
        Variant.entries.forEach {
            assertTrue(it.sideSize >= 44f && it.volumeButtonSize >= 44f && it.playSize >= 48f)
        }
    }

    @Test
    fun `chord geometry`() {
        assertEquals(200f - 2 * PlayerLayout.EDGE_INSET, PlayerLayout.chord(100f, -10f, 10f), 0.01f)
        assertEquals(120f - 2 * PlayerLayout.EDGE_INSET, PlayerLayout.chord(100f, 40f, 80f), 0.01f)
        assertEquals(0f, PlayerLayout.chord(100f, 90f, 120f), 0.01f)
    }
}
