package com.example.navigation.app

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordedSessionsTest {
    @Test
    fun `lists navsession files newest first and ignores other files`() {
        val directory = Files.createTempDirectory("recordings")
        val older = Files.createFile(directory.resolve("older.navsession"))
        Thread.sleep(5)
        val newer = Files.createFile(directory.resolve("newer.navsession"))
        Files.createFile(directory.resolve("notes.txt"))

        assertEquals(listOf(newer, older), listRecordedSessions(directory))
    }
}
