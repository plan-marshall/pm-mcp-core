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
                Trust.UNTRUSTED, List.of(new SourceFile(path + ".issue.md", size, digest)), addedAt, Verdict.CLEAN,
                null);
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

    private String sourceStoring(String id, String storedPath) {
        return "{\"source_id\":\"%s\",\"kind\":\"description\",\"reference\":\"ref\",\"origin\":\"operator\","
                .formatted(id)
                + "\"trust\":\"trusted\",\"files\":[{\"path\":\"%s\",\"size\":1,\"sha256\":\"%s\"}],"
                .formatted(storedPath, digest)
                + "\"added_at\":\"%s\",\"verdict\":null,\"verdict_reason\":null}".formatted(addedAt);
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
                    List.of(new SourceFile(path + ".lesson.md", size, digest)), addedAt, Verdict.UNSCREENED, null);

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
            var duplicate = trusted();
            assertAll(
                    () -> assertEquals(0, empty.revision(), "an empty manifest starts at revision zero"),
                    () -> assertTrue(empty.sources().isEmpty(), "an empty manifest holds no source"),
                    () -> assertEquals(1, added.revision(), "adding a source advances the revision"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> added.withSource(duplicate),
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
                one file path in two sources | "revision":0,"sources":[{"source_id":"s1","kind":"description","reference":"r","origin":"o","trust":"trusted","files":[{"path":"a.md","size":1,"sha256":"0000000000000000000000000000000000000000000000000000000000000000"}],"added_at":"2026-10-13T08:00:00Z","verdict":null,"verdict_reason":null},{"source_id":"s2","kind":"issue","reference":"r","origin":"o","trust":"untrusted","files":[{"path":"a.md","size":1,"sha256":"0000000000000000000000000000000000000000000000000000000000000000"}],"added_at":"2026-10-13T08:00:00Z","verdict":"clean","verdict_reason":null}]
                a stored path of the manifest's own name | "revision":0,"sources":[{"source_id":"s","kind":"description","reference":"r","origin":"o","trust":"trusted","files":[{"path":"sources.json","size":1,"sha256":"0000000000000000000000000000000000000000000000000000000000000000"}],"added_at":"2026-10-13T08:00:00Z","verdict":null,"verdict_reason":null}]
                """)
        @DisplayName("a manifest outside its one shape is refused (the shapes are the schema's contract)")
        void shape(String name, String members) {
            assertRefused(("{\"format_version\":1," + members + "}").getBytes(UTF_8));
        }

        @Test
        @DisplayName("two sources that name one file path are refused by the stored-twice rule, never carried twice")
        void onePathInTwoSources() {
            var second = new Source(sourceId + "x", Kind.ISSUE, "ref", "forge", Trust.UNTRUSTED, List.of(file()),
                    addedAt, Verdict.CLEAN, null);
            var sources = List.of(trusted(), second);

            var refusal = assertThrows(IllegalArgumentException.class, () -> new SourceManifest(2, sources),
                    "one path in two sources should be refused");

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains(path), "the refusal should name the shared path"),
                    () -> assertTrue(refusal.getMessage().contains("is stored twice"),
                            "the refusal should state the stored-twice rule"));
        }

        @Test
        @DisplayName("a stored path of the manifest's own name is refused, so the manifest is never overwritten")
        void reservedPath() {
            var reserved = new Source(sourceId, Kind.DESCRIPTION, "ref", "operator", Trust.TRUSTED,
                    List.of(new SourceFile(SourceManifestCodec.FILE_NAME, size, digest)), addedAt, null, null);
            var sources = List.of(reserved);

            var refusal = assertThrows(IllegalArgumentException.class, () -> new SourceManifest(1, sources),
                    "the manifest name should be reserved");

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains(SourceManifestCodec.FILE_NAME),
                            "the refusal should name the reserved file"),
                    () -> assertTrue(refusal.getMessage().contains(sourceId),
                            "the refusal should name the source that stored it"));
        }

        @Test
        @DisplayName("two paths that differ only in case are refused by the case rule: one file on a case-insensitive filesystem")
        void caseVariantPaths() {
            var variant = path.toUpperCase(Locale.ROOT);
            var second = new Source(sourceId + "x", Kind.ISSUE, "ref", "forge", Trust.UNTRUSTED,
                    List.of(new SourceFile(variant, size, digest)), addedAt, Verdict.CLEAN, null);
            var sources = List.of(trusted(), second);

            var refusal = assertThrows(IllegalArgumentException.class, () -> new SourceManifest(2, sources),
                    "two paths in one case-fold should be refused");

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains(variant),
                            "the refusal should name the path in its own case"),
                    () -> assertTrue(refusal.getMessage().contains(path),
                            "the refusal should name the path stored before it"),
                    () -> assertTrue(refusal.getMessage().contains("differ only in case"),
                            "the refusal should state the case rule"),
                    () -> assertFalse(refusal.getMessage().contains("normalisation"),
                            "the refusal must not answer with the normalisation rule"));
        }

        @Test
        @DisplayName("two paths whose Unicode case forms agree are refused by the case rule: a final sigma is no other file")
        void unicodeCaseVariantPaths() {
            var base = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
            var capital = base + "-Σ.md";
            var finalSigma = base + "-ς.md";
            var first = new Source(sourceId, Kind.DESCRIPTION, "ref", "operator", Trust.TRUSTED,
                    List.of(new SourceFile(capital, size, digest)), addedAt, null, null);
            var second = new Source(sourceId + "x", Kind.ISSUE, "ref", "forge", Trust.UNTRUSTED,
                    List.of(new SourceFile(finalSigma, size, digest)), addedAt, Verdict.CLEAN, null);
            var sources = List.of(first, second);

            var refusal = assertThrows(IllegalArgumentException.class, () -> new SourceManifest(2, sources),
                    "two Unicode case forms of one path should be refused");

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains(capital),
                            "the refusal should name the path as it was stored"),
                    () -> assertTrue(refusal.getMessage().contains(finalSigma),
                            "the refusal should name the Unicode variant"),
                    () -> assertTrue(refusal.getMessage().contains("differ only in case"),
                            "the refusal should state the case rule"),
                    () -> assertFalse(refusal.getMessage().contains("normalisation"),
                            "the refusal must not answer with the normalisation rule"));
        }

        @Test
        @DisplayName("one name in two Unicode normalisation forms is refused by the normalisation rule: a macOS file system is insensitive to it")
        void normalisationVariantPaths() {
            var base = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
            var composed = base + "-\u00E9.md";
            var decomposed = base + "-e\u0301.md";
            var first = new Source(sourceId, Kind.DESCRIPTION, "ref", "operator", Trust.TRUSTED,
                    List.of(new SourceFile(composed, size, digest)), addedAt, null, null);
            var second = new Source(sourceId + "x", Kind.ISSUE, "ref", "forge", Trust.UNTRUSTED,
                    List.of(new SourceFile(decomposed, size, digest)), addedAt, Verdict.CLEAN, null);
            var sources = List.of(first, second);

            var refusal = assertThrows(IllegalArgumentException.class, () -> new SourceManifest(2, sources),
                    "one name in two normalisation forms should be refused");

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains(composed),
                            "the refusal should name the composed form"),
                    () -> assertTrue(refusal.getMessage().contains(decomposed),
                            "the refusal should name the decomposed form"),
                    () -> assertTrue(refusal.getMessage().contains("Unicode normalisation form"),
                            "the refusal should state the normalisation rule"),
                    () -> assertFalse(refusal.getMessage().contains("in case"),
                            "the refusal must not answer with the case rule"));
        }

        @Test
        @DisplayName("a precomposed and a decomposed name whose folded forms collide only after the second NFC are refused by the case rule")
        void casedNormalisationVariantPaths() {
            var base = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
            // the code points are this pair's contract: NFC(U+0390) is U+0390 while NFC(U+0399 U+0308 U+0301) is
            // U+03AA U+0301, so the two NFC forms differ and the normalisation rule cannot unify them; upper(U+0390) is
            // U+0399 U+0308 U+0301 and upper(U+03AA U+0301) is U+03AA U+0301, equal only through the fold's second
            // NFC step, the one after the case mapping. The pair is the case rule's, never the normalisation rule's
            var small = base + "-\u0390.md";
            var combined = base + "-\u0399\u0308\u0301.md";
            var first = new Source(sourceId, Kind.DESCRIPTION, "ref", "operator", Trust.TRUSTED,
                    List.of(new SourceFile(small, size, digest)), addedAt, null, null);
            var second = new Source(sourceId + "x", Kind.ISSUE, "ref", "forge", Trust.UNTRUSTED,
                    List.of(new SourceFile(combined, size, digest)), addedAt, Verdict.CLEAN, null);
            var sources = List.of(first, second);

            var refusal = assertThrows(IllegalArgumentException.class, () -> new SourceManifest(2, sources),
                    "a pair the case fold re-normalises should be refused");

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains(small),
                            "the refusal should name the precomposed form"),
                    () -> assertTrue(refusal.getMessage().contains(combined),
                            "the refusal should name the combined form"),
                    () -> assertTrue(refusal.getMessage().contains("differ only in case"),
                            "the refusal should state the case rule"),
                    () -> assertFalse(refusal.getMessage().contains("normalisation"),
                            "the refusal must not answer with the normalisation rule"));
        }

        @Test
        @DisplayName("a pair the fold unifies although neither case nor normalisation alone does is refused by the case rule")
        void caseFoldWidthPaths() {
            var base = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
            var ligature = base + "-stra\u00DFe.md";
            var letters = base + "-strasse.md";
            var first = new Source(sourceId, Kind.DESCRIPTION, "ref", "operator", Trust.TRUSTED,
                    List.of(new SourceFile(ligature, size, digest)), addedAt, null, null);
            var second = new Source(sourceId + "x", Kind.ISSUE, "ref", "forge", Trust.UNTRUSTED,
                    List.of(new SourceFile(letters, size, digest)), addedAt, Verdict.CLEAN, null);
            var sources = List.of(first, second);

            var refusal = assertThrows(IllegalArgumentException.class, () -> new SourceManifest(2, sources),
                    "a ligature and its letters should be refused");

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains(ligature),
                            "the refusal should name the ligature"),
                    () -> assertTrue(refusal.getMessage().contains(letters),
                            "the refusal should name the letters"),
                    () -> assertTrue(refusal.getMessage().contains("the case fold maps the full case"),
                            "the refusal should state the width of the case fold"),
                    () -> assertFalse(refusal.getMessage().contains("normalisation"),
                            "the refusal must not answer with the normalisation rule"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"file first", "nested path first"})
        @DisplayName("a file and a path below it are refused by the nesting rule: one stored path is no directory of another")
        void nestedPaths(String order) {
            var below = path + "/inner.md";
            var first = "file first".equals(order) ? path : below;
            var second = "file first".equals(order) ? below : path;
            var sources = List.of(
                    new Source(sourceId, Kind.DESCRIPTION, "ref", "operator", Trust.TRUSTED,
                            List.of(new SourceFile(first, size, digest)), addedAt, null, null),
                    new Source(sourceId + "x", Kind.ISSUE, "ref", "forge", Trust.UNTRUSTED,
                            List.of(new SourceFile(second, size, digest)), addedAt, Verdict.CLEAN, null));

            var refusal = assertThrows(IllegalArgumentException.class, () -> new SourceManifest(2, sources),
                    "a file and a path below it should be refused");

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains(first), "the refusal should name the first path"),
                    () -> assertTrue(refusal.getMessage().contains(second),
                            "the refusal should name the second path"),
                    () -> assertTrue(refusal.getMessage().contains("one file and its directory"),
                            "the refusal should state the nesting rule"));
        }

        @Test
        @DisplayName("the manifest's own name is reserved in every case, so no source overwrites it")
        void reservedNameInAnotherCase() {
            var reserved = new Source(sourceId, Kind.DESCRIPTION, "ref", "operator", Trust.TRUSTED,
                    List.of(new SourceFile("Sources.json", size, digest)), addedAt, null, null);
            var sources = List.of(reserved);

            var refusal = assertThrows(IllegalArgumentException.class, () -> new SourceManifest(1, sources),
                    "the manifest name should be reserved in every case");

            assertTrue(refusal.getMessage().contains("Sources.json"), "the refusal should name the reserved path");
        }

        @Test
        @DisplayName("a path with a NUL is refused as no path of this platform, never carried to the move")
        void invalidCharacters() {
            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> new SourceFile("a\u0000b.md", size, digest), "a NUL in a path should be refused");

            assertTrue(refusal.getMessage().contains("no path of this platform"),
                    "the refusal should name the platform-path rule");
        }

        @Test
        @DisplayName("a stored manifest whose paths differ only in case is refused by the case rule at format version 1")
        void caseVariantStoredManifest() {
            var variant = path.toUpperCase(Locale.ROOT);
            var stored = document("\"format_version\":1", "\"revision\":2",
                    "\"sources\":[" + sourceStoring(sourceId, path) + ","
                            + sourceStoring(sourceId + "x", variant) + "]");

            var refusal = assertRefused(stored);

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(),
                            "a colliding stored manifest is unreadable"),
                    () -> assertEquals(1, refusal.current(), "the manifest keeps format version 1"),
                    () -> assertTrue(refusal.getMessage().contains("differ only in case"),
                            "the refusal should state the case rule"),
                    () -> assertFalse(refusal.getMessage().contains("normalisation"),
                            "the refusal must not answer with the normalisation rule"));
        }

        @Test
        @DisplayName("a stored manifest whose paths differ only in their normalisation form is refused by that rule")
        void normalisationVariantStoredManifest() {
            var composed = path + "-\u00E9.md";
            var decomposed = path + "-e\u0301.md";
            var stored = document("\"format_version\":1", "\"revision\":2",
                    "\"sources\":[" + sourceStoring(sourceId, composed) + ","
                            + sourceStoring(sourceId + "x", decomposed) + "]");

            var refusal = assertRefused(stored);

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(),
                            "a colliding stored manifest is unreadable"),
                    () -> assertTrue(refusal.getMessage().contains("Unicode normalisation form"),
                            "the refusal should state the normalisation rule"),
                    () -> assertFalse(refusal.getMessage().contains("in case"),
                            "the refusal must not answer with the case rule"));
        }

        @Test
        @DisplayName("a stored manifest that names one path twice is refused by the stored-twice rule")
        void storedTwiceStoredManifest() {
            var stored = document("\"format_version\":1", "\"revision\":2",
                    "\"sources\":[" + sourceStoring(sourceId, path) + ","
                            + sourceStoring(sourceId + "x", path) + "]");

            var refusal = assertRefused(stored);

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(),
                            "a colliding stored manifest is unreadable"),
                    () -> assertTrue(refusal.getMessage().contains("is stored twice"),
                            "the refusal should state the stored-twice rule"));
        }

        @Test
        @DisplayName("a stored manifest that names a file and its directory is refused by the nesting rule")
        void nestedPathsStoredManifest() {
            var stored = document("\"format_version\":1", "\"revision\":2",
                    "\"sources\":[" + sourceStoring(sourceId, path) + ","
                            + sourceStoring(sourceId + "x", path + "/inner.md") + "]");

            var refusal = assertRefused(stored);

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(),
                            "a colliding stored manifest is unreadable"),
                    () -> assertTrue(refusal.getMessage().contains("one file and its directory"),
                            "the refusal should state the nesting rule"));
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
        assertEquals(SourceManifestCodec.MEMBERS, List.of("format_version", "revision", "sources"),
                "the members should be the document's members");
    }
}
