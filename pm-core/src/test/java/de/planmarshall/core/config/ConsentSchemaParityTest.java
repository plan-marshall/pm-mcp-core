/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;

/**
 * {@link ConsentCodec} keeps parity with the schema of Store Schemas § Consent Before Acting: an empty store carries
 * exactly the schema's members, the stored kinds are the rememberable values of {@link ConsentKind}, the channels are
 * those of {@link Consent.Channel}, and a store written with both entry forms is valid against the schema. The schema is
 * the file {@code consents.schema.json} beside the codec in
 * {@code pm-core/src/main/resources/de/planmarshall/core/config/}, read from the class path through
 * {@link SchemaResource}. The kinds, the channel values and the schema's member names are contracts; the recorded
 * instant and the numbers are generated.
 */
@EnableGeneratorController
@DisplayName("Consent store schema parity")
class ConsentSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/config/consents.schema.json";
    private static final String SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("an empty store has exactly the schema's members, every one required")
    void storeMembers() {
        var schema = SchemaResource.load(SCHEMA);
        var written = document(ConsentCodec.write(List.of(), 0));

        assertAll(
                () -> assertEquals(names(SchemaResource.object(schema.get("properties")).keySet()),
                        names(written.keySet()), "The store members are the schema's in order"),
                () -> assertEquals(names(SchemaResource.array(schema.get("required"))), names(written.keySet()),
                        "Every store member is required"));
    }

    @Test
    @DisplayName("the stored kinds are the rememberable values of the schema")
    void kinds() {
        var consents = SchemaResource.object(
                SchemaResource.object(SchemaResource.load(SCHEMA).get("properties")).get("consents"));
        var items = SchemaResource.object(consents.get("items"));
        var kind = SchemaResource.object(SchemaResource.object(items.get("properties")).get("kind"));

        assertEquals(Arrays.stream(ConsentKind.values()).filter(ConsentKind::rememberable)
                        .map(ConsentKind::wireName).toList(), names(SchemaResource.array(kind.get("enum"))),
                "The stored kinds are the rememberable ones");
    }

    @Test
    @DisplayName("the project-scoped kinds of the entry rule are the project kinds")
    void projectScopedKinds() {
        var consents = SchemaResource.object(
                SchemaResource.object(SchemaResource.load(SCHEMA).get("properties")).get("consents"));
        var items = SchemaResource.object(consents.get("items"));
        var condition = SchemaResource.object(SchemaResource.array(items.get("allOf")).get(0));
        var kinds = SchemaResource.object(SchemaResource.object(condition.get("if")).get("properties")).get("kind");

        assertEquals(Arrays.stream(ConsentKind.values()).filter(ConsentKind::projectScoped)
                        .map(ConsentKind::wireName).toList(),
                names(SchemaResource.array(SchemaResource.object(kinds).get("enum"))),
                "The project-scoped kinds are the entry rule's");
    }

    @Test
    @DisplayName("the channels are the values of the schema")
    void channels() {
        var consents = SchemaResource.object(
                SchemaResource.object(SchemaResource.load(SCHEMA).get("properties")).get("consents"));
        var items = SchemaResource.object(consents.get("items"));
        var channel = SchemaResource.object(SchemaResource.object(items.get("properties")).get("channel"));

        assertEquals(Arrays.stream(Consent.Channel.values()).map(Consent.Channel::wireName).toList(),
                names(SchemaResource.array(channel.get("enum"))), "The channels are the schema's values");
    }

    @Test
    @DisplayName("a store with a machine and a project entry is valid against the schema")
    void writtenStoreIsValid() {
        var recorded = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
        var machine = Consent.machine(ConsentKind.SCAN_HARNESSES, recorded, Consent.Channel.WEB,
                "dev-0123456789ab");
        var scripts = Consent.checkoutScripts("project-a", List.of(new ConsentScript("scripts/build.sh", SHA256)),
                recorded, Consent.Channel.CLI, null);
        var written = ConsentCodec.write(List.of(machine, scripts), 2);

        assertEquals(Set.of(), validator().validate(new String(written, UTF_8), InputFormat.JSON),
                () -> "The written store is valid: " + new String(written, UTF_8));
    }

    private static JsonSchema validator() {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(SchemaResource.text(SCHEMA));
    }

    private static Map<?, ?> document(byte[] written) {
        return SchemaResource.object(SchemaResource.parse(new String(written, UTF_8)));
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
