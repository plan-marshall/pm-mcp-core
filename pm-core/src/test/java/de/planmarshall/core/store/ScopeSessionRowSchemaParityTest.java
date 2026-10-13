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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SpecificationDocument;
import de.planmarshall.core.store.ScopeSessionRow.Client;
import de.planmarshall.core.store.ScopeSessionRow.EnteredFrom;
import de.planmarshall.core.store.ScopeSessionRow.EnvironmentMatchTier;
import de.planmarshall.core.store.ScopeSessionRow.ModelFamily;
import de.planmarshall.core.store.ScopeSessionRow.ModelSource;

/**
 * The codec of the scope session row keeps parity with the schema of Store Schemas § Scope Session Log (PM-ARCH-4):
 * the members it reads and writes, the members it requires, and the values of every enum. And, as the guard of
 * PM-WATCH-STORE-1 demands, no member of a row holds a path of a checkout: no member is named for a path, every free
 * string member is one of the named version and build strings, the worktree is named by an identifier that admits no
 * separator, and no component of {@link ScopeSessionRow} is a {@link Path}. The schema is read from the documentation
 * checkout named by {@value SpecificationDocument#ROOT_PROPERTY}; without the checkout the tests are skipped with the
 * missing path named.
 */
@DisplayName("Scope session row schema parity")
class ScopeSessionRowSchemaParityTest {

    private static final String DOCUMENT = "store-schemas/01-format-and-session-log.adoc";
    private static final String HEADING = "== Scope Session Log";

    /** The string members without enum, pattern or format: versions, a build and a model id, none of them a path. */
    private static final Set<String> FREE_STRINGS = Set.of("client_build", "model_id", "pm_mcp_version",
            "bundle_version");

    /** A member name that contains one of these words is named for a path; it is searched for, not matched whole. */
    private static final Pattern PATH_NAME = Pattern.compile("path|dir|root|checkout|file");

    private static Map<?, ?> schema;
    private static Map<?, ?> properties;

    @BeforeAll
    static void readSchema() {
        schema = SpecificationDocument.object(
                SpecificationDocument.parse(SpecificationDocument.jsonBlockAfter(DOCUMENT, HEADING)));
        properties = SpecificationDocument.object(schema.get("properties"));
    }

    @Test
    @DisplayName("the members of the codec are the members of the schema in schema order")
    void membersEqualSchema() {
        var expected = properties.keySet().stream().map(String::valueOf).toList();

        assertFalse(expected.isEmpty(), "the schema declares members");
        assertEquals(ScopeSessionRowCodec.DECLARED_FIELDS, expected);
    }

    @Test
    @DisplayName("the members the codec requires are the required members of the schema")
    void requiredEqualSchema() {
        var expected = SpecificationDocument.array(schema.get("required")).stream().map(String::valueOf).toList();

        assertEquals(ScopeSessionRowCodec.REQUIRED_FIELDS, expected);
    }

    @Test
    @DisplayName("the values of every enum of the codec are the values of the enum of the schema")
    void enumsEqualSchema() {
        assertAll(
                () -> assertEquals(enumOf("client"), wireNames(Client.values(), Client::wireName)),
                () -> assertEquals(enumOf("client_detected"), wireNames(Client.values(), Client::wireName)),
                () -> assertEquals(enumOf("environment_match_tier"),
                        wireNames(EnvironmentMatchTier.values(), EnvironmentMatchTier::wireName)),
                () -> assertEquals(enumOf("model_family"), wireNames(ModelFamily.values(), ModelFamily::wireName)),
                () -> assertEquals(enumOf("model_source"), wireNames(ModelSource.values(), ModelSource::wireName)),
                () -> assertEquals(enumOf("entered_from"), wireNames(EnteredFrom.values(), EnteredFrom::wireName)));
    }

    @Test
    @DisplayName("the format version of the schema is the format version of the row")
    void formatVersion() {
        var declaration = SpecificationDocument.object(properties.get("format_version"));

        assertEquals(ScopeSessionRow.FORMAT_VERSION, ((Number) declaration.get("const")).intValue());
    }

    /** PM-WATCH-STORE-1: a stored path string would bypass every resolver redirect. */
    @Test
    @DisplayName("no member of a row holds a path of a checkout")
    void noPathMember() {
        var names = properties.keySet().stream().map(String::valueOf).toList();
        var unconstrained = names.stream().filter(name -> isFreeString(SpecificationDocument.object(
                properties.get(name)))).collect(Collectors.toUnmodifiableSet());
        var worktreePattern = String.valueOf(SpecificationDocument.object(properties.get("worktree_of"))
                .get("pattern"));
        var pathComponents = Arrays.stream(ScopeSessionRow.class.getRecordComponents())
                .filter(component -> Path.class.isAssignableFrom(component.getType())).toList();

        assertAll(
                () -> assertEquals(List.of(), names.stream().filter(name -> PATH_NAME.matcher(name).find())
                        .toList(), "no member is named for a path"),
                () -> assertEquals(FREE_STRINGS, unconstrained, "every other string member is constrained"),
                () -> assertFalse(Pattern.compile(worktreePattern).matcher("a/b").find(),
                        "a worktree name admits no separator"),
                () -> assertTrue(Pattern.compile(worktreePattern).matcher("_epics").find()),
                () -> assertEquals(List.of(), pathComponents, "no component of the row is a path"));
    }

    private static boolean isFreeString(Map<?, ?> declaration) {
        var type = declaration.get("type");
        var types = type instanceof List<?> list ? list : List.of(String.valueOf(type));
        return types.contains("string") && !declaration.containsKey("enum") && !declaration.containsKey("pattern")
                && !declaration.containsKey("format");
    }

    private static Set<String> enumOf(String member) {
        var declaration = SpecificationDocument.object(properties.get(member));
        return SpecificationDocument.array(declaration.get("enum")).stream().filter(Objects::nonNull)
                .map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    private static <E extends Enum<E>> Set<String> wireNames(E[] values, Function<E, String> wireName) {
        return Arrays.stream(values).map(wireName).collect(Collectors.toUnmodifiableSet());
    }
}
