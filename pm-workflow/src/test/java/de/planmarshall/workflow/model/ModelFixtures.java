/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.HarnessProfile;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleArtifactValidator;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleResolution;
import de.planmarshall.core.role.RoleResolver;
import de.planmarshall.core.role.RoleSkill;

/**
 * The fixtures of the model-caller tests (test helper): an effective role of any class and form, a resolver over a map of
 * roles, a recorder of the qualification lookup, the availability guard and the task queue.
 * <p>
 * The fixtures name a generic harness and generic roles; a test that needs a role the specification fixes by name (the
 * judge, the steward) names it explicitly.
 */
final class ModelFixtures {

    static final String HARNESS = "acme-cli";
    static final String MODEL = "acme-standard";
    static final String VERSION = "1.4";
    static final String QUEUE_TASK = "qt-0123456789abcdef";
    static final String DIGEST = "a".repeat(64);

    private ModelFixtures() {
    }

    /** @return an effective role of the class and form on the fixture harness */
    static EffectiveRole role(String name, RoleClass roleClass, RoleForm form) {
        return roleIn(name, roleClass, form, HARNESS, MODEL);
    }

    /**
     * @param name    the role name
     * @param roleClass the class
     * @param form    the form
     * @param harness the effective harness
     * @param model   the effective model
     * @return an effective role with those values
     */
    static EffectiveRole roleIn(String name, RoleClass roleClass, RoleForm form, String harness, String model) {
        return EffectiveRole.of(artifact(name, roleClass, form), harness, model, new EffortValue(null),
                List.of(new RoleSkill("skill://pm/role/" + name + "/SKILL.md", DIGEST)), VERSION, Optional.empty(),
                profile());
    }

    /** @return the profile of the harness, which runs every class */
    static HarnessProfile profile() {
        var classes = new LinkedHashMap<RoleClass, HarnessProfile.ToolSet>();
        classes.put(RoleClass.WRITER, HarnessProfile.ToolSet.ENFORCED);
        classes.put(RoleClass.READ_ONLY, HarnessProfile.ToolSet.ADVISORY);
        classes.put(RoleClass.ZERO_TOOL, HarnessProfile.ToolSet.ENFORCED);
        return new HarnessProfile(HARNESS, true, true, Set.of("*"), classes,
                Map.of(MODEL, List.of("medium", "high")),
                Map.of("frontier", "acme-frontier", "standard", MODEL, "fast", "acme-fast"),
                400_000, 3, new HarnessProfile.Resumability(true, 600, true, 604_800), "ask_user");
    }

    static RoleArtifact artifact(String name, RoleClass roleClass, RoleForm form) {
        var tools = roleClass == RoleClass.ZERO_TOOL
                ? "[]"
                : "[\"pm_wait\",\"pm_state\",\"pm_do\",\"pm_skill\",\"pm_skill_file\"]";
        var recycling = form == RoleForm.WARM
                ? ",\"recycling\":{\"idle_wakeups\":null,\"token_budget\":10000,\"tasks_per_generation\":null}"
                : "";
        var json = """
                {"format_version":1,"name":"%s","description":"A role.","class":"%s","tools":%s,
                 "harness":"acme-cli","model":"acme-standard","effort":null,"form":"%s","count":2,
                 "skills":["skill://pm/role/%s/SKILL.md"],"budget_floor_seconds":300%s}
                """.formatted(name, roleClass.wireName(), tools, form.wireName(), name, recycling);
        return RoleArtifactValidator.validate(json.getBytes(StandardCharsets.UTF_8), name);
    }

    /**
     * @param roles the defined roles by name
     * @return a resolver that refuses every other role as absent
     */
    static RoleResolver resolver(Map<String, EffectiveRole> roles) {
        return name -> roles.containsKey(name)
                ? new RoleResolution.Defined(roles.get(name))
                : new RoleResolution.Undefined(name, Optional.empty(), "the role is absent");
    }

    /** The qualification lookup of the tests; a lookup of a skipped role fails the test. */
    static final class Qualifications implements QualificationLookup {

        private final List<String> looked = new ArrayList<>();
        private final List<String> digests = new ArrayList<>();
        private final Map<String, RoleForm> forms = new LinkedHashMap<>();

        Qualifications qualifies(String role, RoleForm form) {
            forms.put(role, form);
            return this;
        }

        List<String> looked() {
            return looked;
        }

        List<String> digests() {
            return digests;
        }

        @Override
        public Optional<RoleForm> qualifiedForm(String roleName, String qualificationDigest,
                RoleForm artifactForm) {
            looked.add(roleName);
            digests.add(qualificationDigest);
            return Optional.ofNullable(forms.get(roleName));
        }
    }

    /** The availability guard of the tests. */
    static final class Guard implements AvailabilityGuard {

        private final Set<String> held = new java.util.HashSet<>();

        Guard holds(String role) {
            held.add(role);
            return this;
        }

        @Override
        public boolean holds(String roleName, String harness) {
            return held.contains(roleName);
        }
    }

    /** The task queue of the tests, which records what the model caller asks of it. */
    static final class RecordingQueue implements ModelQueue {

        private final List<Enqueue> enqueued = new ArrayList<>();
        private final List<Close> closed = new ArrayList<>();

        List<Enqueue> enqueued() {
            return enqueued;
        }

        List<Close> closed() {
            return closed;
        }

        @Override
        public Queued enqueue(Enqueue request) {
            enqueued.add(request);
            return new Queued(QUEUE_TASK);
        }

        @Override
        public void close(Close request) {
            closed.add(request);
        }
    }
}
