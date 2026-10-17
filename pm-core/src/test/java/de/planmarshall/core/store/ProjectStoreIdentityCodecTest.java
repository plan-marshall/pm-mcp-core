/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The identity record {@code store.json} of a per-project machine store (PM-ARCH-1, PM-IMPL-2): read strictly by the
 * closed schema of the store identity and written in the one canonical form, every value outside the schema refused
 * as unreadable, never read as something close to it.
 */
@DisplayName("Project store identity codec")
class ProjectStoreIdentityCodecTest {

    static final Path FILE = Path.of("/base/projects/pm-mcp-core/store.json");

    /** An enrolled project's identity in the canonical form. */
    static final String ENROLLED = "{\"format_version\":1,\"project_id\":\"pm-mcp-core\",\"project_root\":"
            + "\"/home/oliver/git/pm-mcp-core\",\"display_name\":\"plan-marshall/pm-mcp-core\",\"created_at\":"
            + "\"2026-10-17T09:00:00Z\",\"unenrolled_at\":null}";

    /** The same identity after the project was unenrolled. */
    static final String UNENROLLED = ENROLLED.replace("\"unenrolled_at\":null",
            "\"unenrolled_at\":\"2026-10-18T09:00:00Z\"");

    static ProjectStoreIdentity read(String document) {
        return ProjectStoreIdentityCodec.read(FILE, document.getBytes(UTF_8));
    }

    /**
     * @param projectId   the project
     * @param projectRoot the main checkout
     * @return an enrolled identity, created at a fixed instant
     */
    static ProjectStoreIdentity identity(String projectId, String projectRoot) {
        return new ProjectStoreIdentity(projectId, projectRoot, "plan-marshall/" + projectId,
                Instant.parse("2026-10-17T09:00:00Z"), null);
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("an enrolled identity is typed and written back byte for byte")
        void enrolled() {
            var identity = read(ENROLLED);

            assertAll(
                    () -> assertArrayEquals(ENROLLED.getBytes(UTF_8), ProjectStoreIdentityCodec.write(identity)),
                    () -> assertEquals("pm-mcp-core", identity.projectId()),
                    () -> assertEquals("/home/oliver/git/pm-mcp-core", identity.projectRoot()),
                    () -> assertEquals("plan-marshall/pm-mcp-core", identity.displayName()),
                    () -> assertEquals(Instant.parse("2026-10-17T09:00:00Z"), identity.createdAt()),
                    () -> assertNull(identity.unenrolledAt()));
        }

        @Test
        @DisplayName("an unenrolled identity keeps its unenrolment and is written back byte for byte")
        void unenrolled() {
            var identity = read(UNENROLLED);

            assertAll(
                    () -> assertArrayEquals(UNENROLLED.getBytes(UTF_8), ProjectStoreIdentityCodec.write(identity)),
                    () -> assertEquals(Instant.parse("2026-10-18T09:00:00Z"), identity.unenrolledAt()));
        }

        @Test
        @DisplayName("a display name of exactly 128 characters is read, counted in code points")
        void longestDisplayName() {
            var name = "ä".repeat(127) + "😀";

            var identity = read(ENROLLED.replace("plan-marshall/pm-mcp-core", name));

            assertEquals(name, identity.displayName());
        }
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of("an unknown member", ENROLLED.replace("\"unenrolled_at\":null",
                        "\"unenrolled_at\":null,\"remote\":\"origin\""), "undeclared field 'remote'"),
                Arguments.of("a member twice", ENROLLED.replace("\"display_name\"",
                        "\"display_name\":\"x\",\"display_name\""), "field 'display_name' twice"),
                Arguments.of("no project id", ENROLLED.replace("\"project_id\":\"pm-mcp-core\",", ""),
                        "field 'project_id' of the store identity is missing"),
                Arguments.of("no project root", ENROLLED.replace("\"project_root\":\"/home/oliver/git/pm-mcp-core\",",
                        ""), "field 'project_root' of the store identity is missing"),
                Arguments.of("no display name", ENROLLED.replace("\"display_name\":\"plan-marshall/pm-mcp-core\",", ""),
                        "field 'display_name' of the store identity is missing"),
                Arguments.of("no creation instant", ENROLLED.replace("\"created_at\":\"2026-10-17T09:00:00Z\",", ""),
                        "field 'created_at' of the store identity is missing"),
                Arguments.of("no unenrolment member", ENROLLED.replace(",\"unenrolled_at\":null", ""),
                        "field 'unenrolled_at' of the store identity is missing"),
                Arguments.of("a project id outside the grammar", ENROLLED.replace("\"pm-mcp-core\"", "\"PM_core\""),
                        "Not a valid identifier for project_id: PM_core"),
                Arguments.of("a null project id", ENROLLED.replace("\"project_id\":\"pm-mcp-core\"",
                        "\"project_id\":null"), "'project_id' must be a string"),
                Arguments.of("a relative project root", ENROLLED.replace("/home/oliver/git/pm-mcp-core",
                        "git/pm-mcp-core"), "project_root is a normalized absolute path: git/pm-mcp-core"),
                Arguments.of("a project root that is not normalized", ENROLLED.replace("/home/oliver/git/pm-mcp-core",
                        "/home/oliver/../oliver/git/pm-mcp-core"), "project_root is a normalized absolute path"),
                Arguments.of("an empty project root", ENROLLED.replace("/home/oliver/git/pm-mcp-core", ""),
                        "project_root is not empty"),
                Arguments.of("an empty display name", ENROLLED.replace("plan-marshall/pm-mcp-core", ""),
                        "The display name of a project has 1 to 128 characters, not 0"),
                Arguments.of("a display name of 129 characters", ENROLLED.replace("plan-marshall/pm-mcp-core",
                        "n".repeat(129)), "The display name of a project has 1 to 128 characters, not 129"),
                Arguments.of("a creation instant with an offset", ENROLLED.replace("2026-10-17T09:00:00Z",
                        "2026-10-17T11:00:00+02:00"), "'created_at' is not in the one shape of an instant"),
                Arguments.of("an unenrolment that is no instant", ENROLLED.replace("\"unenrolled_at\":null",
                        "\"unenrolled_at\":false"), "'unenrolled_at' must be a string"),
                Arguments.of("a display name that is no string", ENROLLED.replace("\"plan-marshall/pm-mcp-core\"",
                        "128"), "'display_name' must be a string"),
                Arguments.of("no JSON object", "[1]", "not a JSON object"));
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("matched control: both canonical documents are read")
        void controlsAreRead() {
            assertAll(
                    () -> assertDoesNotThrow(() -> read(ENROLLED)),
                    () -> assertDoesNotThrow(() -> read(UNENROLLED)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.ProjectStoreIdentityCodecTest#refusals")
        @DisplayName("a record outside the schema is refused as unreadable, naming the file and the value")
        void refusedAsUnreadable(String name, String document, String detail) {
            var refusal = assertThrows(StoreRefusal.class, () -> read(document));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(FILE, refusal.file()),
                    () -> assertTrue(refusal.getMessage().contains(detail), refusal.getMessage()));
        }

        @Test
        @DisplayName("another format version is refused as newer or outdated before any member is read")
        void otherFormatVersion() {
            var newer = assertThrows(StoreRefusal.class,
                    () -> read(ENROLLED.replace("\"format_version\":1", "\"format_version\":2")));
            var outdated = assertThrows(StoreRefusal.class, () -> read(ENROLLED
                    .replace("\"format_version\":1", "\"format_version\":0").replace("pm-mcp-core\",", "x\",")));

            assertAll(
                    () -> assertEquals(Outcome.FORMAT_NEWER, newer.outcome()),
                    () -> assertEquals(Outcome.FORMAT_OUTDATED, outdated.outcome()));
        }

        @Test
        @DisplayName("an identity outside the schema cannot be built, so it is never written")
        void notBuilt() {
            var createdAt = Instant.parse("2026-10-17T09:00:00Z");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new ProjectStoreIdentity("pm-mcp-core", "relative", "name", createdAt, null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new ProjectStoreIdentity("pm-mcp-core", "/repo", "name", null, null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new ProjectStoreIdentity("pm-mcp-core", "/repo", null, createdAt, null)));
        }
    }
}
