/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The manifest of a corpus source (PM-WORK-6): one format version, every declared field exactly once, no undeclared
 * field, and every value in its format; a refusal names the file.
 */
@DisplayName("Source manifest codec")
class SourceManifestCodecTest {

    private static final Path FILE = Path.of("/corpus/sources/sample-set/manifest.json");
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String HASH = "a".repeat(64);
    private static final String OBJECT_HASH = "b".repeat(64);

    /** The members of a valid manifest, each with its JSON value. */
    private static Map<String, String> members() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("name", "\"sample-set\"");
        members.put("repository", "\"https://github.com/example/sample-set\"");
        members.put("commit", "\"" + COMMIT + "\"");
        members.put("licence", "\"CC-BY-4.0\"");
        members.put("files", "[" + file("data/test.csv", HASH, false) + "," + file("data/big.jsonl", OBJECT_HASH, true)
                + "]");
        return members;
    }

    private static String file(String path, String sha256, boolean lfs) {
        return "{\"path\":\"%s\",\"sha256\":\"%s\",\"lfs\":%s}".formatted(path, sha256, lfs);
    }

    private static byte[] document(Map<String, String> members) {
        return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}")).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] with(String field, String value) {
        var members = members();
        members.put(field, value);
        return document(members);
    }

    private static StoreRefusal refusalOf(byte[] document) {
        return assertThrows(StoreRefusal.class, () -> SourceManifestCodec.read(FILE, document));
    }

    private static void assertUnreadable(StoreRefusal refusal, String detail) {
        assertAll(
                () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                () -> assertEquals(FILE, refusal.file(), "the refusal names the file"),
                () -> assertTrue(refusal.getMessage().startsWith(FILE.toString()), refusal::getMessage),
                () -> assertTrue(refusal.getMessage().contains(detail), refusal::getMessage));
    }

    @Nested
    @DisplayName("a valid manifest")
    class Valid {

        @Test
        @DisplayName("reads with every member")
        void reads() {
            var manifest = SourceManifestCodec.read(FILE, document(members()));

            assertAll(
                    () -> assertEquals("sample-set", manifest.name()),
                    () -> assertEquals(URI.create("https://github.com/example/sample-set"), manifest.repository()),
                    () -> assertEquals(COMMIT, manifest.commit()),
                    () -> assertEquals("CC-BY-4.0", manifest.licence()),
                    () -> assertEquals(List.of(new SourceFile("data/test.csv", HASH, false),
                            new SourceFile("data/big.jsonl", OBJECT_HASH, true)), manifest.files()),
                    () -> assertEquals(new SourceFile("data/big.jsonl", OBJECT_HASH, true),
                            manifest.file("data/big.jsonl").orElseThrow()),
                    () -> assertTrue(manifest.file("data/other.csv").isEmpty()));
        }

        @Test
        @DisplayName("is written and read back equal, and written again byte for byte")
        void roundTrip() {
            var manifest = SourceManifestCodec.read(FILE, document(members()));

            var written = SourceManifestCodec.write(manifest);
            var reread = SourceManifestCodec.read(FILE, written);

            assertAll(
                    () -> assertEquals(manifest, reread),
                    () -> assertArrayEquals(written, SourceManifestCodec.write(reread)));
        }
    }

    @Nested
    @DisplayName("the format version")
    class FormatVersion {

        @Test
        @DisplayName("a newer format version is refused as newer")
        void newer() {
            var refusal = refusalOf(with("format_version", "2"));

            assertAll(
                    () -> assertEquals(Outcome.FORMAT_NEWER, refusal.outcome()),
                    () -> assertEquals(FILE, refusal.file()));
        }

        @Test
        @DisplayName("a missing format version is refused as unreadable")
        void missing() {
            var members = members();
            members.remove("format_version");

            assertUnreadable(refusalOf(document(members)), "'format_version' is missing");
        }
    }

    @Nested
    @DisplayName("the declared fields")
    class DeclaredFields {

        @Test
        @DisplayName("an undeclared field is refused")
        void undeclared() {
            assertUnreadable(refusalOf(with("homepage", "\"https://example.org\"")), "undeclared field 'homepage'");
        }

        @Test
        @DisplayName("a field twice is refused")
        void twice() {
            var text = new String(document(members()), StandardCharsets.UTF_8)
                    .replace("\"licence\":", "\"licence\":\"MIT\",\"licence\":");

            assertUnreadable(refusalOf(text.getBytes(StandardCharsets.UTF_8)), "field 'licence' twice");
        }

        @ParameterizedTest(name = "missing {0}")
        @MethodSource("de.planmarshall.core.role.corpus.SourceManifestCodecTest#requiredFields")
        @DisplayName("a missing field is refused")
        void missing(String field) {
            var members = members();
            members.remove(field);

            assertUnreadable(refusalOf(document(members)), "field '%s' of the manifest is missing".formatted(field));
        }

        @Test
        @DisplayName("an undeclared field of a file is refused")
        void undeclaredFileField() {
            var refusal = refusalOf(with("files",
                    "[{\"path\":\"a.csv\",\"sha256\":\"" + HASH + "\",\"lfs\":false,\"size\":3}]"));

            assertUnreadable(refusal, "undeclared field 'size'");
        }
    }

    static Stream<String> requiredFields() {
        return Stream.of("name", "repository", "commit", "licence", "files");
    }

    static Stream<Arguments> malformedValues() {
        return Stream.of(
                Arguments.of("an http repository", "repository", "\"http://github.com/example/sample-set\"",
                        "'repository'"),
                Arguments.of("a repository with a user", "repository", "\"https://user@github.com/example/s\"",
                        "'repository'"),
                Arguments.of("a relative repository", "repository", "\"github.com/example/sample-set\"",
                        "'repository'"),
                Arguments.of("a short commit", "commit", "\"0123456\"", "'commit'"),
                Arguments.of("an upper-case commit", "commit", "\"" + COMMIT.toUpperCase() + "\"", "'commit'"),
                Arguments.of("a name with upper case", "name", "\"Sample\"", "'name'"),
                Arguments.of("a licence with a space", "licence", "\"CC BY\"", "'licence'"),
                Arguments.of("a short hash", "files", "[" + file("a.csv", "abc", false) + "]", "'sha256'"),
                Arguments.of("a path escaping the repository", "files", "[" + file("../a.csv", HASH, false) + "]",
                        "'path'"),
                Arguments.of("an absolute path", "files", "[" + file("/a.csv", HASH, false) + "]", "'path'"),
                Arguments.of("no file", "files", "[]", "'files'"),
                Arguments.of("a path twice", "files",
                        "[" + file("a.csv", HASH, false) + "," + file("a.csv", OBJECT_HASH, false) + "]",
                        "twice"),
                Arguments.of("a string for lfs", "files", "[{\"path\":\"a.csv\",\"sha256\":\"" + HASH
                        + "\",\"lfs\":\"yes\"}]", "'lfs' must be a boolean"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedValues")
    @DisplayName("a value outside its format is refused naming the file")
    void malformedValue(String name, String field, String value, String detail) {
        assertUnreadable(refusalOf(with(field, value)), detail);
    }

    @Test
    @DisplayName("content after the document is refused")
    void contentAfterDocument() {
        var text = new String(document(members()), StandardCharsets.UTF_8) + "{}";

        assertUnreadable(refusalOf(text.getBytes(StandardCharsets.UTF_8)), "content after the document");
    }
}
