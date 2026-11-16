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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;

/**
 * {@link MachineConfigurationCodec} keeps parity with the schema of Store Schemas § Machine Configuration Store: a
 * document the codec creates carries exactly the members the schema requires, in schema order, its provider keys and
 * variant values are the schema's closed lists, and its two defaults and its format version are the schema's. The codec
 * owns {@code providers}, {@code sources.documents_mode} and {@code cleanup} and copies every other member through, so
 * the parity is stated over the document it creates, not over the members the owner may add. The schema is the file
 * {@code machine-config.schema.json} beside the codec in
 * {@code pm-runtime/src/main/resources/de/planmarshall/runtime/config/}, read from the class path through
 * {@link SchemaResource}.
 */
@DisplayName("Machine configuration schema parity")
class MachineConfigurationSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/runtime/config/machine-config.schema.json";

    @Test
    @DisplayName("a created document has exactly the members the schema requires, in schema order")
    void createdDocumentMembers() {
        var schema = SchemaResource.load(SCHEMA);
        var properties = SchemaResource.object(schema.get("properties"));
        var created = document(MachineConfigurationCodec.create(ProviderDefaults.none()));

        assertAll(
                () -> assertEquals(names(SchemaResource.array(schema.get("required"))), names(created.keySet()),
                        "The created members are the required ones in order"),
                () -> assertEquals(SchemaResource.object(properties.get("format_version")).get("const"),
                        created.get("format_version"), "The format version is the schema const"),
                () -> assertEquals(SchemaResource.object(properties.get("max_build_slots")).get("default"),
                        created.get("max_build_slots"), "The build slot default is the schema's"),
                () -> assertEquals(SchemaResource.object(properties.get("lsp_idle_timeout_seconds")).get("default"),
                        created.get("lsp_idle_timeout_seconds"), "The LSP timeout default is the schema's"));
    }

    @Test
    @DisplayName("the providers member is the schema's and names the closed provider list")
    void providersMember() {
        var schema = SchemaResource.load(SCHEMA);
        var defaults = new ProviderDefaults(Map.of(Provider.GITHUB,
                ProviderDefault.verified(new VerificationResult(true, Instant.EPOCH,
                        List.of(new VerificationResult.Check("origin", true))), ProviderVariant.CLI)
                        .withCredentialKey("github")));
        var created = document(MachineConfigurationCodec.create(defaults));
        var providers = SchemaResource.object(SchemaResource.object(schema.get("properties")).get("providers"));
        var written = SchemaResource.object(SchemaResource.object(created.get("providers")).get("github"));
        var expected = new ArrayList<>(names(SchemaResource.array(schema.get("required"))));
        expected.add("providers");

        assertEquals(expected, names(created.keySet()), "The providers follow the required members");
        assertEquals(Arrays.stream(Provider.values()).map(Provider::wireName).toList(),
                names(SchemaResource.array(SchemaResource.object(providers.get("propertyNames")).get("enum"))),
                "The provider keys are the schema's closed list");
        assertAll(
                () -> assertEquals(Boolean.TRUE, written.get("available"), "The provider is recorded available"),
                () -> assertEquals("cli", written.get("variant"), "The written variant is the cli one"),
                () -> assertEquals("github", written.get("credential_key"), "The written key names the entry"));
    }

    @Test
    @DisplayName("the variant values and the format version are those of the schema")
    void variantAndVersion() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));
        var provider = SchemaResource.object(
                SchemaResource.object(properties.get("providers")).get("additionalProperties"));
        var variant = SchemaResource.object(SchemaResource.object(provider.get("properties")).get("variant"));

        assertEquals(Arrays.stream(ProviderVariant.values()).map(ProviderVariant::wireName).toList(),
                names(SchemaResource.array(variant.get("enum"))), "The variant values are the schema's");
        assertEquals(MachineConfigurationCodec.FORMAT_VERSION,
                ((Number) SchemaResource.object(properties.get("format_version")).get("const")).intValue(),
                "The format version is the schema const");
    }

    @Test
    @DisplayName("the defaults of the codec are the schema's defaults")
    void defaults() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));

        assertEquals(MachineConfigurationCodec.DEFAULT_MAX_BUILD_SLOTS,
                ((Number) SchemaResource.object(properties.get("max_build_slots")).get("default")).intValue(),
                "The build slot default is the schema's");
        assertEquals(MachineConfigurationCodec.DEFAULT_LSP_IDLE_TIMEOUT_SECONDS,
                ((Number) SchemaResource.object(properties.get("lsp_idle_timeout_seconds")).get("default")).intValue(),
                "The LSP timeout default is the schema's");
    }

    @Test
    @DisplayName("the settings bounds of the codec are the schema's")
    void settingsBounds() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));
        var buildSlots = SchemaResource.object(properties.get("max_build_slots"));
        var lspIdleTimeout = SchemaResource.object(properties.get("lsp_idle_timeout_seconds"));

        assertAll(
                () -> assertEquals(MachineConfigurationCodec.MIN_BUILD_SLOTS,
                        ((Number) buildSlots.get("minimum")).intValue(), "the lowest build slot count is the schema's"),
                () -> assertEquals(MachineConfigurationCodec.MAX_BUILD_SLOTS,
                        ((Number) buildSlots.get("maximum")).intValue(), "the highest build slot count is the schema's"),
                () -> assertEquals(MachineConfigurationCodec.MIN_LSP_IDLE_TIMEOUT_SECONDS,
                        ((Number) lspIdleTimeout.get("minimum")).intValue(),
                        "the lowest LSP idle timeout is the schema's"));
    }

    @Test
    @DisplayName("the documents mode and its default are the schema's")
    void documentsMode() {
        var sources = SchemaResource.object(SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"))
                .get("sources"));
        var mode = SchemaResource.object(SchemaResource.object(sources.get("properties")).get("documents_mode"));

        assertAll(
                () -> assertEquals(Arrays.stream(DocumentsMode.values()).map(DocumentsMode::wireName).toList(),
                        names(SchemaResource.array(mode.get("enum"))), "the documents modes are the schema's list"),
                () -> assertEquals(DocumentsMode.DEFAULT.wireName(), mode.get("default"),
                        "the default documents mode is the schema's"));
    }

    @Test
    @DisplayName("the cleanup durations and their bounds are the schema's")
    void cleanup() {
        var schema = SchemaResource.load(SCHEMA);
        var cleanup = SchemaResource.object(SchemaResource.object(schema.get("properties")).get("cleanup"));
        var properties = SchemaResource.object(cleanup.get("properties"));
        var duration = SchemaResource.object(SchemaResource.object(schema.get("$defs")).get("cleanup_duration"));
        var days = SchemaResource.object(SchemaResource.array(duration.get("oneOf")).getFirst());
        var reminder = SchemaResource.object(properties.get("remind_after_days"));
        var created = document(MachineConfigurationCodec.createCleanup(CleanupDurations.defaults()));
        var written = SchemaResource.object(created.get("cleanup"));

        assertAll(
                () -> assertEquals(names(properties.keySet()), names(written.keySet()),
                        "the written cleanup members are the schema's"),
                () -> assertEquals(CleanupDuration.MIN_DAYS, ((Number) days.get("minimum")).intValue(),
                        "the lowest cleanup duration is the schema's"),
                () -> assertEquals(CleanupDuration.DEFAULT_DAYS, ((Number) duration.get("default")).intValue(),
                        "the default cleanup duration is the schema's"),
                () -> assertEquals(CleanupDurations.MIN_REMIND_DAYS, ((Number) reminder.get("minimum")).intValue(),
                        "the lowest reminder interval is the schema's"),
                () -> assertEquals(CleanupDurations.DEFAULT_REMIND_DAYS,
                        ((Number) reminder.get("default")).intValue(),
                        "the default reminder interval is the schema's"));
    }

    /**
     * The rejecting case and the external {@code $ref} to {@code local-config.json#/$defs/settings} are stated as a
     * gap, not closed: the codec's strict reader accepts an undeclared top-level member, and the module carries no
     * JSON-schema validator in its test scope while no dependency is added. The referenced settings block is checked by
     * that file's own parity test. Owner: the work package {@code runtime-schema-validation}.
     */
    private static Map<?, ?> document(byte[] written) {
        return SchemaResource.object(SchemaResource.parse(new String(written, UTF_8)));
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
