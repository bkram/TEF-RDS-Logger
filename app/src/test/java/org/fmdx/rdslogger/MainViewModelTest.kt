package org.fmdx.rdslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {

    private val viewModel = MainViewModel()

    @Test
    fun formatLine_groupsEveryFourCharacters() {
        val formatted = viewModel.formatLine("1a2b3c4d5e6f7a7b")

        assertEquals("1A2B 3C4D 5E6F 7A7B", formatted)
    }

    @Test
    fun buildFileName_usesFirstBlockPrefix() {
        val fileName = viewModel.buildFileName(listOf("1A2B 3C4D 5E6F 7A7B @2024/10/08 12:00:00"))

        assertTrue("Expected timestamped filename with prefix", Regex("^1A2B_\\d{8}_\\d{6}\\.spy$").matches(fileName))
    }

    @Test
    fun buildFileName_fallsBackToRdsWhenPrefixInvalid() {
        val fileName = viewModel.buildFileName(listOf("--- invalid"))

        assertTrue("Expected fallback prefix", Regex("^rds_\\d{8}_\\d{6}\\.spy$").matches(fileName))
    }

    @Test
    fun handleIncomingPayload_parsesRdsSpyBlocks() {
        viewModel.handleIncomingPayload("G:\n1A2B3C4D5E6F7A7B\n")

        val lines = viewModel.uiState.value.lines
        assertEquals(1, lines.size)
        assertTrue("Expected formatted payload with timestamp", lines.first().startsWith("1A2B 3C4D 5E6F 7A7B @"))
    }

    @Test
    fun handleIncomingPayload_keepsTotalLinesWhileWindowingDisplay() {
        repeat(1002) {
            viewModel.handleIncomingPayload("G:\n1A2B3C4D5E6F7A7B\n")
        }

        val state = viewModel.uiState.value
        assertEquals(1002, state.totalLines)
        assertEquals(1000, state.lines.size)
    }

    @Test
    fun decodeLine_parsesPiGroupAndPty() {
        val decoded = MainViewModel.decodeLine("1A2B 3C4D 5E6F 7A7B @2024/10/08 12:00:00")

        requireNotNull(decoded)
        assertEquals("1A2B", decoded.pi)
        assertEquals("3B", decoded.group)
        assertEquals((0x3C4D shr 5) and 0x1F, decoded.pty)
    }
}
