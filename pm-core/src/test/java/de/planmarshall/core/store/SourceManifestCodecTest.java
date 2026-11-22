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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.SourceManifest.Kind;
import de.planmarshall.core.store.SourceManifest.Source;
import de.planmarshall.core.store.SourceManifest.SourceFile;
import de.planmarshall.core.store.SourceManifest.Trust;
import de.planmarshall.core.store.SourceManifest.Verdict;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The source manifest {@code sources/sources.json} (Filesystem Layout § The Source Directory): written and read back
 * byte for byte, every source with its files, trust and verdict, and every other shape refused as a
 * {@link StoreRefusal} naming the file. The member names, the kind, trust and verdict values and the source-id form are
 * wire contracts; ids, digests, sizes and instants are generated.
 */
@EnableGeneratorController
@DisplayName("Source manifest codec")
class SourceManifestCodecTest {

    // Fixture path: the file a refusal names.
    private static final Path FILE = Path.of("/base/projects/pm-mcp-core/pending/ent-0123456789abcdef/sources/sources.json");

    private String sourceId;
    private String digest;
    private String path;
    private Instant addedAt;
    private long size;

    @BeforeEach
    void generate() {
        sourceId = "source_" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
        digest = Generators.strings("0123456789abcdef", 64, 64).next();
        path = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT) + ".md";
        addedAt = Instant.ofEpochSecond(Generators.longs(1_800_000_000L, 1_900_000_000L).next());
        size = Generators.longs(0, 100_000L).next();
    }

    private SourceFile file() {
        return new SourceFile(path, size, digest);
    }

    private Source trusted() {
        return new Source(sourceId, Kind.DESCRIPTION, Generators.letterStrings(3, 20).next(), "operator",
                Trust.TRUSTED, List.of(file()), addedAt, null, null);
    }

    private Source untrusted() {
        return new Source(sourceId + "x", Kind.ISSUE, "https://github.com/example/repo/issues/42", "forge",
                Trust.UNTRUSTED, List.of(file()), addedAt, Verdict.CLEAN, null);
    }

    private static byte[] document(String... lines) {
        return ("{" + String.join(",", lines) + "}").getBytes(UTF_8);
    }

    private static StoreRefusal assertRefused(byte[] document) {
        return assertThrows(StoreRefusal.class, () -> SourceManifestCodec.read(FILE, document),
                "the manifest should be refused");
    }

    private String source(String kind, String trust, String verdict, String verdictReason) {
        return "{\"source_id\":\"%s\",\"kind\":\"%s\",\"reference\":\"ref\",\"origin\":\"operator\","
                .formatted(sourceId, kind)
                + "\"trust\":\"%s\",\"files\":[{\"path\":\"%s\",\"size\":1,\"sha256\":\"%s\"}],"
                .formatted(trust, path, digest)
                + "\"added_at\":\"%s\",\"verdict\":%s,\"verdict_reason\":%s}"
                .formatted(addedAt, verdict, verdictReason);
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("a manifest with a trusted and an untrusted source is written and read again byte for byte")
        void byteForByte() {
            var manifest = new SourceManifest(3, List.of(trusted(), untrusted()));

            var written = SourceManifestCodec.write(manifest);

            assertAll(
                    () -> assertEquals(manifest, SourceManifestCodec.read(FILE, written),
                            "the manifest should be read back equal"),
                    () -> assertArrayEquals(written,
                            SourceManifestCodec.write(SourceManifestCodec.read(FILE, written)),
                            "the manifest should be written byte for byte"));
        }

        @Test
        @DisplayName("an accepted source keeps the operator's reason, and an unscreened one has none")
        void verdicts() {
            var accepted = new Source(sourceId, Kind.PR, "ref", "forge", Trust.UNTRUSTED, List.of(file()), addedAt,
                    Verdict.ACCEPTED, "the operator read the diff");
            var unscreened = new Source(sourceId + "x", Kind.LESSON, "ref", "operator", Trust.UNTRUSTED,
                    List.of(file()), addedAt, Verdict.UNSCREENED, null);

            var read = SourceManifestCodec.read(FILE, SourceManifestCodec.write(
                    new SourceManifest(1, List.of(accepted, unscreened))));

            assertAll(
                    () -> assertEquals(Optional.of(accepted), read.source(sourceId),
                            "the accepted source should keep its verdict and reason"),
                    () -> assertEquals("the operator read the diff",
                            read.source(sourceId).orElseThrow().verdictReason(),
                            "the accepted source should keep the operator's reason"),
                    () -> assertEquals(Verdict.UNSCREENED, read.source(sourceId + "x").orElseThrow().verdict(),
                            "the unscreened source should keep its verdict"));
        }

        @Test
        @DisplayName("an empty manifest has revision zero and nothing to add twice")
        void empty() {
            var empty = SourceManifest.empty();
            var added = empty.withSource(trusted());

            assertAll(
                    () -> assertEquals(0, empty.revision(), "an empty manifest starts at revision zero"),
                    () -> assertTrue(empty.sources().isEmpty(), "an empty manifest holds no source"),
                    () -> assertEquals(1, added.revision(), "adding a source advances the revision"),
                    () -> assertThrows(IllegalArgumentException.class, () -> added.withSource(trusted()),
                            "a source id is used once"));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "format_version {0}")
        @CsvSource({"2, FORMAT_NEWER", "0, FORMAT_OUTDATED"})
        @DisplayName("a manifest of another format version is refused as such, with the version found")
        void otherVersion(int version, Outcome outcome) {
            var refusal = assertRefused(document("\"format_version\":" + version, "\"revision\":0",
                    "\"sources\":[]"));

            assertAll(
                    () -> assertEquals(outcome, refusal.outcome(), "the refusal should carry its outcome"),
                    () -> assertEquals(OptionalInt.of(version), refusal.found(),
                            "the refusal should carry the version found"));
        }

        @ParameterizedTest(name = "without {0}")
        @ValueSource(strings = {"revision", "sources"})
        @DisplayName("a manifest without a required member is refused naming it")
        void missingMember(String member) {
            var members = new LinkedHashMap<String, String>();
            members.put("format_version", "1");
            members.put("revision", "0");
            members.put("sources", "[]");
            members.remove(member);

            var refusal = assertRefused(document(members.entrySet().stream()
                    .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                    .toArray(String[]::new)));

            assertTrue(refusal.getMessage().contains(member), "the refusal should name the missing member");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                undeclared member        | "revision":0,"sources":[],"note":{}
                a source without files   | "revision":0,"sources":[{"source_id":"s","kind":"description","reference":"r","origin":"o","trust":"trusted","files":[],"added_at":"2026-10-13T08:00:00Z","verdict":null,"verdict_reason":null}]
                an untrusted source without a verdict | "revision":0,"sources":[{"source_id":"s","kind":"issue","reference":"r","origin":"o","trust":"untrusted","files":[{"path":"a.md","size":1,"sha256":"0000000000000000000000000000000000000000000000000000000000000000"}],"added_at":"2026-10-13T08:00:00Z","verdict":null,"verdict_reason":null}]
                a trusted source with a verdict | "revision":0,"sources":[{"source_id":"s","kind":"description","reference":"r","origin":"o","trust":"trusted","files":[{"path":"a.md","size":1,"sha256":"0000000000000000000000000000000000000000000000000000000000000000"}],"added_at":"2026-10-13T08:00:00Z","verdict":"clean","verdict_reason":null}]
                an accepted source without a reason | "revision":0,"sources":[{"source_id":"s","kind":"issue","reference":"r","origin":"o","trust":"untrusted","files":[{"path":"a.md","size":1,"sha256":"0000000000000000000000000000000000000000000000000000000000000000"}],"added_at":"2026-10-13T08:00:00Z","verdict":"accepted","verdict_reason":null}]
                a digest that is no SHA-256 | "revision":0,"sources":[{"source_id":"s","kind":"description","reference":"r","origin":"o","trust":"trusted","files":[{"path":"a.md","size":1,"sha256":"ABC"}],"added_at":"2026-10-13T08:00:00Z","verdict":null,"verdict_reason":null}]
                a path that leaves the directory | "revision":0,"sources":[{"source_id":"s","kind":"description","reference":"r","origin":"o","trust":"trusted","files":[{"path":"../a.md","size":1,"sha256":"0000000000000000000000000000000000000000000000000000000000000000"}],"added_at":"2026-10-13T08:00:00Z","verdict":null,"verdict_reason":null}]
                a source id of another form | "revision":0,"sources":[{"source_id":"Source 1","kind":"description","reference":"r","origin":"o","trust":"trusted","files":[{"path":"a.md","size":1,"sha256":"0000000000000000000000000000000000000000000000000000000000000000"}],"added_at":"2026-10-13T08:00:00Z","verdict":null,"verdict_reason":null}]
                a source id used twice | "revision":0,"sources":[{"source_id":"s","kind":"description","reference":"r","origin":"o","trust":"trusted","files":[{"path":"a.md","size":1,"sha256":"0000000000000000000000000000000000000000000000000000000000000000"}],"added_at":"2026-10-13T08:00:00Z","verdict":null,"verdict_reason":null},{"source_id":"s","kind":"description","reference":"r","origin":"o","trust":"trusted","files":[{"path":"a.md","size":1,"sha256":"0000000000000000000000000000000000000000000000000000000000000000"}],"added_at":"2026-10-13T08:00:00Z","verdict":null,"verdict_reason":null}]
                """)
        @DisplayName("a manifest outside its one shape is refused (the shapes are the schema's contract)")
        void shape(String name, String members) {
            assertRefused(("{\"format_version\":1," + members + "}").getBytes(UTF_8));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                an unknown kind    | "revision":0,"sources":[%s]
                an unknown trust   | "revision":0,"sources":[%s]
                an unknown verdict | "revision":0,"sources":[%s]
                """)
        @DisplayName("a closed value outside its set is refused naming the value")
        void closedValue(String name, String members) {
            var kind = "an unknown kind".equals(name) ? source("task", "trusted", "null", "null")
                    : "an unknown trust".equals(name) ? source("description", "maybe", "null", "null")
                    : source("issue", "untrusted", "\"half\"", "null");

            assertRefused(document("\"format_version\":1", members.formatted(kind)));
        }
    }

    @Test
    @DisplayName("the member names of the manifest are the wire contract")
    void members() {
        assertEquals(List.of("format_version", "revision", "sources"), SourceManifestCodec.MEMBERS,
                "the members should be the document's members");
    }
}
