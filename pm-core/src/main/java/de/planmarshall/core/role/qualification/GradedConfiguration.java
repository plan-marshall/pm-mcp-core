/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleSkill;

/**
 * The graded members a qualification record is bound to, recorded beside its digest (Store Schemas § Role
 * Qualification Record, {@code configuration}; Model Work § Role Qualification): the effective {@code harness},
 * {@code model} and {@code effort}, the artifact's {@code tools} and {@code class}, and the digests of its skills as the
 * project resolves them, with the major.minor of the enrolled harness version.
 * <p>
 * Members outside it — {@code count}, {@code form}, {@code recycling}, the off-track settings,
 * {@code budget_floor_seconds}, {@code context_budget_tokens} and {@code description} — never invalidate a record.
 *
 * @param harness        the client id of the graded harness
 * @param harnessVersion the major.minor of the enrolled harness version
 * @param model          the effective model
 * @param effort         the effective effort; {@code null} when the configuration names none
 * @param tools          the artifact's tools
 * @param roleClass      the artifact's class
 * @param skillDigests   the digests of the resolved skills by skill URI
 * @since 0.1
 */
public record GradedConfiguration(String harness, String harnessVersion, String model, @Nullable String effort,
List<String> tools, RoleClass roleClass, Map<String, String> skillDigests) {

    private static final Pattern HARNESS_NAME = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+");
    private static final Pattern DIGEST = Pattern.compile("[\\da-f]{64}");

    /**
     * @throws IllegalArgumentException if a value is outside the record's format
     */
    public GradedConfiguration {
        require(harness, HARNESS_NAME, "harness");
        require(harnessVersion, VERSION, "harness_version");
        Objects.requireNonNull(model, "model");
        if (model.isEmpty()) {
            throw new IllegalArgumentException("'model' must not be empty");
        }
        tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
        Objects.requireNonNull(roleClass, "roleClass");
        skillDigests = Map.copyOf(Objects.requireNonNull(skillDigests, "skillDigests"));
        for (var entry : skillDigests.entrySet()) {
            if (!entry.getKey().startsWith("skill://pm/")) {
                throw new IllegalArgumentException("Not a skill URI: " + entry.getKey());
            }
            require(entry.getValue(), DIGEST, "skill digest");
        }
    }

    /**
     * @param role the effective role that was graded
     * @return the graded members of that role
     */
    public static GradedConfiguration of(EffectiveRole role) {
        Objects.requireNonNull(role, "role");
        var digests = new LinkedHashMap<String, String>();
        for (RoleSkill skill : role.skills()) {
            digests.put(skill.uri(), skill.digest());
        }
        return new GradedConfiguration(role.harness(), role.harnessVersion(), role.model(), role.effort().value(),
                role.artifact().tools(), role.roleClass(), digests);
    }

    private static void require(String value, Pattern pattern, String field) {
        Objects.requireNonNull(value, field);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is outside its format: %s".formatted(field, value));
        }
    }
}
