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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.launch.HarnessEnrolment.UsageReport;
import de.planmarshall.runtime.launch.HarnessEnrolment.UsageSource;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * {@link HarnessEnrolmentStore} keeps parity with the schema of Store Schemas § Harness Enrolment Store for the program
 * form of an entry: a written entry carries the schema's required members, the members of the optional {@code weights}
 * member are the schema's, the usage report and its source values are the schema's, and the format version is the
 * schema's {@code const}. The store writes canonical JSON, whose members are sorted, so the parity is stated over the
 * member sets rather than the schema's order. The remote-endpoint form (ADR {@code runtime-endpoint/005}) is a member the
 * schema block of this work package does not state yet, so this test states the program form only. The schema is the
 * file {@code harnesses.schema.json} beside the store in
 * {@code pm-runtime/src/main/resources/de/planmarshall/runtime/launch/}, read from the class path through
 * {@link SchemaResource}. The member names, the source values and the results are contracts; the ids, the digest, the
 * version and the instants are generated.
 */
@EnableGeneratorController
@DisplayName("Harness enrolment schema parity")
class HarnessEnrolmentSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/runtime/launch/harnesses.schema.json";
    private static final String USER = System.getProperty("user.name");
    private static final String SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path base;

    @Test
    @DisplayName("a written program entry carries exactly the required members")
    void programEntryMembers() {
        var required = SchemaResource.array(programEntry().get("required"));
        var entry = writtenEntry(Map.of(), Map.of());

        assertEquals(Set.copyOf(names(required)), Set.copyOf(names(entry.keySet())),
                "The program entry carries the required members");
    }

    @Test
    @DisplayName("an entry with weights carries exactly the schema's members")
    void fullEntryMembers() {
        var properties = SchemaResource.object(programEntry().get("properties"));
        var entry = writtenEntry(Map.of("deepseek-v4.1", new HarnessEnrolment.Weights("weights-" + SHA256,
                Instant.ofEpochSecond(Generators.longs(0, 2_000_000_000L).next()))), Map.of());

        assertEquals(Set.copyOf(names(properties.keySet())), Set.copyOf(names(entry.keySet())),
                "The written members are exactly the schema's");
    }

    @Test
    @DisplayName("the usage report and its source values are those of the schema")
    void usageReportMembers() {
        var defs = SchemaResource.object(SchemaResource.load(SCHEMA).get("$defs"));
        var usageReport = SchemaResource.object(defs.get("usage_report"));
        var written = SchemaResource.object(writtenEntry(Map.of(), Map.of()).get("usage_report"));
        var source = SchemaResource.object(SchemaResource.object(usageReport.get("properties")).get("source"));

        assertAll(
                () -> assertEquals(Set.copyOf(names(SchemaResource.array(usageReport.get("required")))),
                        Set.copyOf(names(written.keySet())),
                        "The usage report carries the required members"),
                () -> assertEquals(Arrays.stream(UsageSource.values()).map(UsageSource::wireName).toList(),
                        names(SchemaResource.array(source.get("enum"))), "The source values are the schema's"));
    }

    @Test
    @DisplayName("the weights entry and the event results are the schema's")
    void weightsAndEvents() {
        var defs = SchemaResource.object(SchemaResource.load(SCHEMA).get("$defs"));
        var weightsEntry = SchemaResource.object(SchemaResource.object(defs.get("weights")).get("additionalProperties"));
        var eventResults = SchemaResource.object(SchemaResource.object(defs.get("events")).get("additionalProperties"));
        var written = writtenEntry(Map.of("deepseek-v4.1", new HarnessEnrolment.Weights("weights-" + SHA256,
                        Instant.ofEpochSecond(Generators.longs(0, 2_000_000_000L).next()))),
                Map.of("turn", "reported", "budget", "missing"));
        var writtenWeights = SchemaResource.object(SchemaResource.object(written.get("weights"))
                .get("deepseek-v4.1"));

        assertAll(
                () -> assertEquals(Set.copyOf(names(SchemaResource.array(weightsEntry.get("required")))),
                        Set.copyOf(names(writtenWeights.keySet())), "The weights members are the schema's"),
                () -> assertTrue(names(SchemaResource.array(eventResults.get("enum")))
                                .containsAll(names(SchemaResource.object(written.get("events")).values())),
                        "Every written event result is a schema value"),
                () -> assertEquals(HarnessEnrolmentStore.FORMAT_VERSION, formatVersion(),
                        "The format version is the schema const"));
    }

    private static Map<?, ?> programEntry() {
        return SchemaResource.object(
                SchemaResource.object(SchemaResource.load(SCHEMA).get("$defs")).get("program_entry"));
    }

    private static int formatVersion() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));
        return ((Number) SchemaResource.object(properties.get("format_version")).get("const")).intValue();
    }

    private Map<?, ?> writtenEntry(Map<String, HarnessEnrolment.Weights> weights, Map<String, String> events) {
        var now = Instant.ofEpochSecond(Generators.longs(0, 2_000_000_000L).next());
        var enrolment = new HarnessEnrolment("opencode", Path.of("/opt/harness/opencode"), SHA256,
                "1." + Generators.integers(0, 99).next(), now, now, new UsageReport(UsageSource.VERIFIED, Map.of()),
                events, weights);
        store().enrol(enrolment);
        var document = SchemaResource.object(SchemaResource.parse(
                new String(read(base.resolve("harnesses.json")), UTF_8)));
        return SchemaResource.object(SchemaResource.object(document.get("harnesses")).get("opencode"));
    }

    private HarnessEnrolmentStore store() {
        SecretKey key = new SecretKeySpec(Generators.letterStrings(32, 32).next().getBytes(UTF_8),
                EnrolmentKey.ALGORITHM);
        return new HarnessEnrolmentStore(base, () -> key, new FileLockManager(event -> {
        }), USER);
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("The enrolment store cannot be read: " + file, e);
        }
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
