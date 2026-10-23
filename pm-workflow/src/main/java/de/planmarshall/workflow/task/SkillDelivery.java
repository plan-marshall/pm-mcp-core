/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.planmarshall.core.role.RoleSkill;

/**
 * The skills delivered to each worker generation (Model Work § Skill Delivery Record &amp; Re-Delivery).
 * <p>
 * A generation starts with an empty record, so its first offer carries every required skill in-band. A skill whose
 * digest changed since its delivery is delivered again. A skill whose digest is unchanged is named by reference only.
 * The record lives for the generations of one runtime; a new generation never inherits the record of another.
 *
 * @since 0.1
 */
public final class SkillDelivery {

    private final Map<String, Map<String, String>> delivered = new LinkedHashMap<>();

    /**
     * Records the delivery of the required skills to a generation and reports which of them must travel in-band.
     *
     * @param generation the generation
     * @param required   the skills the task requires
     * @return the skills to deliver in-band: those the generation has not received, or whose digest changed
     */
    public synchronized List<RoleSkill> deliver(String generation, List<RoleSkill> required) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(required, "required");
        var record = delivered.computeIfAbsent(generation, key -> new LinkedHashMap<>());
        var inBand = new ArrayList<RoleSkill>();
        for (var skill : required) {
            if (!skill.digest().equals(record.get(skill.uri()))) {
                inBand.add(skill);
            }
        }
        required.forEach(skill -> record.put(skill.uri(), skill.digest()));
        return List.copyOf(inBand);
    }

    /**
     * @param generation the generation
     * @return the skills the generation has received, by URI and digest; empty for a new generation
     */
    public synchronized Map<String, String> recordOf(String generation) {
        Objects.requireNonNull(generation, "generation");
        return Map.copyOf(delivered.getOrDefault(generation, Map.of()));
    }

    /**
     * Forgets the record of a generation that ended. Forgetting a new generation is a no-op.
     *
     * @param generation the generation
     */
    public synchronized void forget(String generation) {
        delivered.remove(Objects.requireNonNull(generation, "generation"));
    }
}
