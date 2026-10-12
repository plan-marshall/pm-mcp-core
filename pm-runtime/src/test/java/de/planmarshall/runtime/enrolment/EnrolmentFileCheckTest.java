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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicInteger;


import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreRefusal.Outcome;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    private static final String OWNER_ONLY = "rw-------";

    private static final String READ_ONLY = "r--------";

    private static final String CONTENT = "{}";

    private static final String WORLD_WRITABLE = "rw-rw-rw-";

    private static final String ABSENT = "absent.json";

    @TempDir
    Path directory;

    private Path file;
    private EnrolmentFileCheck check;

    @BeforeEach
    void create() throws IOException {
        file = Files.writeString(directory.resolve("pm-mcp-core.json"), CONTENT);
        check = new EnrolmentFileCheck(USER);
    }

    private void mode(String permissions) throws IOException {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions));
    }

    @ParameterizedTest(name = "mode {0} passes")
    @ValueSource(strings = {OWNER_ONLY, READ_ONLY})
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
        mode(OWNER_ONLY);
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
        mode(OWNER_ONLY);
        var foreignCheck = new EnrolmentFileCheck("someone-else");

        var refusal = assertThrows(StoreRefusal.class, () -> foreignCheck.check(file));

        assertAll(
                () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome()),
                () -> assertTrue(refusal.getMessage().contains("foreign owner " + USER), refusal.getMessage()));
    }

    @Test
    @DisplayName("a file that does not exist cannot be checked")
    void missingFile() {
        var missing = directory.resolve(ABSENT);

        assertThrows(UncheckedIOException.class, () -> check.check(missing));
    }

    @Test
    @DisplayName("a check without the expected user is refused")
    void missingUser() {
        assertThrows(NullPointerException.class, () -> new EnrolmentFileCheck(null));
    }

    /**
     * The no-follow snapshot: the checks and the read are bound to one open of the file, and the bytes of a link
     * target, of an insecure file or of a file that changed while it was read are never handed out.
     */
    @Nested
    @DisplayName("snapshot")
    class Snapshot {

        private static final byte[] BYTES = CONTENT.getBytes(StandardCharsets.UTF_8);

        private static final Runnable NOTHING = () -> {
            // the seam changes nothing
        };

        @ParameterizedTest(name = "mode {0} is read in full")
        @ValueSource(strings = {OWNER_ONLY, READ_ONLY})
        @DisplayName("a file that passes the checks is read in full")
        void aPassingFileIsReadInFull(String permissions) throws IOException {
            mode(permissions);

            var snapshot = check.snapshot(file);

            assertAll(
                    () -> assertTrue(snapshot.violation().isEmpty()),
                    () -> assertArrayEquals(BYTES, snapshot.content().orElseThrow()),
                    () -> assertFalse(snapshot.isAbsent()));
        }

        @Test
        @DisplayName("a file that does not exist is an absent snapshot, not an exception")
        void anAbsentFileIsAnAbsentSnapshotNotAnException() {
            var missing = directory.resolve(ABSENT);

            var snapshot = check.snapshot(missing);

            assertAll(
                    () -> assertTrue(snapshot.violation().isEmpty()),
                    () -> assertTrue(snapshot.content().isEmpty()),
                    () -> assertTrue(snapshot.isAbsent()),
                    () -> assertThrows(UncheckedIOException.class, () -> check.check(missing)));
        }

        @ParameterizedTest(name = "a link whose target exists: {0}")
        @ValueSource(booleans = {true, false})
        @DisplayName("a symbolic link is refused without reading its target")
        void aSymbolicLinkIsRefusedWithoutReadingItsTarget(boolean targetExists) throws IOException {
            mode(OWNER_ONLY);
            var target = targetExists ? file : directory.resolve("dangling.json");
            var link = Files.createSymbolicLink(directory.resolve("link.json"), target);

            var snapshot = check.snapshot(link);

            assertAll(
                    () -> assertEquals(EnrolmentFileCheck.SYMBOLIC_LINK, snapshot.violation().orElseThrow()),
                    () -> assertTrue(snapshot.content().isEmpty()),
                    () -> assertFalse(snapshot.isAbsent()));
        }

        @ParameterizedTest(name = "mode {0}, expected owner {1}")
        @CsvSource({"rw-r--r--, ", "rwx------, ", "rw-------, someone-else"})
        @DisplayName("a broader mode or a foreign owner is refused with the reason of the violation")
        void aBroaderModeOrAForeignOwnerIsRefusedWithTheReasonOfViolation(String permissions, String owner)
                throws IOException {
            mode(permissions);
            var ownerCheck = new EnrolmentFileCheck(owner == null ? USER : owner);
            var expected = ownerCheck.violation(file).orElseThrow();

            var snapshot = ownerCheck.snapshot(file);

            assertAll(
                    () -> assertEquals(expected, snapshot.violation().orElseThrow()),
                    () -> assertTrue(snapshot.content().isEmpty()));
        }

        @Test
        @DisplayName("a file that cannot be opened for another reason than a link or its removal is an I/O failure")
        void anUnreadableFileIsAnIoFailure() throws IOException {
            Assumptions.assumeFalse("root".equals(USER), "root reads a file of mode 0000");
            mode("---------");

            var failure = assertThrows(UncheckedIOException.class, () -> check.snapshot(file));

            assertTrue(failure.getMessage().contains(file.toString()), failure.getMessage());
        }

        @Test
        @DisplayName("a file replaced atomically while it was read is refused")
        void aFileReplacedWhileItWasReadIsRefused() throws IOException {
            mode(OWNER_ONLY);
            var other = writeOwnerOnly("other.json", "{\"other\": true}");

            var snapshot = check.snapshot(file, () -> replace(other));

            assertAll(
                    () -> assertEquals(EnrolmentFileCheck.CHANGED_WHILE_READ, snapshot.violation().orElseThrow()),
                    () -> assertTrue(snapshot.content().isEmpty()));
        }

        @Test
        @DisplayName("a file replaced by a symbolic link while it was read is refused")
        void aFileReplacedByALinkWhileItWasReadIsRefused() throws IOException {
            mode(OWNER_ONLY);
            var valid = writeOwnerOnly("valid.json", CONTENT);
            var link = Files.createSymbolicLink(directory.resolve("swap.json"), valid);

            var snapshot = check.snapshot(file, () -> replace(link));

            assertAll(
                    () -> assertTrue(snapshot.violation().isPresent()),
                    () -> assertTrue(snapshot.content().isEmpty()));
        }

        @Test
        @DisplayName("a mode loosened while the file was read is refused with the ordinary reason")
        void aModeLoosenedWhileItWasReadIsRefused() throws IOException {
            mode(OWNER_ONLY);

            var snapshot = check.snapshot(file, () -> loosen(file));

            assertAll(
                    () -> assertEquals("mode 0666 broader than 0600", snapshot.violation().orElseThrow()),
                    () -> assertTrue(snapshot.content().isEmpty()));
        }

        @Test
        @DisplayName("a seam that changes nothing returns the content")
        void aSeamThatChangesNothingReturnsTheContent() throws IOException {
            mode(OWNER_ONLY);

            var snapshot = check.snapshot(file, NOTHING);

            assertAll(
                    () -> assertTrue(snapshot.violation().isEmpty()),
                    () -> assertArrayEquals(BYTES, snapshot.content().orElseThrow()));
        }

        @Test
        @DisplayName("the seam runs once for a present file and never for an absent or a refused one")
        void theSeamRunsOncePerReadFile() throws IOException {
            mode(OWNER_ONLY);
            var runs = new AtomicInteger();
            Runnable counting = runs::incrementAndGet;
            check.snapshot(file, counting);
            var afterRead = runs.get();
            check.snapshot(directory.resolve(ABSENT), counting);
            mode(WORLD_WRITABLE);
            check.snapshot(file, counting);

            assertAll(
                    () -> assertEquals(1, afterRead),
                    () -> assertEquals(1, runs.get()));
        }

        private Path writeOwnerOnly(String name, String content) throws IOException {
            var written = Files.writeString(directory.resolve(name), content);
            Files.setPosixFilePermissions(written, PosixFilePermissions.fromString(OWNER_ONLY));
            return written;
        }

        private void replace(Path replacement) {
            try {
                Files.move(replacement, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static void loosen(Path target) {
            try {
                Files.setPosixFilePermissions(target, PosixFilePermissions.fromString(WORLD_WRITABLE));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
