/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.enrolment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;


import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreRefusal.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The per-use checks of an enrolment file (PM-SEC-4): a foreign owner, a mode broader than {@code 0600} and a symbolic
 * link are each refused with {@code enrolment_insecure}; a {@code 0600} and a narrower {@code 0400} file of the
 * running user pass.
 */
@DisplayName("Enrolment file check")
class EnrolmentFileCheckTest {

    private static final String USER = System.getProperty("user.name");

    @TempDir
    Path directory;

    private Path file;
    private EnrolmentFileCheck check;

    @BeforeEach
    void create() throws IOException {
        file = Files.writeString(directory.resolve("pm-mcp-core.json"), "{}");
        check = new EnrolmentFileCheck(USER);
    }

    private void mode(String permissions) throws IOException {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions));
    }

    @ParameterizedTest(name = "mode {0} passes")
    @ValueSource(strings = {"rw-------", "r--------"})
    @DisplayName("a file of the running user with no permission bit outside 0600 passes")
    void passes(String permissions) throws Exception {
        mode(permissions);

        assertAll(
                () -> assertDoesNotThrow(() -> check.check(file)),
                () -> assertTrue(check.violation(file).isEmpty()));
    }

    @ParameterizedTest(name = "mode {0} ({1}) is refused")
    @CsvSource({"rw-r-----, 0640", "rw-r--r--, 0644", "rwx------, 0700", "rw----rw-, 0606"})
    @DisplayName("a mode broader than 0600 is refused as enrolment_insecure naming the file and the mode")
    void broaderMode(String permissions, String octal) throws Exception {
        mode(permissions);

        var refusal = assertThrows(StoreRefusal.class, () -> check.check(file));

        assertAll(
                () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome()),
                () -> assertEquals(file, refusal.file()),
                () -> assertTrue(refusal.getMessage().contains("mode %s broader than 0600".formatted(octal)),
                        refusal.getMessage()));
    }

    @Test
    @DisplayName("a symbolic link is refused, also when its target would pass")
    void symbolicLink() throws Exception {
        mode("rw-------");
        var link = Files.createSymbolicLink(directory.resolve("link.json"), file);

        var refusal = assertThrows(StoreRefusal.class, () -> check.check(link));

        assertAll(
                () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome()),
                () -> assertEquals(link, refusal.file()),
                () -> assertTrue(refusal.getMessage().contains(EnrolmentFileCheck.SYMBOLIC_LINK)));
    }

    @Test
    @DisplayName("a file owned by another user than the expected one is refused as a foreign owner")
    void foreignOwner() throws Exception {
        mode("rw-------");

        var refusal = assertThrows(StoreRefusal.class, () -> new EnrolmentFileCheck("someone-else").check(file));

        assertAll(
                () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome()),
                () -> assertTrue(refusal.getMessage().contains("foreign owner " + USER), refusal.getMessage()));
    }

    @Test
    @DisplayName("a file that does not exist cannot be checked")
    void missingFile() {
        var missing = directory.resolve("absent.json");

        assertThrows(UncheckedIOException.class, () -> check.check(missing));
    }

    @Test
    @DisplayName("a check without the expected user is refused")
    void missingUser() {
        assertThrows(NullPointerException.class, () -> new EnrolmentFileCheck(null));
    }
}
