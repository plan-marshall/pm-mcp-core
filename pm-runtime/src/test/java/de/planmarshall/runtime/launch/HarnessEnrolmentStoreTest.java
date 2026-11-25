/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.shortWords;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.runtime.enrolment.EnrolmentHmac;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The harness enrolment store: its HMAC sidecar, the re-baseline of a tampered file and the client ordering.
 *
 * <p>
 * The client ids, versions, digests, instants, models and the HMAC key are generated. The literals that stay are the
 * contract: the store's file names ({@code harnesses.json} and its {@code .hmac} sidecar), the stored JSON documents
 * of the malformed-entry, top-level-member and endpoint-member tests (the store's wire format), the usage figure and
 * event names with their stage results ({@code reported}, {@code missing}, {@code not_applicable}), the role classes of
 * a start context and the file permissions {@code rw-------} the store requires.
 */
@EnableGeneratorController
@DisplayName("Harness enrolment store")
class HarnessEnrolmentStoreTest {

    private static final String USER = System.getProperty("user.name");
    private static final String STORE_FILE = "harnesses.json";
    private static final String SIDECAR_FILE = "harnesses.json.hmac";

    @TempDir
    Path base;

    private HarnessEnrolmentStore store;
    private SecretKey key;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.ofEpochSecond(Generators.integers(0, Integer.MAX_VALUE).next());
        key = new SecretKeySpec(Generators.letterStrings(32, 32).next().getBytes(StandardCharsets.UTF_8),
                EnrolmentKey.ALGORITHM);
        store = new HarnessEnrolmentStore(base, () -> key, new FileLockManager(event -> {
        }), USER);
    }

    @Test
    @DisplayName("writes and reads an enrolment, with the canonical file and its sidecar")
    void writeAndRead() {
        var client = harnessId();
        var version = version();
        store.enrol(enrolment(client, version));
        assertEquals(version, store.find(client).orElseThrow().version(), "the enrolled version is read back");
        assertTrue(Files.exists(base.resolve(STORE_FILE)), "the canonical store file is written");
        assertTrue(Files.exists(base.resolve(SIDECAR_FILE)), "the HMAC sidecar of the store is written");
    }

    @Test
    @DisplayName("a tampered file is refused and never parsed")
    void tampered() throws Exception {
        var client = harnessId();
        store.enrol(enrolment(client, version()));
        // an empty store document in the store's wire format
        Files.writeString(base.resolve(STORE_FILE), "{\"format_version\":1,\"harnesses\":{}}",
                StandardCharsets.UTF_8);
        var refusal = assertThrows(HarnessEnrolmentStore.StoreException.class, () -> store.find(client),
                "a tampered store file is refused");
        assertEquals(HarnessEnrolmentStore.Refusal.TAMPERED, refusal.refusal(), "the refusal names the tampering");
    }

    @Test
    @DisplayName("a new enrolment re-baselines a tampered file with the named client only")
    void rebaseline() throws Exception {
        var client = harnessId();
        var other = client + "-" + harnessId();
        var newer = version() + "." + Generators.integers(0, 99).next();
        store.enrol(enrolment(client, version()));
        store.enrol(enrolment(other, version()));
        Files.writeString(base.resolve(STORE_FILE), "{}", StandardCharsets.UTF_8);
        store.enrol(enrolment(client, newer));
        assertEquals(List.of(client), store.all().stream().map(HarnessEnrolment::harnessId).toList(),
                "only the named client survives the re-baseline");
        assertEquals(newer, store.find(client).orElseThrow().version(), "the re-baselined entry has the new version");
        assertTrue(store.find(other).isEmpty(), "the other client is gone after the re-baseline");
    }

    @Test
    @DisplayName("a new enrolment re-baselines a verified file that does not read as the store")
    void rebaselineUnreadable() throws Exception {
        var client = harnessId();
        var other = client + "-" + harnessId();
        store.enrol(enrolment(client, version()));
        var file = base.resolve(STORE_FILE);
        // format_version is the store's version member; 2 is a version the store does not read
        var bytes = Files.readString(file, StandardCharsets.UTF_8)
                .replace("\"format_version\":1", "\"format_version\":2").getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);
        Files.write(base.resolve(SIDECAR_FILE), EnrolmentHmac.sidecar(key, bytes).write());
        store.enrol(enrolment(other, version()));
        assertEquals(List.of(other), store.all().stream().map(HarnessEnrolment::harnessId).toList(),
                "only the new client is in the re-baselined store");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedEntries")
    @DisplayName("an entry without a required object member is refused, not read as empty")
    void malformedMembers(String name, String tail, String member) throws Exception {
        writeVerifiable(entryDocument(tail));

        var refusal = assertThrows(HarnessEnrolmentStore.StoreException.class, () -> store.find("claude"),
                "a malformed store document is refused");

        assertAll(
                () -> assertEquals(HarnessEnrolmentStore.Refusal.UNREADABLE, refusal.refusal(),
                        "the malformed document is refused as unreadable"),
                () -> assertTrue(refusal.getMessage().contains(member),
                        "the refusal names the malformed member"));
    }

    static Stream<Arguments> malformedEntries() {
        return Stream.of(
                Arguments.of("the events member is absent", "", "events"),
                Arguments.of("the events member is not an object", ",\"events\":\"not-an-object\"", "events"),
                Arguments.of("the events member is empty", ",\"events\":{\"text\":5}", "text"),
                Arguments.of("the weights member is an array", ",\"events\":{},\"weights\":[]", "weights"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("topLevelDocuments")
    @DisplayName("a missing, null or non-object top-level harnesses is refused, not read as empty")
    void topLevelHarnessesRefused(String name, String document) throws Exception {
        writeVerifiable(document);

        var refusal = assertThrows(HarnessEnrolmentStore.StoreException.class, () -> store.find("claude"),
                "a store without its harnesses object is refused");

        assertAll(
                () -> assertEquals(HarnessEnrolmentStore.Refusal.UNREADABLE, refusal.refusal(),
                        "the store is refused as unreadable"),
                () -> assertTrue(refusal.getMessage().contains("harnesses"),
                        "the refusal names the harnesses member"));
    }

    static Stream<Arguments> topLevelDocuments() {
        return Stream.of(
                Arguments.of("the harnesses member is absent", "{\"format_version\":1}"),
                Arguments.of("the harnesses member is null", "{\"format_version\":1,\"harnesses\":null}"),
                Arguments.of("the harnesses member is an array", "{\"format_version\":1,\"harnesses\":[]}"),
                Arguments.of("the harnesses member is a string", "{\"format_version\":1,\"harnesses\":\"claude\"}"));
    }

    @Test
    @DisplayName("an empty harnesses object is a valid empty store")
    void emptyHarnessesObjectReadsAsEmptyStore() throws Exception {
        writeVerifiable("{\"format_version\":1,\"harnesses\":{}}");

        assertTrue(store.all().isEmpty(), "an empty harnesses object reads as an empty store");
    }

    /** Writes a document and its sidecar the way a verified store file is read: owner-only, HMAC over the bytes. */
    private void writeVerifiable(String document) throws Exception {
        var bytes = document.getBytes(StandardCharsets.UTF_8);
        var file = base.resolve(STORE_FILE);
        var sidecar = base.resolve(SIDECAR_FILE);
        Files.write(file, bytes);
        Files.write(sidecar, EnrolmentHmac.sidecar(key, bytes).write());
        var restricted = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(file, restricted);
        Files.setPosixFilePermissions(sidecar, restricted);
    }

    private void assertUnreadable(String document) throws Exception {
        writeVerifiable(document);
        var refusal = assertThrows(HarnessEnrolmentStore.StoreException.class, () -> store.find("claude"),
                "a malformed store document is refused");
        assertEquals(HarnessEnrolmentStore.Refusal.UNREADABLE, refusal.refusal(),
                "the malformed document is refused as unreadable");
    }

    /**
     * The documents below are fixtures in the store's wire format, kept as literals.
     *
     * @param tail a malformed member appended after {@code usage_report}, before the closing braces
     */
    private static String entryDocument(String tail) {
        return "{\"format_version\":1,\"harnesses\":{\"claude\":{\"executable_path\":\"/usr/local/bin/claude\","
                + "\"sha256\":\"" + "a".repeat(64) + "\",\"version\":\"1.5.0\","
                + "\"enrolled_at\":\"2026-10-24T10:00:00Z\",\"updated_at\":\"2026-10-24T10:00:00Z\","
                + "\"usage_report\":{\"source\":\"declared\",\"figures\":{\"input\":\"reported\"}}" + tail + "}}}";
    }

    @Test
    @DisplayName("an accepted self-update rewrites the version and the digest")
    void selfUpdate() {
        var client = harnessId();
        var newer = version() + "." + Generators.integers(0, 99).next();
        var digest = RuntimeGenerators.sha256Digests().next();
        var seconds = Generators.integers(1, 100_000).next();
        var enrolled = store.enrol(enrolment(client, version()));
        var updated = store.acceptSelfUpdate(client, newer, digest, now.plusSeconds(seconds)).orElseThrow();
        assertEquals(newer, updated.version(), "the self-update rewrites the version");
        assertEquals(digest, updated.sha256(), "the self-update rewrites the digest");
        assertEquals(enrolled.enrolledAt(), updated.enrolledAt(), "the self-update keeps the enrolment instant");
        assertEquals(now.plusSeconds(seconds), updated.updatedAt(), "the self-update records its own instant");
    }

    @Test
    @DisplayName("an unenrol removes only the named client")
    void unenrol() {
        var client = harnessId();
        var other = client + "-" + harnessId();
        store.enrol(enrolment(client, version()));
        store.enrol(enrolment(other, version()));
        assertTrue(store.unenrol(client), "an enrolled client is unenrolled");
        assertFalse(store.unenrol(client), "an unenrolled client is not unenrolled again");
        assertEquals(List.of(other), store.all().stream().map(HarnessEnrolment::harnessId).toList(),
                "only the other client remains enrolled");
    }

    @Test
    @DisplayName("an entry that carries an endpoint member is refused, not read as a program in part")
    void endpointMemberRefused() throws Exception {
        assertUnreadable("{\"format_version\":1,\"harnesses\":{\"hosted-judge\":{\"endpoint\":{\"origin\":"
                + "\"https://judge.example.test\",\"credential_key\":\"judge-token\"},"
                + "\"enrolled_at\":\"2026-10-24T10:00:00Z\",\"updated_at\":\"2026-10-24T10:00:00Z\","
                + "\"usage_report\":{\"source\":\"declared\",\"figures\":{}},\"events\":{}}}}");
        assertUnreadable(entryDocument(",\"events\":{},\"weights\":{},\"endpoint\":{\"origin\":"
                + "\"https://judge.example.test\",\"credential_key\":\"judge-token\"}"));
    }

    @Test
    @DisplayName("the same program entry without the endpoint member is read")
    void programEntryWithoutEndpointMemberRead() throws Exception {
        var bytes = entryDocument(",\"events\":{},\"weights\":{}").getBytes(StandardCharsets.UTF_8);
        var file = base.resolve(STORE_FILE);
        var sidecar = base.resolve(SIDECAR_FILE);
        Files.write(file, bytes);
        Files.write(sidecar, EnrolmentHmac.sidecar(key, bytes).write());
        var restricted = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(file, restricted);
        Files.setPosixFilePermissions(sidecar, restricted);

        // the version of the fixture document in the store's wire format
        assertEquals("1.5.0", store.find("claude").orElseThrow().version(), "the program entry is read");
    }

    private HarnessEnrolment enrolment(String harnessId, String version) {
        return new HarnessEnrolment(harnessId, Path.of("/usr/local/bin/" + harnessId).toAbsolutePath(),
                RuntimeGenerators.sha256Digests().next(), version, now, now,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of("input", "reported")),
                Map.of("text", "reported"), Map.of());
    }

    @Test
    @DisplayName("the report maps the usage stage's figure names to the store's")
    void reportMapsProfileFigureNames() {
        // the figure names of the usage stage and of the store are the mapping under test
        var report = HarnessEnrolment.UsageReport.of(HarnessEnrolment.UsageSource.VERIFIED,
                Map.of("input_tokens", "reported", "output_tokens", "missing"), List.of());

        assertEquals(Map.of("input", "reported", "output", "missing"), report.figures(),
                "the stage's figure names map to the store's");
    }

    @Test
    @DisplayName("the start context of a verified usage report round-trips")
    void startContextRoundTrip() {
        var client = harnessId();
        var model = shortWords().next();
        var report = new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.VERIFIED,
                Map.of("input", "reported", "cache_creation", "not_applicable"), List.of(
                        new HarnessEnrolment.UsageReport.StartContext("writer", model,
                                Generators.longs(1, 1_000_000).next()),
                        new HarnessEnrolment.UsageReport.StartContext("zero_tool", model, null)));
        store.enrol(new HarnessEnrolment(client, Path.of("/usr/local/bin/" + client).toAbsolutePath(),
                RuntimeGenerators.sha256Digests().next(), version(), now, now, report, Map.of("text", "reported"),
                Map.of()));

        assertEquals(report, store.find(client).orElseThrow().usageReport(),
                "the start context of the report round-trips");
    }

    /** @return a client id of the store's pattern: a lower-case letter first, at most 32 characters */
    private static String harnessId() {
        return "c" + shortWords().next();
    }

    private static String version() {
        return Generators.integers(0, 9).next() + "." + Generators.integers(0, 20).next() + "."
                + Generators.integers(0, 20).next();
    }
}
