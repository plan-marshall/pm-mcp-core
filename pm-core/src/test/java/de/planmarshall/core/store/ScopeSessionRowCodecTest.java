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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.ScopeSessionRow.Client;
import de.planmarshall.core.store.ScopeSessionRow.EnteredFrom;
import de.planmarshall.core.store.ScopeSessionRow.EnvironmentMatchTier;
import de.planmarshall.core.store.ScopeSessionRow.ModelFamily;
import de.planmarshall.core.store.ScopeSessionRow.ModelSource;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The entry row of the scope session log (PM-IMPL-2): written in the order of its schema, read back equal, and every
 * other shape refused as a {@link StoreRefusal} naming the file.
 */
@DisplayName("Scope session row codec")
class ScopeSessionRowCodecTest {

    private static final Path FILE = Path.of("/base/projects/pm-mcp-core/plans/a-plan/sessions.jsonl");
    private static final Instant ENTERED_AT = Instant.parse("2026-10-13T08:00:00Z");

    /** A row from the main checkout of a session whose host exposes everything. */
    static ScopeSessionRow row() {
        return ScopeSessionRow.builder().clientSessionId("sess-1").client(Client.CLAUDE).clientDetected(Client.CLAUDE)
                .environmentMatchTier(EnvironmentMatchTier.MATCHED).clientBuild("2.1.0").modelFamily(ModelFamily.OPUS)
                .modelId("claude-opus-5").modelSource(ModelSource.HOST).pmMcpVersion("0.1.0").bundleVersion("0.1.0")
                .projectId("pm-mcp-core").enteredFrom(EnteredFrom.MAIN_CHECKOUT).generationId("sg-0123456789abcdef")
                .enteredAt(ENTERED_AT).build();
    }

    /** @return the members of the document of {@link #row()}, each with its JSON value, in the order of the schema */
    private static Map<String, String> members() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("client_session_id", "\"sess-1\"");
        members.put("client", "\"claude\"");
        members.put("client_detected", "\"claude\"");
        members.put("environment_match_tier", "\"MATCHED\"");
        members.put("client_build", "\"2.1.0\"");
        members.put("model_family", "\"opus\"");
        members.put("model_id", "\"claude-opus-5\"");
        members.put("model_source", "\"host\"");
        members.put("pm_mcp_version", "\"0.1.0\"");
        members.put("bundle_version", "\"0.1.0\"");
        members.put("project_id", "\"pm-mcp-core\"");
        members.put("entered_from", "\"main_checkout\"");
        members.put("generation_id", "\"sg-0123456789abcdef\"");
        members.put("entered_at", "\"2026-10-13T08:00:00Z\"");
        return members;
    }

    /** @return every member the schema requires besides the format version */
    static Stream<String> requiredMembers() {
        return ScopeSessionRowCodec.REQUIRED_FIELDS.stream().filter(field -> !"format_version".equals(field));
    }

    private static byte[] document(Map<String, String> members) {
        return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}")).getBytes(UTF_8);
    }

    private static StoreRefusal assertRefused(Outcome outcome, byte[] document) {
        var refusal = assertThrows(StoreRefusal.class, () -> ScopeSessionRowCodec.read(FILE, document));
        assertAll(
                () -> assertEquals(outcome, refusal.outcome()),
                () -> assertEquals(FILE, refusal.file()),
                () -> assertTrue(refusal.getMessage().contains(FILE.toString()), "the message names the file"));
        return refusal;
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("a row is written in the order of the schema, without worktree_of for the main checkout")
        void writtenShape() {
            assertArrayEquals(document(members()), ScopeSessionRowCodec.write(row()));
        }

        @Test
        @DisplayName("a row from a worktree is read back equal, with worktree_of before generation_id")
        void worktree() {
            var row = row().toBuilder().enteredFrom(EnteredFrom.WORKTREE).worktreeOf("a-plan").build();

            var written = ScopeSessionRowCodec.write(row);

            assertAll(
                    () -> assertEquals(row, ScopeSessionRowCodec.read(FILE, written)),
                    () -> assertTrue(new String(written, UTF_8)
                            .contains("\"entered_from\":\"worktree\",\"worktree_of\":\"a-plan\",\"generation_id\"")));
        }

        @Test
        @DisplayName("a row of the epic worktree names it _epics")
        void epicsWorktree() {
            var row = row().toBuilder().enteredFrom(EnteredFrom.WORKTREE).worktreeOf("_epics").build();

            assertEquals(row, ScopeSessionRowCodec.read(FILE, ScopeSessionRowCodec.write(row)));
        }

        @Test
        @DisplayName("a row whose host provides nothing optional writes and reads the nulls")
        void nullsWritten() {
            var row = row().toBuilder().clientSessionId(null).clientDetected(null)
                    .environmentMatchTier(EnvironmentMatchTier.UNVERIFIED).clientBuild(null).modelFamily(null)
                    .modelId(null).modelSource(null).bundleVersion(null).build();

            var written = ScopeSessionRowCodec.write(row);

            assertAll(
                    () -> assertEquals(row, ScopeSessionRowCodec.read(FILE, written)),
                    () -> assertArrayEquals(written, ScopeSessionRowCodec.write(ScopeSessionRowCodec.read(FILE,
                            written))));
        }

        @Test
        @DisplayName("the members may come in any order and read the same row")
        void memberOrder() {
            var reversed = new LinkedHashMap<String, String>();
            var members = members();
            members.keySet().stream().toList().reversed().forEach(key -> reversed.put(key, members.get(key)));

            assertEquals(row(), ScopeSessionRowCodec.read(FILE, document(reversed)));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "format_version {0}")
        @CsvSource({"2, FORMAT_NEWER", "0, FORMAT_OUTDATED"})
        @DisplayName("a row of another format version is refused as such, with the version found")
        void otherVersion(int version, Outcome outcome) {
            var members = members();
            members.put("format_version", Integer.toString(version));

            var refusal = assertRefused(outcome, document(members));

            assertEquals(OptionalInt.of(version), refusal.found());
        }

        @ParameterizedTest(name = "without {0}")
        @MethodSource("de.planmarshall.core.store.ScopeSessionRowCodecTest#requiredMembers")
        @DisplayName("a row without a required member is refused naming it")
        void missingMember(String member) {
            var members = members();
            members.remove(member);

            var refusal = assertRefused(Outcome.UNREADABLE, document(members));

            assertTrue(refusal.getMessage().contains(member), refusal.getMessage());
        }

        @Test
        @DisplayName("an undeclared member is refused naming it")
        void undeclaredMember() {
            var members = members();
            members.put("checkout_path", "\"/home/user/repo\"");

            var refusal = assertRefused(Outcome.UNREADABLE, document(members));

            assertTrue(refusal.getMessage().contains("checkout_path"), refusal.getMessage());
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                unknown client                  | client                 | "cursor"
                client as null                  | client                 | null
                unknown detected client         | client_detected        | "cursor"
                lower-case match tier           | environment_match_tier | "matched"
                unknown model family            | model_family           | "llama"
                unknown model source            | model_source           | "guess"
                empty client build              | client_build           | ""
                empty model id                  | model_id               | ""
                empty runtime version           | pm_mcp_version         | ""
                empty bundle version            | bundle_version         | ""
                session id with a space         | client_session_id      | "a b"
                project id upper case           | project_id             | "PM-MCP"
                generation id without sg-       | generation_id          | "0123456789abcdef"
                generation id too short         | generation_id          | "sg-0123"
                unknown entered_from            | entered_from           | "clone"
                instant with an offset          | entered_at             | "2026-10-13T10:00:00+02:00"
                version as string               | format_version         | "1"
                """)
        @DisplayName("a member outside its one shape is refused")
        void memberShape(String name, String member, String value) {
            var members = members();
            members.put(member, value);

            assertRefused(Outcome.UNREADABLE, document(members));
        }

        @Test
        @DisplayName("a client session id of 128 characters is read, one of 129 refused")
        void sessionIdLength() {
            var longest = members();
            longest.put("client_session_id", "\"" + "a".repeat(128) + "\"");
            var tooLong = members();
            tooLong.put("client_session_id", "\"" + "a".repeat(129) + "\"");

            assertAll(
                    () -> assertEquals("a".repeat(128), ScopeSessionRowCodec.read(FILE, document(longest))
                            .clientSessionId()),
                    () -> assertRefused(Outcome.UNREADABLE, document(tooLong)));
        }

        @Test
        @DisplayName("worktree_of on an entry from the main checkout is refused")
        void worktreeOfOnMainCheckout() {
            var members = members();
            members.put("worktree_of", "\"a-plan\"");

            assertRefused(Outcome.UNREADABLE, document(members));
        }

        @ParameterizedTest(name = "worktree_of {0}")
        @CsvSource(delimiter = '|', textBlock = """
                absent         |
                a path         | "/home/user/repo/.marshall/local/worktrees/a-plan"
                null           | null
                """)
        @DisplayName("an entry from a worktree without the name of its worktree is refused")
        void worktreeWithoutName(String name, String value) {
            var members = members();
            members.put("entered_from", "\"worktree\"");
            if (value != null) {
                members.put("worktree_of", value);
            }

            assertRefused(Outcome.UNREADABLE, document(members));
        }

        @Test
        @DisplayName("a model family or id without a model source, and a model source without a family, are refused")
        void modelRules() {
            var familyWithoutSource = members();
            familyWithoutSource.put("model_source", "null");
            var idWithoutSource = members();
            idWithoutSource.put("model_source", "null");
            idWithoutSource.put("model_family", "null");
            var sourceWithoutFamily = members();
            sourceWithoutFamily.put("model_family", "null");

            assertAll(
                    () -> assertRefused(Outcome.UNREADABLE, document(familyWithoutSource)),
                    () -> assertRefused(Outcome.UNREADABLE, document(idWithoutSource)),
                    () -> assertRefused(Outcome.UNREADABLE, document(sourceWithoutFamily)));
        }

        @Test
        @DisplayName("a member twice and content after the row are refused")
        void duplicateAndTrailing() {
            var once = new String(document(members()), UTF_8);
            var twice = once.replace("\"client\":\"claude\",", "\"client\":\"claude\",\"client\":\"claude\",");

            assertAll(
                    () -> assertRefused(Outcome.UNREADABLE, twice.getBytes(UTF_8)),
                    () -> assertRefused(Outcome.UNREADABLE, (once + once).getBytes(UTF_8)));
        }
    }

    @Test
    @DisplayName("a row is built only with its required components")
    void requiredComponents() {
        var withoutClient = row().toBuilder().client(null);
        var withoutGeneration = row().toBuilder().generationId(null);
        var withoutInstant = row().toBuilder().enteredAt(null);

        assertAll(
                () -> assertThrows(NullPointerException.class, withoutClient::build),
                () -> assertThrows(NullPointerException.class, withoutGeneration::build),
                () -> assertThrows(NullPointerException.class, withoutInstant::build));
    }

    @Test
    @DisplayName("the client of two rows differs only when the declared client does")
    void clientDiffers() {
        var other = row().toBuilder().client(Client.CODEX).clientSessionId("sess-2").build();
        var sameClient = row().toBuilder().clientSessionId("sess-2").clientDetected(Client.OPENCODE).build();

        assertAll(
                () -> assertTrue(other.clientDiffersFrom(row())),
                () -> assertFalse(sameClient.clientDiffersFrom(row())));
    }
}
