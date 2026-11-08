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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;

/**
 * {@link LeaseCodec} keeps parity with the schema of Store Schemas § Machine Lease Stores for the owner of a lease: a
 * written owner carries the schema's members in schema order, the schema requires the owner's first four members, the
 * scope types the codec writes are values the schema enumerates, and the holder instance carries the schema's two
 * required members. The schema is the file {@code lease-owner.schema.json} beside the codec in
 * {@code pm-core/src/main/resources/de/planmarshall/core/store/}, read from the class path through {@link SchemaResource}.
 * The member names and the scope-type values are contracts; the ids, the pid and the instants are generated.
 */
@EnableGeneratorController
@DisplayName("Lease owner schema parity")
class LeaseOwnerSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/store/lease-owner.schema.json";

    @Test
    @DisplayName("a written owner has the schema's members in order, and its required set")
    void ownerMembers() {
        var schema = SchemaResource.load(SCHEMA);
        var owner = writtenOwner();

        assertAll(
                () -> assertEquals(names(SchemaResource.object(schema.get("properties")).keySet()),
                        names(owner.keySet()), "The owner members are the schema's in order"),
                () -> assertEquals(List.of("project_id", "scope_type", "scope_id", "holder_instance"),
                        names(SchemaResource.array(schema.get("required"))),
                        "The schema requires the owner's first four members"));
    }

    @Test
    @DisplayName("the scope types the codec writes are values the schema enumerates")
    void scopeTypes() {
        var scopeType = SchemaResource.object(
                SchemaResource.object(SchemaResource.load(SCHEMA).get("properties")).get("scope_type"));

        assertTrue(names(SchemaResource.array(scopeType.get("enum"))).containsAll(
                        Arrays.stream(ScopeType.values()).map(ScopeType::wireName).toList()),
                "Every written scope type is a schema value");
    }

    @Test
    @DisplayName("the holder instance has the schema's members, both required")
    void holderMembers() {
        var holder = SchemaResource.object(
                SchemaResource.object(SchemaResource.load(SCHEMA).get("properties")).get("holder_instance"));
        var owner = writtenOwner();
        var written = SchemaResource.object(owner.get("holder_instance"));

        assertAll(
                () -> assertEquals(names(SchemaResource.object(holder.get("properties")).keySet()),
                        names(written.keySet()), "The holder members are the schema's in order"),
                () -> assertEquals(names(SchemaResource.array(holder.get("required"))), names(written.keySet()),
                        "Every holder member is required"),
                () -> assertInstanceOf(String.class, owner.get("project_id"), "The project id is a string"),
                () -> assertInstanceOf(String.class, owner.get("scope_type"), "The scope type is a string"),
                () -> assertInstanceOf(Map.class, owner.get("holder_instance"), "The holder is an object"),
                () -> assertInstanceOf(Number.class, written.get("runtime_pid"), "The pid is a number"),
                () -> assertInstanceOf(String.class, written.get("runtime_started_at"),
                        "The start instant is a string"));
    }

    /** @return the owner object of a lease store document, written by the codec and parsed again */
    private static Map<?, ?> writtenOwner() {
        var owner = new LeaseOwner("project-a", ScopeType.PLAN, "plan-a",
                new HolderInstance(Generators.longs(1, 100_000).next(),
                        Instant.ofEpochSecond(Generators.longs(0, 2_000_000_000L).next())),
                null);
        var written = LeaseCodec.write(List.of(new LeaseRecord("build-slots", owner,
                Instant.ofEpochSecond(Generators.longs(0, 2_000_000_000L).next()), null)));
        var document = SchemaResource.object(SchemaResource.parse(new String(written, UTF_8)));
        var leases = SchemaResource.array(document.get("leases"));
        return SchemaResource.object(SchemaResource.object(leases.get(0)).get("owner"));
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
