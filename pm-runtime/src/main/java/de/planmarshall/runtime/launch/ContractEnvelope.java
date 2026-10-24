/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.util.List;
import java.util.Objects;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.JsonNode;

/**
 * The contract envelope of a fresh worker's one task (PM-WORK-1, Worker Launch): the scope and the entity the worker
 * passes to {@code pm_state} and {@code pm_do}, the first call, the links the entity offers with their named parameter
 * schemas, the worker's result schema, and the rule that deliverables reach the server only through the entity's
 * {@code submit} links. One definition drives both the composed prompt and the job-bound checks of the worker's calls.
 *
 * @param scopeKind    the type of the scope entity, for example {@code plan}, {@code epic} or {@code no_plan}
 * @param scopeId      the plan or epic id; {@code null} for {@code no_plan}
 * @param entity       the scope entity the task works on
 * @param firstCall    the first call the worker makes, always {@code pm_state} on the entity
 * @param links        the links the entity offers
 * @param resultSchema the worker's result schema
 * @since 0.1
 */
public record ContractEnvelope(String scopeKind, String scopeId, String entity, String firstCall, List<LinkSchema> links,
JsonNode resultSchema) {

    /** The first call of every fresh worker. */
    public static final String PM_STATE = "pm_state";

    /** Copies the links and refuses a missing envelope. */
    public ContractEnvelope {
        Objects.requireNonNull(scopeKind, "scopeKind");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(firstCall, "firstCall");
        links = List.copyOf(Objects.requireNonNull(links, "links"));
        Objects.requireNonNull(resultSchema, "resultSchema");
    }

    /**
     * @param scopeKind    the type of the scope
     * @param scopeId      the plan or epic id; {@code null} for no plan
     * @param entity       the scope entity
     * @param links        the links the entity offers
     * @param resultSchema the worker's result schema
     * @return the contract envelope of a fresh worker
     */
    public static ContractEnvelope of(String scopeKind, String scopeId, String entity, List<LinkSchema> links,
            JsonNode resultSchema) {
        return new ContractEnvelope(scopeKind, scopeId, entity, PM_STATE, links, resultSchema);
    }

    /**
     * @return the envelope as the composer renders it into the launch prompt, without a per-launch value before it
     */
    public String render() {
        var out = new StringBuilder();
        out.append("## Contract envelope\n\n");
        out.append("- scope: ").append(scopeKind);
        if (scopeId != null) {
            out.append('/').append(scopeId);
        }
        out.append('\n');
        out.append("- entity: ").append(entity).append('\n');
        out.append("- first call: ").append(firstCall).append(" on ").append(entity).append('\n');
        out.append("- links:\n");
        for (var link : links) {
            out.append("  - ").append(link.target());
            if (!link.parameters().isEmpty()) {
                out.append(" (");
                for (var index = 0; index < link.parameters().size(); index++) {
                    if (index > 0) {
                        out.append(", ");
                    }
                    var parameter = link.parameters().get(index);
                    out.append(parameter.name()).append(':').append(parameter.type());
                }
                out.append(')');
            }
            out.append('\n');
        }
        out.append("- result schema: ").append(CanonicalJson.write(resultSchema)).append('\n');
        out.append("- deliverables reach the server only through the entity's submit links\n");
        return out.toString();
    }
}
