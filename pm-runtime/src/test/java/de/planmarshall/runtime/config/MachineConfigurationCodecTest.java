/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.StoreRefusal;

/**
 * The machine configuration document (Store Schemas § Machine Configuration Store): one JSON object and nothing else,
 * with the two settings every document requires inside the bounds of the schema, and every member the codec does not own
 * copied through unchanged.
 * <p>
 * The settings bounds (1 to 16 build slots, at least 60 seconds of LSP idle timeout), the member names and the format
 * version are the schema's contract. The revision and the unknown member's values are generated.
 */
@EnableGeneratorController
@DisplayName("Machine configuration codec")
class MachineConfigurationCodecTest {

    @TempDir
    Path base;

    private Path file;

    private final long revision = Generators.longs(1, 1_000_000).next();
    private final String harness = Generators.letterStrings(3, 12).next().toLowerCase();

    @BeforeEach
    void createFile() {
        file = base.resolve(MachineConfigurationStore.FILE_NAME);
    }

    /** @return the required document, with the two settings given as raw JSON values */
    private String document(String maxBuildSlots, String lspIdleTimeout) {
        return """
                {"format_version":1,"revision":%d,"max_build_slots":%s,"lsp_idle_timeout_seconds":%s}"""
                .formatted(revision, maxBuildSlots, lspIdleTimeout);
    }

    private byte[] bytes(String document) {
        return document.getBytes(UTF_8);
    }

    private StoreRefusal readRefused(String document) {
        return assertThrows(StoreRefusal.class,
                () -> MachineConfigurationCodec.read(file, bytes(document)),
                "an unreadable document must be refused on read");
    }

    private StoreRefusal writeRefused(String document) {
        return assertThrows(StoreRefusal.class,
                () -> MachineConfigurationCodec.write(file, bytes(document), ProviderDefaults.none(), revision),
                "an unreadable document must be refused on write");
    }

    @Test
    @DisplayName("two objects after another are refused on read and on write")
    void trailingObject() {
        var document = document("2", "900") + document("2", "900");

        var read = readRefused(document);
        var written = writeRefused(document);

        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, read.outcome(),
                        "trailing content makes the document unreadable"),
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, written.outcome(),
                        "a write refuses trailing content before it writes"),
                () -> assertEquals(file, read.file(), "the refusal names the document"));
    }

    @Test
    @DisplayName("a number after the object is refused on read and on write")
    void trailingNumber() {
        var document = document("2", "900") + "42";

        var read = readRefused(document);
        var written = writeRefused(document);

        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, read.outcome(),
                        "a trailing number makes the document unreadable"),
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, written.outcome(),
                        "a write refuses a trailing number before it writes"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"2\"", "0", "17"})
    @DisplayName("a build slot limit outside an integer of 1 to 16 is refused on read and on write")
    void maxBuildSlotsRefused(String maxBuildSlots) {
        var document = document(maxBuildSlots, "900");

        var read = readRefused(document);
        var written = writeRefused(document);

        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, read.outcome(),
                        "the build slot limit must be an integer in its bounds"),
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, written.outcome(),
                        "a write refuses an out-of-bounds build slot limit"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"900\"", "59"})
    @DisplayName("an LSP idle timeout below the minimum integer of 60 is refused on read and on write")
    void lspIdleTimeoutRefused(String lspIdleTimeout) {
        var document = document("2", lspIdleTimeout);

        var read = readRefused(document);
        var written = writeRefused(document);

        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, read.outcome(),
                        "the LSP idle timeout must be an integer of at least 60"),
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, written.outcome(),
                        "a write refuses an out-of-bounds LSP idle timeout"));
    }

    @Test
    @DisplayName("the bounds of both settings are accepted at their edges")
    void boundsAccepted() {
        var read = MachineConfigurationCodec.read(file, bytes(document("1", "60")));

        assertEquals(revision, read.revision(), "a document at the lower bounds must be read");
    }

    @Test
    @DisplayName("a write keeps an unknown member byte-identical and replaces providers alone")
    void keepsUnknownMember() {
        var unknown = "\"harnesses\":{\"default\":\"" + harness + "\"}";
        var document = """
                {"format_version":1,"revision":%d,"max_build_slots":2,"lsp_idle_timeout_seconds":900,%s}"""
                .formatted(revision, unknown);
        var defaults = new ProviderDefaults(Map.of(Provider.SONAR,
                ProviderDefault.unavailable(null)));

        var written = new String(
                MachineConfigurationCodec.write(file, bytes(document), defaults, revision + 1), UTF_8);

        assertAll(
                () -> assertTrue(written.contains(unknown), "the unknown member must be copied verbatim"),
                () -> assertEquals(revision + 1,
                        MachineConfigurationCodec.read(file, written.getBytes(UTF_8)).revision(),
                        "the write must carry the given revision"),
                () -> assertEquals(defaults,
                        MachineConfigurationCodec.read(file, written.getBytes(UTF_8)).providers(),
                        "the written defaults must be read back"));
    }
}
