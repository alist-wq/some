package com.example.navigation.app

import java.nio.file.Files
import java.nio.file.Path

/** Returns completed session files in stable newest-first order for recorder export. */
internal fun listRecordedSessions(recordingsDirectory: Path): List<Path> {
    if (!Files.isDirectory(recordingsDirectory)) return emptyList()
    return Files.list(recordingsDirectory).use { paths ->
        paths.filter { path ->
            Files.isRegularFile(path) && path.fileName.toString().endsWith(SESSION_EXTENSION)
        }.sorted { first, second ->
            Files.getLastModifiedTime(second).compareTo(Files.getLastModifiedTime(first))
        }.toList()
    }
}

private const val SESSION_EXTENSION = ".navsession"
