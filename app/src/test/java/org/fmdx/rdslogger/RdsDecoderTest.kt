package org.fmdx.rdslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RdsDecoderTest {

    @Test
    fun psComesFromBlockDOnly() {
        val decoder = MainViewModel.RdsDecoder()
        val blockA = 0x1234
        val blockB = 0x0000 // group type 0A
        val blockC = 0x4142 // should be ignored for PS
        val blockD = 0x4344 // "CD"
        val frame = "%04X%04X%04X%04X".format(blockA, blockB, blockC, blockD)

        repeat(3) { decoder.ingest(frame) } // commit PS after repeats

        val snapshot = decoder.snapshots().single()
        assertEquals("CD", snapshot.ps)
        assertNull(snapshot.ecc)
    }

    @Test
    fun eccDecodedFromType1aVariant0() {
        val decoder = MainViewModel.RdsDecoder()
        val blockA = 0x1ABC
        val blockB = 0x1000 // group type 1A
        val ecc = 0xE1
        // variant 0, ECC carried in block D (data LSB) with data MSB nibble in block C
        val blockC = 0x0000
        val blockD = ecc
        val frame = "%04X%04X%04X%04X".format(blockA, blockB, blockC, blockD)

        decoder.ingest(frame)

        val snapshot = decoder.snapshots().single()
        assertEquals(ecc, snapshot.ecc)
    }

    @Test
    fun diFlagsLabelCompressedAndArtificialHeadAreDistinct() {
        val decoder = MainViewModel.RdsDecoder()
        val frames = listOf(
            build0ADiFrame(diIndex = 0, diBit = 0), // stereo
            build0ADiFrame(diIndex = 1, diBit = 1), // artificial head on
            build0ADiFrame(diIndex = 2, diBit = 1), // compressed on
            build0ADiFrame(diIndex = 3, diBit = 1)  // dynamic PTY on
        )
        frames.forEach { decoder.ingest(it) }

        val flags = decoder.snapshots().single().di.associateBy { it.label }
        assertEquals("On", flags["Artificial head"]?.value)
        assertEquals("On", flags["Compressed"]?.value)
    }

    @Test
    fun dynamicPtyReflectsBitState() {
        val decoder = MainViewModel.RdsDecoder()
        decoder.ingest(build0ADiFrame(diIndex = 3, diBit = 0)) // dynamic PTY off
        val flags = decoder.snapshots().single().di.associateBy { it.label }
        assertEquals("Static", flags["Dynamic PTY"]?.value)
        decoder.ingest(build0ADiFrame(diIndex = 3, diBit = 1)) // turn on
        val updated = decoder.snapshots().single().di.associateBy { it.label }
        assertEquals("Dynamic", updated["Dynamic PTY"]?.value)
    }

    private fun build0ADiFrame(diIndex: Int, diBit: Int): String {
        val blockA = 0x1234
        val blockB = (diBit shl 2) or (diIndex and 0x3)
        val blockC = 0x0000
        val blockD = 0x0000
        return "%04X%04X%04X%04X".format(blockA, blockB, blockC, blockD)
    }
}
