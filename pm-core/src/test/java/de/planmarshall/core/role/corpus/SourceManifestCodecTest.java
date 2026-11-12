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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.role.corpus.SourceManifest.FetchKind;
import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The manifest of a corpus source (PM-WORK-6): one format version, every declared field exactly once, no undeclared
 * field, and every value in its format; a refusal names the file.
 */
@EnableGeneratorController
@DisplayName("Source manifest codec")
class SourceManifestCodecTest {

    private static final Path FILE = Path.of("/corpus/sources/sample-set/manifest.json");
    private static final String NAME = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String HASH = "%064x".formatted(Generators.longs().next());
    private static final String OBJECT_HASH = "%064x".formatted(Generators.longs().next());

    /** The members of a valid manifest, each with its JSON value. */
    private static Map<String, String> members() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("name", "\"" + NAME + "\"");
        members.put("repository", "\"https://github.com/example/sample-set\"");
        members.put("commit", "\"" + COMMIT + "\"");
        members.put("licence", "\"CC-BY-4.0\"");
        members.put("fetch", "\"git\"");
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
                    () -> assertEquals(NAME, manifest.name()),
                    () -> assertEquals(URI.create("https://github.com/example/sample-set"), manifest.repository()),
                    () -> assertEquals(COMMIT, manifest.commit()),
                    () -> assertEquals("CC-BY-4.0", manifest.licence()),
                    () -> assertEquals(FetchKind.GIT, manifest.fetch()),
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

        @ParameterizedTest(name = "a file without {0}")
        @MethodSource("de.planmarshall.core.role.corpus.SourceManifestCodecTest#fileFields")
        @DisplayName("a file object missing a field is refused, and lfs is never defaulted")
        void missingFileField(String field) {
            var members = new LinkedHashMap<String, String>();
            members.put("path", "\"data/big.jsonl\"");
            members.put("sha256", "\"" + OBJECT_HASH + "\"");
            members.put("lfs", "true");
            members.remove(field);
            var incomplete = members.entrySet().stream()
                    .map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                    .collect(Collectors.joining(",", "{", "}"));

            var refusal = refusalOf(with("files", "[" + file("data/test.csv", HASH, false) + "," + incomplete + "]"));

            assertUnreadable(refusal, "field '%s' of the files[1] is missing".formatted(field));
        }
    }

    static Stream<String> fileFields() {
        return Stream.of("path", "sha256", "lfs");
    }

    static Stream<String> requiredFields() {
        return Stream.of("name", "repository", "commit", "licence", "fetch", "files");
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
                Arguments.of("an unknown fetch", "fetch", "\"ftp\"", "'fetch'"),
                Arguments.of("a short hash", "files", "[" + file("a.csv", "abc", false) + "]", "'sha256'"),
                Arguments.of("a path escaping the repository", "files", "[" + file("../a.csv", HASH, false) + "]",
                        "'path'"),
                Arguments.of("an absolute path", "files", "[" + file("/a.csv", HASH, false) + "]", "'path'"),
                Arguments.of("a .. segment behind a carriage return", "files",
                        "[" + file("docs\\r/../x", HASH, false) + "]", "'path'"),
                Arguments.of("a .. segment behind U+0085", "files", "[" + file("docs\\u0085/../x", HASH, false) + "]",
                        "'path'"),
                Arguments.of("a .. segment behind U+2028", "files", "[" + file("docs\\u2028/../x", HASH, false) + "]",
                        "'path'"),
                Arguments.of("a .. segment behind U+2029", "files", "[" + file("docs/a\\u2029/..", HASH, false) + "]",
                        "'path'"),
                Arguments.of("a name ending in a line feed", "name", "\"sample-set\\n\"", "'name'"),
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

    /** One string member of the manifest: its value at the bound, one code point over, and the refusal of the latter. */
    @Builder
    record Bound(String name, String field, String atBound, String overBound, String refusal) {
    }

    static Stream<Bound> boundedValues() {
        var path = "a".repeat(1024);
        return Stream.of(
                new Bound("name", "name", quoted("a".repeat(64)), quoted("a".repeat(65)),
                        "'name' is longer than 64 characters"),
                new Bound("repository", "repository", quoted("https://h.test/" + "p".repeat(2033)),
                        quoted("https://h.test/" + "p".repeat(2034)), "'repository' is longer than 2048 characters"),
                new Bound("commit", "commit", quoted("a".repeat(40)), quoted("a".repeat(41)),
                        "'commit' is longer than 40 characters"),
                new Bound("licence", "licence", quoted("A".repeat(64)), quoted("A".repeat(65)),
                        "'licence' is longer than 64 characters"),
                new Bound("a file path", "files", "[" + file(path, HASH, false) + "]",
                        "[" + file(path + "a", HASH, false) + "]", "'path' is longer than 1024 characters"),
                new Bound("a file sha256", "files", "[" + file("data/test.csv", "a".repeat(64), false) + "]",
                        "[" + file("data/test.csv", "a".repeat(65), false) + "]",
                        "'sha256' is longer than 64 characters"));
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("boundedValues")
    @DisplayName("a value of exactly the bound reads and one code point over is refused for its length")
    void boundedValue(Bound bound) {
        var atBound = SourceManifestCodec.read(FILE, with(bound.field(), bound.atBound()));

        assertAll(
                () -> assertEquals(atBound, SourceManifestCodec.read(FILE, SourceManifestCodec.write(atBound)),
                        "the value at the bound round-trips unchanged"),
                () -> assertUnreadable(refusalOf(with(bound.field(), bound.overBound())), bound.refusal()));
    }
}
