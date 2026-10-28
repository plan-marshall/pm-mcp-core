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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
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

/** The harness enrolment store: its HMAC sidecar, the re-baseline of a tampered file and the client ordering. */
@DisplayName("Harness enrolment store")
class HarnessEnrolmentStoreTest {

    private static final String USER = System.getProperty("user.name");
    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    @TempDir
    Path base;

    private HarnessEnrolmentStore store;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                EnrolmentKey.ALGORITHM);
        store = new HarnessEnrolmentStore(base, () -> key, new FileLockManager(event -> {
        }), USER);
    }

    @Test
    @DisplayName("writes and reads an enrolment, with the canonical file and its sidecar")
    void writeAndRead() {
        store.enrol(enrolment("claude", "1.5.0"));
        assertEquals("1.5.0", store.find("claude").orElseThrow().version());
        assertTrue(Files.exists(base.resolve("harnesses.json")));
        assertTrue(Files.exists(base.resolve("harnesses.json.hmac")));
    }

    @Test
    @DisplayName("a tampered file is refused and never parsed")
    void tampered() throws Exception {
        store.enrol(enrolment("claude", "1.5.0"));
        Files.writeString(base.resolve("harnesses.json"), "{\"format_version\":1,\"harnesses\":{}}",
                StandardCharsets.UTF_8);
        var refusal = assertThrows(HarnessEnrolmentStore.StoreException.class, () -> store.find("claude"));
        assertEquals(HarnessEnrolmentStore.Refusal.TAMPERED, refusal.refusal());
    }

    @Test
    @DisplayName("a new enrolment re-baselines a tampered file with the named client only")
    void rebaseline() throws Exception {
        store.enrol(enrolment("claude", "1.5.0"));
        store.enrol(enrolment("opencode", "1.0.0"));
        Files.writeString(base.resolve("harnesses.json"), "{}", StandardCharsets.UTF_8);
        store.enrol(enrolment("claude", "1.6.0"));
        assertEquals(List.of("claude"), store.all().stream().map(HarnessEnrolment::clientId).toList());
        assertEquals("1.6.0", store.find("claude").orElseThrow().version());
        assertTrue(store.find("opencode").isEmpty());
    }

    @Test
    @DisplayName("a new enrolment re-baselines a verified file that does not read as the store")
    void rebaselineUnreadable() throws Exception {
        store.enrol(enrolment("claude", "1.5.0"));
        var file = base.resolve("harnesses.json");
        var bytes = Files.readString(file, StandardCharsets.UTF_8)
                .replace("\"format_version\":1", "\"format_version\":2").getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);
        Files.write(base.resolve("harnesses.json.hmac"), EnrolmentHmac.sidecar(key, bytes).write());
        store.enrol(enrolment("opencode", "1.0.0"));
        assertEquals(List.of("opencode"), store.all().stream().map(HarnessEnrolment::clientId).toList());
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
        var file = base.resolve("harnesses.json");
        var sidecar = base.resolve("harnesses.json.hmac");
        Files.write(file, bytes);
        Files.write(sidecar, EnrolmentHmac.sidecar(key, bytes).write());
        var restricted = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(file, restricted);
        Files.setPosixFilePermissions(sidecar, restricted);
        var refusal = assertThrows(HarnessEnrolmentStore.StoreException.class, () -> store.find("claude"));
        assertEquals(HarnessEnrolmentStore.Refusal.UNREADABLE, refusal.refusal(), document);
    }

    /** @param tail a malformed member appended after {@code usage_report}, before the closing braces */
    private static String entryDocument(String tail) {
        return "{\"format_version\":1,\"harnesses\":{\"claude\":{\"executable_path\":\"/usr/local/bin/claude\","
                + "\"sha256\":\"" + "a".repeat(64) + "\",\"version\":\"1.5.0\","
                + "\"enrolled_at\":\"2026-10-24T10:00:00Z\",\"updated_at\":\"2026-10-24T10:00:00Z\","
                + "\"usage_report\":{\"source\":\"declared\",\"figures\":{\"input\":\"reported\"}}" + tail + "}}}";
    }

    @Test
    @DisplayName("an accepted self-update rewrites the version and the digest")
    void selfUpdate() {
        var enrolled = store.enrol(enrolment("claude", "1.5.0"));
        var updated = store.acceptSelfUpdate("claude", "1.6.0", "b".repeat(64), NOW.plusSeconds(60)).orElseThrow();
        assertEquals("1.6.0", updated.version());
        assertEquals("b".repeat(64), updated.sha256());
        assertEquals(enrolled.enrolledAt(), updated.enrolledAt());
        assertEquals(NOW.plusSeconds(60), updated.updatedAt());
    }

    @Test
    @DisplayName("an unenrol removes only the named client")
    void unenrol() {
        store.enrol(enrolment("claude", "1.5.0"));
        store.enrol(enrolment("opencode", "1.0.0"));
        assertTrue(store.unenrol("claude"));
        assertFalse(store.unenrol("claude"));
        assertEquals(List.of("opencode"), store.all().stream().map(HarnessEnrolment::clientId).toList());
    }

    @Test
    @DisplayName("the remote form of a judgment endpoint round-trips its origin and credential key")
    void endpointEntryRoundTrip() {
        var endpoint = HarnessEnrolment.endpoint("hosted-judge", "https://judge.example.test", "judge-token", NOW,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED,
                        Map.of("input", "reported")));

        store.enrol(endpoint);
        var read = store.find("hosted-judge").orElseThrow();

        assertAll(
                () -> assertTrue(read.isEndpoint()),
                () -> assertEquals("https://judge.example.test", read.binding().origin()),
                () -> assertEquals("judge-token", read.binding().credentialKey()),
                () -> assertNull(read.executablePath()),
                () -> assertNull(read.sha256()),
                () -> assertNull(read.version()),
                () -> assertEquals(endpoint, read));
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

    /** @param endpoint the members of the remote form's {@code endpoint} object */
    private static String endpointDocument(String endpoint) {
        return "{\"format_version\":1,\"harnesses\":{\"hosted-judge\":{\"endpoint\":" + endpoint + ","
                + "\"enrolled_at\":\"2026-10-24T10:00:00Z\",\"updated_at\":\"2026-10-24T10:00:00Z\","
                + "\"usage_report\":{\"source\":\"declared\",\"figures\":{}},\"events\":{}}}}";
    }

    private static HarnessEnrolment enrolment(String clientId, String version) {
        return new HarnessEnrolment(clientId, Path.of("/usr/local/bin/" + clientId).toAbsolutePath(), "a".repeat(64),
                version, NOW, NOW, new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED,
                        Map.of("input", "reported")), Map.of("text", "reported"), Map.of());
    }

    @Test
    @DisplayName("the report maps the usage stage's figure names to the store's")
    void reportMapsProfileFigureNames() {
        var report = HarnessEnrolment.UsageReport.of(HarnessEnrolment.UsageSource.VERIFIED,
                Map.of("input_tokens", "reported", "output_tokens", "missing"), List.of());

        assertEquals(Map.of("input", "reported", "output", "missing"), report.figures());
    }

    @Test
    @DisplayName("the start context of a verified usage report round-trips")
    void startContextRoundTrip() {
        var report = new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.VERIFIED,
                Map.of("input", "reported", "cache_creation", "not_applicable"), List.of(
                        new HarnessEnrolment.UsageReport.StartContext("writer", "a-model", OptionalLong.of(27104)),
                        new HarnessEnrolment.UsageReport.StartContext("zero_tool", "a-model",
                                OptionalLong.empty())));
        store.enrol(new HarnessEnrolment("claude", Path.of("/usr/local/bin/claude").toAbsolutePath(),
                "a".repeat(64), "1.5.0", NOW, NOW, report, Map.of("text", "reported"), Map.of()));

        assertEquals(report, store.find("claude").orElseThrow().usageReport());
    }
}
