/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.EnrolledHarness;
import de.planmarshall.core.profile.JsonNode;

/**
 * The fixtures of the role tests (test helper): one role artifact that passes validation and passes every validation row,
 * which a test mutates at one JSON Pointer; one harness profile; one enrolled harness; and a store-backed
 * {@link RoleEnvironment} whose values a test changes between two reads.
 * <p>
 * The fixtures name a generic harness and a generic role only, so no test fixture of production code names a client or a
 * shipped role.
 */
final class RoleFixtures {

    /** The role name of the base artifact. */
    static final String ROLE = "implement";

    /** The harness of the base artifact. */
    static final String HARNESS = "acme-cli";

    /** The model of the base artifact. */
    static final String MODEL = "acme-standard";

    /** The digest of a resolved skill the fixture environment reports unless a test changes it. */
    static final String SKILL_DIGEST = "a".repeat(64);

    private RoleFixtures() {
    }

    /** @return an artifact that passes validation and every validation row */
    static RoleFixture valid() {
        return new RoleFixture(base());
    }

    /** @return the base artifact as a document */
    static byte[] validBytes() {
        return valid().bytes();
    }

    private static Map<String, Object> base() {
        var root = new LinkedHashMap<String, Object>();
        root.put("format_version", 1);
        root.put("name", ROLE);
        root.put("description", "Implements the tasks of a plan.");
        root.put("class", "read_only");
        root.put("tools", new ArrayList<>(List.of("pm_wait", "pm_state", "pm_do", "pm_skill", "pm_skill_file")));
        root.put("harness", HARNESS);
        root.put("model", MODEL);
        root.put("effort", null);
        root.put("form", "fresh");
        root.put("count", 2);
        root.put("skills", new ArrayList<>(List.of("skill://pm/role/implement/SKILL.md")));
        root.put("budget_floor_seconds", 300);
        return root;
    }

    /** @return the profile of the base harness, which runs every class and allows every model */
    static HarnessProfile harness() {
        return harness(HARNESS);
    }

    /**
     * @param clientId a client id
     * @return a profile of that harness that runs every class and allows every model
     */
    static HarnessProfile harness(String clientId) {
        return new HarnessProfile(clientId, true, true, Set.of("*"),
                Map.of(RoleClass.WRITER, HarnessProfile.ToolSet.ENFORCED,
                        RoleClass.READ_ONLY, HarnessProfile.ToolSet.ADVISORY,
                        RoleClass.ZERO_TOOL, HarnessProfile.ToolSet.ENFORCED),
                Map.of(MODEL, List.of("medium", "high"), "acme-frontier", List.of("medium", "high"),
                        "acme-fast", List.of()),
                Map.of("frontier", "acme-frontier", "standard", MODEL, "fast", "acme-fast"),
                400_000, 3,
                new HarnessProfile.Resumability(true, 600, true, 604_800), "ask_user");
    }

    /**
     * @param clientId a client id
     * @param version  the exact version the harness reported
     * @return the enrolled build of that harness
     */
    static EnrolledHarness enrolled(String clientId, String version) {
        return new EnrolledHarness("acme", version, "sha256:" + "0".repeat(64));
    }

    /** @return the enrolled build of the base harness */
    static EnrolledHarness enrolled() {
        return enrolled(HARNESS, "1.4.2");
    }

    /** @return a store-backed environment with the base harness, its enrolment and the base catalogue */
    static TestEnvironment environment() {
        return new TestEnvironment();
    }

    /**
     * @param harness a client id
     * @param models  the models it allows
     * @return the catalogue that allows exactly those models on that harness
     */
    static ModelCatalogue catalogue(String harness, String... models) {
        return new ModelCatalogue(Map.of(harness, new LinkedHashSet<>(List.of(models))));
    }

    /** @return the catalogue that allows the base harness's base model */
    static ModelCatalogue catalogue() {
        return catalogue(HARNESS, MODEL);
    }

    /**
     * @param shipped the shipped role documents by role name
     * @return the sets with only a shipped set
     */
    static RoleSets shipped(Map<String, byte[]> shipped) {
        var entries = new LinkedHashMap<String, RoleSets.ShippedRole>();
        for (var entry : shipped.entrySet()) {
            entries.put(entry.getKey(), new RoleSets.ShippedRole(entry.getValue()));
        }
        return RoleSets.of(entries);
    }

    /** @return the shipped set of the base artifact alone */
    static RoleSets shippedValid() {
        return shipped(Map.of(ROLE, validBytes()));
    }

    /** A role artifact document a test mutates at one pointer. */
    static final class RoleFixture {

        private final Map<String, Object> root;

        private RoleFixture(Map<String, Object> root) {
            this.root = copy(root);
        }

        /**
         * @param pointer a JSON Pointer
         * @param value   the value to set; {@code null} sets JSON null
         * @return this fixture
         */
        RoleFixture set(String pointer, Object value) {
            var tokens = tokens(pointer);
            var parent = parent(tokens);
            var last = tokens.get(tokens.size() - 1);
            if (parent instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                var object = (Map<String, Object>) map;
                object.put(last, value);
            } else if (parent instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                var elements = (List<Object>) list;
                elements.set(Integer.parseInt(last), value);
            }
            return this;
        }

        /**
         * @param pointer a JSON Pointer
         * @return this fixture without the value
         */
        RoleFixture remove(String pointer) {
            var tokens = tokens(pointer);
            var parent = parent(tokens);
            var last = tokens.get(tokens.size() - 1);
            if (parent instanceof Map<?, ?> map) {
                map.remove(last);
            } else if (parent instanceof List<?> list) {
                list.remove(Integer.parseInt(last));
            }
            return this;
        }

        /** @return the document, UTF-8 encoded */
        byte[] bytes() {
            return CanonicalJson.write(tree()).getBytes(StandardCharsets.UTF_8);
        }

        /** @return the document tree */
        JsonNode tree() {
            return node(root);
        }

        /** @return the artifact the fixture validates to */
        RoleArtifact artifact() {
            return RoleArtifactValidator.validate(bytes(), ROLE);
        }

        private Object parent(List<String> tokens) {
            Object current = root;
            for (var index = 0; index < tokens.size() - 1; index++) {
                current = child(current, tokens.get(index));
            }
            return current;
        }

        private static Object child(Object current, String token) {
            if (current instanceof Map<?, ?> map) {
                return map.get(token);
            }
            if (current instanceof List<?> list) {
                return list.get(Integer.parseInt(token));
            }
            throw new IllegalArgumentException("No value at '" + token + "'");
        }
    }

    private static List<String> tokens(String pointer) {
        return new ArrayList<>(List.of(pointer.substring(1).split("/", -1)));
    }

    static JsonNode node(Object value) {
        if (value == null) {
            return JsonNode.nullValue();
        }
        if (value instanceof Map<?, ?> map) {
            var members = new LinkedHashMap<String, JsonNode>();
            for (var entry : map.entrySet()) {
                members.put(String.valueOf(entry.getKey()), node(entry.getValue()));
            }
            return JsonNode.object(members);
        }
        if (value instanceof List<?> list) {
            return JsonNode.array(list.stream().map(RoleFixtures::node).toList());
        }
        if (value instanceof String text) {
            return JsonNode.string(text);
        }
        if (value instanceof Integer number) {
            return JsonNode.integer(number);
        }
        if (value instanceof Long number) {
            return JsonNode.integer(number);
        }
        if (value instanceof Double number) {
            return JsonNode.decimal(BigDecimal.valueOf(number));
        }
        if (value instanceof Boolean bool) {
            return JsonNode.bool(bool);
        }
        throw new IllegalArgumentException("Not a JSON value: " + value.getClass());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copy(Map<String, Object> map) {
        var copy = new LinkedHashMap<String, Object>();
        for (var entry : map.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return copy((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            var copy = new ArrayList<Object>();
            for (var element : list) {
                copy.add(copyValue(element));
            }
            return copy;
        }
        return value;
    }

    /**
     * The store-backed environment of the tests: every fact a role or task resolution reads, which a test changes between
     * two reads to prove that the value is read again.
     */
    static final class TestEnvironment implements RoleEnvironment {

        private final Map<String, HarnessProfile> profiles = new LinkedHashMap<>();
        private final Map<String, EnrolledHarness> enrolled = new LinkedHashMap<>();
        private final Map<String, String> skills = new LinkedHashMap<>();
        private final Map<String, String> weights = new LinkedHashMap<>();
        private ModelCatalogue machineCatalogue;
        private ModelCatalogue projectCatalogue;
        private RoleAssignments machineAssignments;
        private RoleAssignments projectAssignments;
        private MachineConfiguration machineConfiguration;
        private final Map<String, EffortValue> defaultEfforts = new LinkedHashMap<>();

        private TestEnvironment() {
            profiles.put(HARNESS, harness());
            enrolled.put(HARNESS, RoleFixtures.enrolled());
            machineCatalogue = RoleFixtures.catalogue();
            machineConfiguration = new MachineConfiguration(Optional.of(HARNESS));
            defaultEfforts.put("steward", new EffortValue("medium"));
        }

        TestEnvironment profile(String clientId, HarnessProfile profile) {
            profiles.put(clientId, profile);
            return this;
        }

        TestEnvironment enrolled(String clientId, EnrolledHarness harness) {
            enrolled.put(clientId, harness);
            return this;
        }

        TestEnvironment skill(String uri, String digest) {
            skills.put(uri, digest);
            return this;
        }

        TestEnvironment weightsIdentity(String clientId, String model, String identity) {
            weights.put(clientId + "/" + model, identity);
            return this;
        }

        TestEnvironment machineCatalogue(ModelCatalogue catalogue) {
            this.machineCatalogue = catalogue;
            return this;
        }

        TestEnvironment projectCatalogue(ModelCatalogue catalogue) {
            this.projectCatalogue = catalogue;
            return this;
        }

        TestEnvironment machineAssignments(RoleAssignments assignments) {
            this.machineAssignments = assignments;
            return this;
        }

        TestEnvironment projectAssignments(RoleAssignments assignments) {
            this.projectAssignments = assignments;
            return this;
        }

        TestEnvironment machineConfiguration(MachineConfiguration configuration) {
            this.machineConfiguration = configuration;
            return this;
        }

        TestEnvironment defaultEffort(String role, String effort) {
            defaultEfforts.put(role, new EffortValue(effort));
            return this;
        }

        @Override
        public Optional<HarnessProfile> profile(String clientId) {
            return Optional.ofNullable(profiles.get(clientId));
        }

        @Override
        public Optional<EnrolledHarness> enrolledHarness(String clientId) {
            return Optional.ofNullable(enrolled.get(clientId));
        }

        @Override
        public Optional<ModelCatalogue> machineCatalogue() {
            return Optional.ofNullable(machineCatalogue);
        }

        @Override
        public Optional<ModelCatalogue> projectCatalogue() {
            return Optional.ofNullable(projectCatalogue);
        }

        @Override
        public Optional<RoleAssignments> machineAssignments() {
            return Optional.ofNullable(machineAssignments);
        }

        @Override
        public Optional<RoleAssignments> projectAssignments() {
            return Optional.ofNullable(projectAssignments);
        }

        @Override
        public Optional<MachineConfiguration> machineConfiguration() {
            return Optional.ofNullable(machineConfiguration);
        }

        @Override
        public Optional<EffortValue> defaultEffort(String role) {
            return Optional.ofNullable(defaultEfforts.get(role));
        }

        @Override
        public Optional<RoleSkill> resolveSkill(String uri) {
            var digest = skills.get(uri);
            if (digest != null) {
                return Optional.of(new RoleSkill(uri, digest));
            }
            return uri.startsWith("skill://pm/role/") || uri.startsWith("skill://pm/ext/")
                    ? Optional.of(new RoleSkill(uri, SKILL_DIGEST))
                    : Optional.empty();
        }

        @Override
        public Optional<String> weightsIdentity(String clientId, String model) {
            return Optional.ofNullable(weights.get(clientId + "/" + model));
        }
    }
}
