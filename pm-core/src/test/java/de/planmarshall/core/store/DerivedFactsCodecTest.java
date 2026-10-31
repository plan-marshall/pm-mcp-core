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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The strict codec of {@code derived-facts.json} (Store Schemas § Derived Facts Store; PM-EXT-6, PM-IMPL-2): the
 * document round-trips, the canonical bytes are written again byte for byte, and every document outside the one shape
 * of the store is refused as a {@link StoreRefusal} naming the file, never as an empty store.
 */
@EnableGeneratorController
@DisplayName("Derived facts codec")
class DerivedFactsCodecTest {

    private static final Path FILE = Path.of("/pm-base/projects/sample/derived-facts.json");

    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    private static final String DIGEST_C = "c".repeat(64);

    private static final String MODULE_VALUE_JSON = "{\"descriptor_tree_hash\":\"" + DIGEST_A
            + "\",\"modules\":[{\"module_id\":\"pm-core\",\"build_system\":\"maven\",\"path\":\".\","
            + "\"facts\":{\"count\":1,\"ratio\":1.50}}],\"diagnostics\":[]}";

    private static final String GOLDEN = "{\"format_version\":1,\"facts\":{"
            + "\"module_discovery\":{\"kind\":\"module_discovery\",\"value\":" + MODULE_VALUE_JSON + ","
            + "\"source\":{\"derivation\":\"module-discovery\",\"class\":\"local\"},"
            + "\"input_digest\":\"" + DIGEST_B + "\",\"derived_at\":\"2026-10-31T10:00:00Z\","
            + "\"state\":\"current\",\"failure\":null},"
            + "\"cost_calibration:plan-1\":{\"kind\":\"cost_calibration\",\"value\":null,"
            + "\"source\":{\"derivation\":\"record-metrics\",\"class\":\"local\"},"
            + "\"input_digest\":\"" + DIGEST_C + "\",\"derived_at\":\"2026-10-31T10:00:00Z\","
            + "\"state\":\"failed\",\"failure\":{\"reason\":\"no metrics\",\"at\":\"2026-10-31T10:00:01Z\"}}}}";

    private static final String MINIMAL = "{\"format_version\":1,\"facts\":{\"module_discovery\":{"
            + "\"kind\":\"module_discovery\",\"value\":{},\"source\":{\"derivation\":\"derive1\",\"class\":\"local\"},"
            + "\"input_digest\":\"" + DIGEST_A + "\",\"derived_at\":\"2026-10-31T10:00:00Z\","
            + "\"state\":\"current\",\"failure\":null}}}";

    private static DerivedFacts store() {
        var facts = new LinkedHashMap<DerivedFactKey, DerivedFact>();
        facts.put(new DerivedFactKey(DerivedFactKind.MODULE_DISCOVERY, null), DerivedFact.builder()
                .kind(DerivedFactKind.MODULE_DISCOVERY)
                .value(JsonValue.parse(MODULE_VALUE_JSON.getBytes(UTF_8)))
                .source(new DerivedFact.Source("module-discovery", DerivedFact.DerivationClass.LOCAL))
                .inputDigest(DIGEST_B).derivedAt(Instant.parse("2026-10-31T10:00:00Z"))
                .state(DerivedFact.State.CURRENT).build());
        facts.put(new DerivedFactKey(DerivedFactKind.COST_CALIBRATION, "plan-1"), DerivedFact.builder()
                .kind(DerivedFactKind.COST_CALIBRATION).value(null)
                .source(new DerivedFact.Source("record-metrics", DerivedFact.DerivationClass.LOCAL))
                .inputDigest(DIGEST_C).derivedAt(Instant.parse("2026-10-31T10:00:00Z"))
                .state(DerivedFact.State.FAILED)
                .failure(new DerivedFact.Failure("no metrics", Instant.parse("2026-10-31T10:00:01Z"))).build());
        return new DerivedFacts(facts);
    }

    private static String failedEntry(String reason) {
        return "{\"kind\":\"module_discovery\",\"value\":null,"
                + "\"source\":{\"derivation\":\"derive1\",\"class\":\"local\"},"
                + "\"input_digest\":\"" + DIGEST_A + "\",\"derived_at\":\"2026-10-31T10:00:00Z\","
                + "\"state\":\"failed\",\"failure\":{\"reason\":\"" + reason
                + "\",\"at\":\"2026-10-31T10:00:00Z\"}}";
    }

    private static String failedDocument(String entry) {
        return "{\"format_version\":1,\"facts\":{\"module_discovery\":" + entry + "}}";
    }

    private static DerivedFacts read(String document) {
        return DerivedFactsCodec.read(FILE, document.getBytes(UTF_8));
    }

    private static StoreRefusal refused(String document) {
        return assertThrows(StoreRefusal.class, () -> read(document), "The document is refused");
    }

    @Test
    @DisplayName("the canonical document is written byte for byte")
    void canonicalDocumentIsWrittenByteForByte() {
        var written = new String(DerivedFactsCodec.write(store()), UTF_8);

        assertEquals(GOLDEN, written, "The writer writes the canonical document");
    }

    @Test
    @DisplayName("read(write(x)) is x and write(read(b)) is b for canonical bytes")
    void documentRoundTrips() {
        var document = GOLDEN.getBytes(UTF_8);

        assertAll(
                () -> assertEquals(store(), read(GOLDEN), "The golden document reads into the store"),
                () -> assertEquals(store(), DerivedFactsCodec.read(FILE, DerivedFactsCodec.write(store())),
                        "read(write(x)) is x"),
                () -> assertTrue(Arrays.equals(document,
                                DerivedFactsCodec.write(DerivedFactsCodec.read(FILE, document))),
                        "write(read(b)) is b for canonical bytes"));
    }

    @Test
    @DisplayName("a number spelled with an exponent or a negative zero is written canonically once")
    void numberSpellingIsNormalizedOnce() {
        var exponentSpelled = MINIMAL.replace("\"value\":{}", "\"value\":{\"ratio\":1e2,\"zero\":-0}");
        var canonical = "{\"format_version\":1,\"facts\":{\"module_discovery\":{\"kind\":\"module_discovery\","
                + "\"value\":{\"ratio\":1e+2,\"zero\":0},"
                + "\"source\":{\"derivation\":\"derive1\",\"class\":\"local\"},"
                + "\"input_digest\":\"" + DIGEST_A + "\",\"derived_at\":\"2026-10-31T10:00:00Z\","
                + "\"state\":\"current\",\"failure\":null}}}";

        var written = new String(DerivedFactsCodec.write(read(exponentSpelled)), UTF_8);

        assertAll(
                () -> assertEquals(canonical, written, "The writer states the canonical spelling"),
                () -> assertEquals(canonical, new String(DerivedFactsCodec.write(read(canonical)), UTF_8),
                        "The canonical spelling is written again unchanged"));
    }

    @Test
    @DisplayName("an empty store round-trips and a failure member may be absent")
    void emptyStoreAndAbsentFailureRoundTrip() {
        var withoutFailure = MINIMAL.replace(",\"failure\":null", "");

        assertAll(
                () -> assertEquals(DerivedFacts.empty(), read("{\"format_version\":1,\"facts\":{}}"),
                        "The empty store round-trips"),
                () -> assertEquals(read(MINIMAL), read(withoutFailure),
                        "An absent failure reads as a current entry"),
                () -> assertEquals("{\"format_version\":1,\"facts\":{}}",
                        new String(DerivedFactsCodec.write(DerivedFacts.empty()), UTF_8),
                        "The empty store is written canonically"));
    }

    @Test
    @DisplayName("a failed entry with a generated reason round-trips")
    void failedEntryWithGeneratedReasonRoundTrips() {
        var reason = Generators.letterStrings(1, 40).next();

        var document = failedDocument(failedEntry(reason));

        var fact = read(document).fact(new DerivedFactKey(DerivedFactKind.MODULE_DISCOVERY, null)).orElseThrow();

        assertAll(
                () -> assertEquals(DerivedFact.State.FAILED, fact.state(), "The entry is failed"),
                () -> assertEquals(reason, fact.failure().reason(), "The failure reason round-trips"),
                () -> assertEquals(document, new String(DerivedFactsCodec.write(read(document)), UTF_8),
                        "The failed entry is written canonically"));
    }

    @Test
    @DisplayName("another format version is refused")
    void anotherFormatVersionIsRefused() {
        var refusal = refused(MINIMAL.replace("\"format_version\":1", "\"format_version\":2"));

        assertEquals(Outcome.FORMAT_NEWER, refusal.outcome(), "A newer format version is refused");
    }

    @Test
    @DisplayName("an unknown member of the document or of an entry is refused")
    void unknownMemberIsRefused() {
        assertAll(
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace("\"facts\":{", "\"extra\":1,\"facts\":{")).outcome(),
                        "An unknown document member is refused"),
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace("\"kind\":\"module_discovery\"",
                                "\"kind\":\"module_discovery\",\"extra\":1")).outcome(),
                        "An unknown entry member is refused"));
    }

    @Test
    @DisplayName("a member named twice is refused")
    void duplicateMemberIsRefused() {
        var refusal = refused("{\"format_version\":1,\"format_version\":1,\"facts\":{}}");

        assertEquals(Outcome.UNREADABLE, refusal.outcome(), "A duplicate document member is refused");
    }

    @Test
    @DisplayName("a missing required member is refused")
    void missingRequiredMemberIsRefused() {
        assertAll(
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace(",\"state\":\"current\"", "")).outcome(),
                        "A missing entry member is refused"),
                () -> assertEquals(Outcome.UNREADABLE,
                        refused("{\"format_version\":1}").outcome(),
                        "A missing document member is refused"));
    }

    @Test
    @DisplayName("a key outside the grammar or with a kind unlike its entry is refused")
    void keyRefusals() {
        assertAll(
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace("\"module_discovery\":{", "\"Bad\":{")).outcome(),
                        "A key outside the grammar is refused"),
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace("\"kind\":\"module_discovery\"",
                                "\"kind\":\"cost_calibration\"")).outcome(),
                        "A key of another kind than its entry is refused"),
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace("\"module_discovery\":{", "\"not_a_kind\":{")).outcome(),
                        "A key whose kind part names no kind is refused"));
    }

    @Test
    @DisplayName("a bad digest, an offset instant and a reason of 501 code points are refused")
    void valueBoundsAreRefused() {
        assertAll(
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace(DIGEST_A, DIGEST_A.substring(1))).outcome(),
                        "A digest of 63 digits is refused"),
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace("2026-10-31T10:00:00Z", "2026-10-31T11:00:00+01:00")).outcome(),
                        "An offset-spelled instant is refused"),
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(failedDocument(failedEntry("r".repeat(DerivedFact.Failure.MAX_REASON + 1)))).outcome(),
                        "A failure reason of 501 code points is refused"));
    }

    @Test
    @DisplayName("a value or a failure outside the state's shape is refused")
    void stateShapesAreRefused() {
        assertAll(
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace("\"value\":{}", "\"value\":null")).outcome(),
                        "A current entry without a value is refused"),
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace("\"state\":\"current\"", "\"state\":\"stale\"")).outcome(),
                        "A stale entry without a failure is refused"),
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace("\"state\":\"current\"", "\"state\":\"unknown\"")).outcome(),
                        "An undeclared state is refused"));
    }

    @Test
    @DisplayName("content after the document and an undeclared source class are refused")
    void trailingContentAndClosedValuesAreRefused() {
        assertAll(
                () -> assertEquals(Outcome.UNREADABLE, refused(MINIMAL + "{}").outcome(),
                        "Content after the document is refused"),
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace("\"class\":\"local\"", "\"class\":\"remote\"")).outcome(),
                        "An undeclared source class is refused"),
                () -> assertEquals(Outcome.UNREADABLE,
                        refused(MINIMAL.replace("\"kind\":\"module_discovery\",\"value\"",
                                "\"kind\":\"not_a_kind\",\"value\"")).outcome(),
                        "An undeclared kind is refused"));
    }

    @Test
    @DisplayName("a refusal names the file it read")
    void refusalNamesTheFile() {
        var refusal = refused(MINIMAL + "{}");

        assertEquals(FILE, refusal.file(), "The refusal names the file");
    }
}
