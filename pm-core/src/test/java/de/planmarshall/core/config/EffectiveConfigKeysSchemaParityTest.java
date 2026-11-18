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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.SchemaResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins {@link EffectiveConfigKeys} to the closed schemas of Store Schemas 07 and 08 (R10): walks the three configuration
 * schemas and asserts, key by key, the effective-key set, each value domain and the existence of a compiled default.
 * <p>
 * The walk is the one of the plan's Decision 3: an internal {@code $ref} is resolved and its members are keys of their
 * own, an {@code allOf} of the operator file's {@code $defs/settings} contributes its members at the root, and a map node
 * (a {@code propertyNames} with an {@code additionalProperties} schema) contributes the wildcard segment {@code *}. An
 * external {@code $ref} contributes the referenced definition's domain and no member keys, because those members are
 * another store's.
 * <p>
 * Two external references are read: the review-bot registry record from this module's resources, and
 * {@code machine-config.json}'s {@code cleanup_duration} from {@code pm-runtime}'s resources. The second is read by path
 * relative to this module's base directory, because {@code pm-core} depends on no other module of the product and the
 * file is not on this module's class path.
 */
@EnableGeneratorController
@DisplayName("Effective configuration key schema parity")
class EffectiveConfigKeysSchemaParityTest {

    private static final String CONFIG_DIRECTORY = "de/planmarshall/core/config/";
    private static final String MACHINE_CONFIG =
            "../pm-runtime/src/main/resources/de/planmarshall/runtime/config/machine-config.schema.json";
    private static final String REVIEW_BOT_REGISTRY = "de/planmarshall/core/findings/review-bot-registry.schema.json";
    private static final String SCHEMA_PREFIX = "https://cuioss.de/plan-marshall-mcp/schemas/";

    /** The three configuration files that hold configuration values, by file stem and resource name. */
    private static final Map<String, String> FILES = files();

    /**
     * The nodes whose compiled default the schema states only in its description, each with the reason it is compiled:
     * the parity test derives the default flag from the {@code default} keyword or from this map.
     */
    private static final Map<String, String> PROSE_DEFAULTS = Map.of(
            "project.review.optional_bots", "description 'Default []'",
            "project.review.operator_paths", "description 'Default []'",
            "workflow.plan.phase-5-execute.per_deliverable_verbs", "description 'Default [compile, module-tests]'",
            "local-config.commit_trailer", "description 'compiled default plan-marshall <noreply@cuioss.de>'",
            "local-config.envelope_budget_tokens", "description 'compiled fallback 400000'");

    /**
     * The nodes whose description mentions a default that is NOT compiled, each with the reason: a derived value, an
     * answered absence, or a phrase about default steps. The second assertion of Decision 4 refuses an unclassified
     * mention, so a new prose default cannot be added in silence.
     */
    private static final Map<String, String> STATED_BUT_NOT_COMPILED = Map.of(
            "project.base_branch", "the default is derived from origin/HEAD, not compiled",
            "project.review.required_bots", "the presence of the key is the roster answer",
            "workflow.plan.phase-5-execute.final_verification", "the default is derived from the build systems",
            "workflow.plan.phase-6-finalize.project_steps.*.order", "the phrase names the default steps, not a default",
            "local-config.integrations.provenance", "the description states that no machine default exists");

    /** The four file-metadata members the table leaves out, each with what they are instead. */
    private static final Map<String, String> LEFT_OUT = Map.of(
            "project.format_version", "the format version of the team file",
            "workflow.format_version", "the format version of the team file",
            "local-config.format_version", "the format version of the operator file",
            "local-config.revision", "the write revision of the operator file");

    private static final Pattern MENTIONS_DEFAULT = Pattern.compile("\\bdefault\\b", Pattern.CASE_INSENSITIVE);

    /** The schema document whose walk is in progress, the root an internal {@code $ref} resolves in. */
    private static Map<?, ?> currentDocument;

    @Test
    @DisplayName("Should hold exactly the keys of the three configuration schemas")
    void shouldHoldExactlyTheSchemaKeys() {
        var expected = new LinkedHashSet<>(walk().keySet());
        expected.removeAll(LEFT_OUT.keySet());
        var actual = new LinkedHashSet<String>();
        EffectiveConfigKeys.all().forEach(key -> actual.add(key.key()));

        assertEquals(expected.size(), EffectiveConfigKeys.all().size(),
                "The table should register every key once");
        assertEquals(new TreeSet<>(expected), new TreeSet<>(actual),
                () -> "The table should hold exactly the schema keys; missing %s, extra %s"
                        .formatted(difference(expected, actual), difference(actual, expected)));
    }

    @Test
    @DisplayName("Should carry the schema domain of every key")
    void shouldCarryTheSchemaDomains() {
        var expected = walk();
        var mismatches = new ArrayList<String>();
        for (var key : EffectiveConfigKeys.all()) {
            var schema = expected.get(key.key());
            if (schema == null) {
                mismatches.add(key.key() + ": no schema node");
            } else if (!schema.domain().equals(key.domain())) {
                mismatches.add("%s: table %s, schema %s".formatted(key.key(), key.domain(), schema.domain()));
            }
        }

        assertTrue(mismatches.isEmpty(), () -> "Every domain should equal the schema's: " + mismatches);
    }

    @Test
    @DisplayName("Should carry the compiled-default flag the schema and the prose defaults state")
    void shouldCarryTheCompiledDefaultFlag() {
        var expected = walk();
        var mismatches = new ArrayList<String>();
        for (var key : EffectiveConfigKeys.all()) {
            var schema = expected.get(key.key());
            var compiled = schema != null && (schema.keywordDefault() || PROSE_DEFAULTS.containsKey(key.key()));
            if (schema != null && schema.keywordDefault() && PROSE_DEFAULTS.containsKey(key.key())) {
                mismatches.add(key.key() + ": listed as a prose default although the keyword is present");
            }
            if (key.hasCompiledDefault() != compiled) {
                mismatches.add("%s: table %s, schema %s".formatted(key.key(), key.hasCompiledDefault(), compiled));
            }
        }

        assertTrue(mismatches.isEmpty(), () -> "The default flag should follow the schema: " + mismatches);
    }

    @Test
    @DisplayName("Should refuse a description that states an unclassified default")
    void shouldRefuseAnUndeclaredProseDefault() {
        var nodes = walk();
        var undeclared = new ArrayList<String>();
        nodes.forEach((key, node) -> {
            if (!node.keywordDefault() && MENTIONS_DEFAULT.matcher(node.description()).find()
                    && !PROSE_DEFAULTS.containsKey(key) && !STATED_BUT_NOT_COMPILED.containsKey(key)) {
                undeclared.add(key);
            }
        });

        assertTrue(undeclared.isEmpty(), () -> "A stated default should be classified, and these are not: " + undeclared);
        var absent = STATED_BUT_NOT_COMPILED.keySet().stream().filter(key -> !nodes.containsKey(key)).toList();
        assertTrue(absent.isEmpty(), () -> "A classified absence should name a real node, and these do not: " + absent);
    }

    @Test
    @DisplayName("Should name the three stems and leave the four metadata members out")
    void shouldNameTheThreeStemsAndLeaveTheMetadataOut() {
        assertEquals(Set.of("project", "workflow", "local-config"), EffectiveConfigKeys.stems(),
                "The stems should be the three configuration files");
        assertEquals(new TreeSet<>(FILES.keySet()), new TreeSet<>(EffectiveConfigKeys.stems()),
                "The schema files read should be the files whose stems are keys");

        var leftOut = LEFT_OUT.keySet().stream()
                .filter(key -> EffectiveConfigKeys.find(key).isPresent())
                .toList();
        assertTrue(leftOut.isEmpty(), () -> "A metadata member should be no configuration key: " + leftOut);
        var walked = walk();
        var unread = LEFT_OUT.keySet().stream().filter(key -> !walked.containsKey(key)).toList();
        assertTrue(unread.isEmpty(), () -> "The schema should still carry the metadata member: " + unread);
        for (var stem : List.of("domains", "build-map")) {
            assertFalse(EffectiveConfigKeys.stems().contains(stem),
                    () -> "The records-only file %s should contribute no key".formatted(stem));
        }
    }

    @Test
    @DisplayName("Should read the cleanup duration from the machine-config definition")
    void shouldReadTheCleanupDurationFromMachineConfig() {
        var expected = walk().get("local-config.cleanup.plans_after_days");

        assertTrue(expected != null, "The cleanup duration should be a key");
        assertEquals(new ConfigValueDomain.Union(List.of(new ConfigValueDomain.IntegerRange(7, null),
                        new ConfigValueDomain.Enumeration(List.of("never")))),
                expected.domain(), "The union should follow machine-config's definition");
        assertTrue(expected.keywordDefault(), "The cleanup duration should carry its default");
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        var only = new TreeSet<>(left);
        only.removeAll(right);
        return only;
    }

    private static Map<String, String> files() {
        var files = new LinkedHashMap<String, String>();
        files.put("project", "config-project.schema.json");
        files.put("workflow", "config-workflow.schema.json");
        files.put("local-config", "local-config.schema.json");
        return files;
    }

    /**
     * Walks the three configuration schemas.
     *
     * @return the schema node of every key of the three files, in stem and schema order
     */
    private static Map<String, SchemaNode> walk() {
        var walked = new LinkedHashMap<String, SchemaNode>();
        for (var file : FILES.entrySet()) {
            var schema = SchemaResource.load(CONFIG_DIRECTORY + file.getValue());
            currentDocument = schema;
            walkObject(schema, file.getKey(), walked);
        }
        return walked;
    }

    private static void walkObject(Map<?, ?> schema, String path, Map<String, SchemaNode> into) {
        for (var entry : properties(schema).entrySet()) {
            var name = String.valueOf(entry.getKey());
            var childPath = path.isEmpty() ? name : path + "." + name;
            var child = asObject(entry.getValue());
            record(childPath, child, into);
            walkNode(child, childPath, into);
        }
        for (var entry : additionalProperties(schema).entrySet()) {
            var childPath = path.isEmpty() ? "*" : path + ".*";
            record(childPath, entry.getValue(), into);
            walkNode(entry.getValue(), childPath, into);
        }
    }

    private static void walkNode(Map<?, ?> node, String path, Map<String, SchemaNode> into) {
        var resolved = resolve(node);
        if (resolved.containsKey("oneOf")) {
            return;
        }
        walkObject(resolved, path, into);
    }

    private static void record(String path, Map<?, ?> node, Map<String, SchemaNode> into) {
        var resolved = resolve(node);
        var description = resolved.get("description");
        into.put(path, new SchemaNode(domain(resolved), resolved.containsKey("default"),
                description == null ? "" : String.valueOf(description)));
    }

    private static Map<String, Map<?, ?>> properties(Map<?, ?> schema) {
        var resolved = resolve(schema);
        var properties = new LinkedHashMap<String, Map<?, ?>>();
        var own = resolved.get("properties");
        if (own != null) {
            asObject(own).forEach((name, value) -> properties.put(String.valueOf(name), asObject(value)));
        }
        var allOf = resolved.get("allOf");
        if (allOf != null) {
            for (var entry : SchemaResource.array(allOf)) {
                properties.putAll(properties(asObject(entry)));
            }
        }
        return properties;
    }

    private static Map<String, Map<?, ?>> additionalProperties(Map<?, ?> schema) {
        var resolved = resolve(schema);
        var additional = resolved.get("additionalProperties");
        if (additional == null || !(additional instanceof Map<?, ?>)) {
            return Map.of();
        }
        return Map.of("*", asObject(additional));
    }

    private static ConfigValueDomain domain(Map<?, ?> node) {
        var resolved = resolve(node);
        var oneOf = resolved.get("oneOf");
        if (oneOf != null) {
            return new ConfigValueDomain.Union(SchemaResource.array(oneOf).stream()
                    .map(entry -> domain(asObject(entry))).toList());
        }
        var type = resolved.get("type");
        if (type instanceof List<?> types) {
            var only = types.stream().map(String::valueOf).filter(name -> !"null".equals(name)).toList();
            if (only.size() == 1) {
                return domainOf(resolved, only.get(0));
            }
        }
        return domainOf(resolved, type == null ? "object" : String.valueOf(type));
    }

    private static ConfigValueDomain domainOf(Map<?, ?> node, String type) {
        return switch (type) {
            case "string" -> enumeration(node).orElseGet(ConfigValueDomain.Text::new);
            case "integer" -> new ConfigValueDomain.IntegerRange(bound(node, "minimum"), bound(node, "maximum"));
            case "boolean" -> new ConfigValueDomain.Bool();
            case "array" -> new ConfigValueDomain.ListOf(domain(asObject(node.get("items"))));
            default -> new ConfigValueDomain.Object();
        };
    }

    private static Optional<ConfigValueDomain> enumeration(Map<?, ?> node) {
        var values = node.get("enum");
        if (values != null) {
            return Optional.of(new ConfigValueDomain.Enumeration(
                    SchemaResource.array(values).stream().map(String::valueOf).toList()));
        }
        var constant = node.get("const");
        if (constant != null) {
            return Optional.of(new ConfigValueDomain.Enumeration(List.of(String.valueOf(constant))));
        }
        return Optional.empty();
    }

    private static Integer bound(Map<?, ?> node, String keyword) {
        var value = node.get(keyword);
        return value instanceof Number number ? Integer.valueOf(number.intValue()) : null;
    }

    /**
     * Resolves an internal or external {@code $ref} of a schema node, following chained references.
     */
    private static Map<?, ?> resolve(Map<?, ?> node) {
        var current = node;
        var seen = new LinkedHashSet<String>();
        while (current.get("$ref") instanceof String reference) {
            if (!seen.add(reference)) {
                fail("The schema holds a reference cycle at " + reference);
            }
            current = asObject(target(reference));
        }
        return current;
    }

    private static Object target(String reference) {
        if (reference.startsWith("#/")) {
            Map<?, ?> node = currentDocument;
            for (var segment : reference.substring(2).split("/")) {
                node = asObject(node.get(segment));
            }
            return node;
        }
        var fragment = reference.contains("#") ? reference.substring(reference.indexOf('#') + 1) : "";
        Map<?, ?> external;
        if (reference.startsWith(SCHEMA_PREFIX + "machine-config.json")) {
            external = asObject(parseMachineConfig());
        } else if (reference.startsWith(SCHEMA_PREFIX + "review-bot-registry.json")) {
            external = SchemaResource.load(REVIEW_BOT_REGISTRY);
        } else {
            fail("The schema names an external reference this test does not know: " + reference);
            return null;
        }
        var node = external;
        if (!fragment.isEmpty()) {
            for (var segment : fragment.replaceFirst("^/", "").split("/")) {
                node = asObject(node.get(segment));
            }
        }
        return node;
    }

    private static Object parseMachineConfig() {
        try {
            return SchemaResource.parse(Files.readString(Path.of(MACHINE_CONFIG), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("The machine-config schema should be readable at " + MACHINE_CONFIG,
                    exception);
        }
    }

    private static Map<?, ?> asObject(Object value) {
        return SchemaResource.object(value);
    }

    /**
     * The facts of one schema node the table is compared with.
     *
     * @param domain         the value domain the node's keywords express
     * @param keywordDefault whether the node carries the {@code default} keyword
     * @param description    the node's description, empty when it carries none
     */
    private record SchemaNode(ConfigValueDomain domain, boolean keywordDefault, String description) {
    }
}
