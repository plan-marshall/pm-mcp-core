/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shared refusal construction of the coverage parsers.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("CoverageRefusals")
class CoverageRefusalsTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("opens the reason with the format identifier")
    void opensTheReasonWithTheFormat() {
        String message = Generators.nonEmptyStrings().next();

        CoverageParseResult.Refused refused = CoverageRefusals.of(CoverageFormat.LCOV, new IOException(message));

        assertTrue(refused.reason().startsWith("lcov: "), "The reason opens with the format id");
        assertEquals("lcov: " + message, refused.reason(), "The reason carries the cause's message");
    }

    @Test
    @DisplayName("names the failure class when the cause has no message")
    void namesTheFailureClassWithoutMessage() {
        CoverageParseResult.Refused refused = CoverageRefusals.of(CoverageFormat.ISTANBUL, new IOException());

        assertEquals("istanbul: IOException", refused.reason(), "A message-less cause names its class");
    }

    // The reason text "no such file" is the contract.
    @Test
    @DisplayName("names a missing report file by its file name, never its directory")
    void namesAMissingFileByItsFileName() {
        Path reportFile = reportFile();

        CoverageParseResult.Refused refused = CoverageRefusals.of(CoverageFormat.JACOCO, reportFile,
                new NoSuchFileException(reportFile.toString()));

        assertAll(
                () -> assertEquals("jacoco: " + reportFile.getFileName() + ": no such file", refused.reason(),
                        "The reason names the file name and the failure"),
                () -> assertFalse(refused.reason().contains(directory.toString()),
                        "The reason never names the directory"));
    }

    // The reason text "access denied" is the contract.
    @Test
    @DisplayName("names an unreadable report file by its file name, never its directory")
    void namesAnUnreadableFileByItsFileName() {
        Path reportFile = reportFile();

        CoverageParseResult.Refused refused = CoverageRefusals.of(CoverageFormat.COBERTURA, reportFile,
                new AccessDeniedException(reportFile.toString()));

        assertEquals("cobertura: " + reportFile.getFileName() + ": access denied", refused.reason(),
                "An access failure names the file name only");
    }

    @Test
    @DisplayName("names another file-system failure by its reason, never its path")
    void namesAnotherFileSystemFailureByItsReason() {
        Path reportFile = reportFile();
        String reason = Generators.letterStrings(1, 30).next();

        CoverageParseResult.Refused withReason = CoverageRefusals.of(CoverageFormat.LCOV, reportFile,
                new FileSystemException(reportFile.toString(), null, reason));
        CoverageParseResult.Refused withoutReason = CoverageRefusals.of(CoverageFormat.LCOV, reportFile,
                new FileSystemException(reportFile.toString()));

        assertAll(
                () -> assertEquals("lcov: " + reportFile.getFileName() + ": " + reason, withReason.reason(),
                        "The reason carries the failure's own reason"),
                () -> assertEquals("lcov: " + reportFile.getFileName() + ": FileSystemException",
                        withoutReason.reason(), "A reason-less failure names its class"));
    }

    @Test
    @DisplayName("passes the message of a read failure that is not a file-system failure through")
    void passesAReadFailureMessageThrough() {
        String message = Generators.nonEmptyStrings().next();

        CoverageParseResult.Refused refused =
                CoverageRefusals.of(CoverageFormat.ISTANBUL, reportFile(), new IOException(message));

        assertEquals("istanbul: " + message, refused.reason(), "A read failure keeps its own message");
    }

    @Test
    @DisplayName("names a report file without a file name generically")
    void namesAFileWithoutAFileName() {
        Path root = directory.getRoot();

        CoverageParseResult.Refused refused =
                CoverageRefusals.of(CoverageFormat.JACOCO, root, new NoSuchFileException(root.toString()));

        assertEquals("jacoco: the report file: no such file", refused.reason(),
                "A path without a file name is named generically");
    }

    private Path reportFile() {
        return directory.resolve(Generators.letterStrings(1, 20).next() + ".xml");
    }
}
