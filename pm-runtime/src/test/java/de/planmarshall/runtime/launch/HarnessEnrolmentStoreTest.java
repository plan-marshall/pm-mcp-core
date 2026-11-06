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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.runtime.enrolment.EnrolmentHmac;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The harness enrolment store: its HMAC sidecar, the re-baseline of a tampered file and the client ordering.
 *
 * <p>
 * The client ids, versions, digests, instants, origins, credential keys, models and the HMAC key are generated. The
 * literals that stay are the contract: the store's file names ({@code harnesses.json} and its {@code .hmac} sidecar),
 * the stored JSON documents of the malformed-entry tests (the store's wire format), the usage figure and event names
 * with their stage results ({@code reported}, {@code missing}, {@code not_applicable}), the role classes of a start
 * context and the file permissions {@code rw-------} the store requires.
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
        var client = clientId();
        var version = version();
        store.enrol(enrolment(client, version));
        assertEquals(version, store.find(client).orElseThrow().version(), "the enrolled version is read back");
        assertTrue(Files.exists(base.resolve(STORE_FILE)), "the canonical store file is written");
        assertTrue(Files.exists(base.resolve(SIDECAR_FILE)), "the HMAC sidecar of the store is written");
    }

    @Test
    @DisplayName("a tampered file is refused and never parsed")
    void tampered() throws Exception {
        var client = clientId();
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
        var client = clientId();
        var other = client + "-" + clientId();
        var newer = version() + "." + Generators.integers(0, 99).next();
        store.enrol(enrolment(client, version()));
        store.enrol(enrolment(other, version()));
        Files.writeString(base.resolve(STORE_FILE), "{}", StandardCharsets.UTF_8);
        store.enrol(enrolment(client, newer));
        assertEquals(List.of(client), store.all().stream().map(HarnessEnrolment::clientId).toList(),
                "only the named client survives the re-baseline");
        assertEquals(newer, store.find(client).orElseThrow().version(), "the re-baselined entry has the new version");
        assertTrue(store.find(other).isEmpty(), "the other client is gone after the re-baseline");
    }

    @Test
    @DisplayName("a new enrolment re-baselines a verified file that does not read as the store")
    void rebaselineUnreadable() throws Exception {
        var client = clientId();
        var other = client + "-" + clientId();
        store.enrol(enrolment(client, version()));
        var file = base.resolve(STORE_FILE);
        // format_version is the store's version member; 2 is a version the store does not read
        var bytes = Files.readString(file, StandardCharsets.UTF_8)
                .replace("\"format_version\":1", "\"format_version\":2").getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);
        Files.write(base.resolve(SIDECAR_FILE), EnrolmentHmac.sidecar(key, bytes).write());
        store.enrol(enrolment(other, version()));
        assertEquals(List.of(other), store.all().stream().map(HarnessEnrolment::clientId).toList(),
                "only the new client is in the re-baselined store");
    }

    @Test
    @DisplayName("an entry without a required object member is refused, not read as empty")
    void malformedMembers() throws Exception {
        var withoutEvents = entryDocument("");
        assertUnreadable(withoutEvents);
        assertUnreadable(entryDocument(",\"events\":\"not-an-object\""));
        assertUnreadable(entryDocument(",\"events\":{\"text\":5}"));
        assertUnreadable(entryDocument(",\"events\":{},\"weights\":[]"));
    }

    private void assertUnreadable(String document) throws Exception {
        var bytes = document.getBytes(StandardCharsets.UTF_8);
        var file = base.resolve(STORE_FILE);
        var sidecar = base.resolve(SIDECAR_FILE);
        Files.write(file, bytes);
        Files.write(sidecar, EnrolmentHmac.sidecar(key, bytes).write());
        var restricted = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(file, restricted);
        Files.setPosixFilePermissions(sidecar, restricted);
        var refusal = assertThrows(HarnessEnrolmentStore.StoreException.class, () -> store.find("claude"),
                "a malformed store document is refused");
        assertEquals(HarnessEnrolmentStore.Refusal.UNREADABLE, refusal.refusal(),
                "the malformed document is unreadable: " + document);
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
        var client = clientId();
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
        var client = clientId();
        var other = client + "-" + clientId();
        store.enrol(enrolment(client, version()));
        store.enrol(enrolment(other, version()));
        assertTrue(store.unenrol(client), "an enrolled client is unenrolled");
        assertFalse(store.unenrol(client), "an unenrolled client is not unenrolled again");
        assertEquals(List.of(other), store.all().stream().map(HarnessEnrolment::clientId).toList(),
                "only the other client remains enrolled");
    }

    @Test
    @DisplayName("the remote form of a judgment endpoint round-trips its origin and credential key")
    void endpointEntryRoundTrip() {
        var client = clientId();
        var origin = "https://" + lowerLetters() + ".example.test";
        var credentialKey = lowerLetters();
        var endpoint = HarnessEnrolment.endpoint(client, origin, credentialKey, now,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED,
                        Map.of("input", "reported")));

        store.enrol(endpoint);
        var read = store.find(client).orElseThrow();

        assertAll(
                () -> assertTrue(read.isEndpoint(), "the read entry is of the remote form"),
                () -> assertEquals(origin, read.binding().origin(), "the endpoint's origin round-trips"),
                () -> assertEquals(credentialKey, read.binding().credentialKey(),
                        "the endpoint's credential key round-trips"),
                () -> assertNull(read.executablePath(), "an endpoint has no executable path"),
                () -> assertNull(read.sha256(), "an endpoint has no executable digest"),
                () -> assertNull(read.version(), "an endpoint has no program version"),
                () -> assertEquals(endpoint, read, "the whole endpoint entry round-trips"));
    }

    @Test
    @DisplayName("an entry of the remote form without its required members is refused, not read as a program")
    void malformedEndpointEntry() throws Exception {
        assertUnreadable(endpointDocument("{\"origin\":\"https://judge.example.test\"}"));
        assertUnreadable("{\"format_version\":1,\"harnesses\":{\"hosted-judge\":{\"endpoint\":null,"
                + "\"enrolled_at\":\"2026-10-24T10:00:00Z\",\"updated_at\":\"2026-10-24T10:00:00Z\","
                + "\"usage_report\":{\"source\":\"declared\",\"figures\":{}},\"events\":{}}}}");
        assertUnreadable(endpointDocument("{\"origin\":\"http://judge.example.test\",\"credential_key\":"
                + "\"judge-token\"}"));
    }

    @Test
    @DisplayName("an entry that carries members of both forms is refused, not read as one of its halves")
    void mixedFormEntry() throws Exception {
        assertUnreadable("{\"format_version\":1,\"harnesses\":{\"hosted-judge\":{\"endpoint\":{\"origin\":"
                + "\"https://judge.example.test\",\"credential_key\":\"judge-token\"},\"executable_path\":"
                + "\"/usr/local/bin/claude\",\"sha256\":\"" + "a".repeat(64) + "\",\"version\":\"1.5.0\","
                + "\"enrolled_at\":\"2026-10-24T10:00:00Z\",\"updated_at\":\"2026-10-24T10:00:00Z\","
                + "\"usage_report\":{\"source\":\"declared\",\"figures\":{}},\"events\":{}}}}");
        assertUnreadable(entryDocument(",\"endpoint\":{\"origin\":\"https://judge.example.test\","
                + "\"credential_key\":\"judge-token\"}"));
    }

    /** @param endpoint the members of the remote form's {@code endpoint} object, in the store's wire format */
    private static String endpointDocument(String endpoint) {
        return "{\"format_version\":1,\"harnesses\":{\"hosted-judge\":{\"endpoint\":" + endpoint + ","
                + "\"enrolled_at\":\"2026-10-24T10:00:00Z\",\"updated_at\":\"2026-10-24T10:00:00Z\","
                + "\"usage_report\":{\"source\":\"declared\",\"figures\":{}},\"events\":{}}}}";
    }

    private HarnessEnrolment enrolment(String clientId, String version) {
        return new HarnessEnrolment(clientId, Path.of("/usr/local/bin/" + clientId).toAbsolutePath(),
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
        var client = clientId();
        var model = lowerLetters();
        var report = new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.VERIFIED,
                Map.of("input", "reported", "cache_creation", "not_applicable"), List.of(
                        new HarnessEnrolment.UsageReport.StartContext("writer", model,
                                OptionalLong.of(Generators.integers(1, 1_000_000).next())),
                        new HarnessEnrolment.UsageReport.StartContext("zero_tool", model,
                                OptionalLong.empty())));
        store.enrol(new HarnessEnrolment(client, Path.of("/usr/local/bin/" + client).toAbsolutePath(),
                RuntimeGenerators.sha256Digests().next(), version(), now, now, report, Map.of("text", "reported"),
                Map.of()));

        assertEquals(report, store.find(client).orElseThrow().usageReport(),
                "the start context of the report round-trips");
    }

    /** @return a client id of the store's pattern: a lower-case letter first, at most 32 characters */
    private static String clientId() {
        return "c" + lowerLetters();
    }

    private static String lowerLetters() {
        return Generators.letterStrings(2, 10).next().toLowerCase(Locale.ROOT);
    }

    private static String version() {
        return Generators.integers(0, 9).next() + "." + Generators.integers(0, 20).next() + "."
                + Generators.integers(0, 20).next();
    }
}
