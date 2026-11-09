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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;

/**
 * {@link MachineConfigurationCodec} keeps parity with the schema of Store Schemas § Machine Configuration Store: a
 * document the codec creates carries exactly the members the schema requires, in schema order, its provider keys and
 * variant values are the schema's closed lists, and its two defaults and its format version are the schema's. The codec
 * owns only {@code providers} and copies every other member through, so the parity is stated over the document it
 * creates, not over the members the owner may add. The schema is the file {@code machine-config.schema.json} beside the
 * codec in {@code pm-runtime/src/main/resources/de/planmarshall/runtime/config/}, read from the class path through
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
                new ProviderDefault(true, ProviderVariant.CLI, "github")));
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

    private static Map<?, ?> document(byte[] written) {
        return SchemaResource.object(SchemaResource.parse(new String(written, UTF_8)));
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
