/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.diagram.WorkflowDiagramGenerator;
import org.jspecify.annotations.Nullable;

/**
 * The instance resources of a plan (PM-IMPL-5; MCP Tools § Resource URI Templates; Workflow DSL § Display): the DSL
 * text of a version with its header, the diagram of the version in force with its active state, and the version with
 * the composition event that produced it.
 * <p>
 * The class is the package's API for {@code pm://plan/{plan_id}/instance[/{version}]} and the instance half of
 * {@code pm://plan/{plan_id}/diagram}: the assembly binds the resource URIs and supplies the plan's id, its active
 * state and its {@code instance_version}, and asks this class for the payload. It reads the plan's store through
 * {@link PlanInstanceVersions}, never a level's copy of a unit, so a resource shows the process the plan actually runs.
 *
 * @since 0.1
 */
public final class PlanInstanceResources {

    private final PlanInstanceStore store;

    private final PlanInstanceVersions versions;

    /**
     * @param store the store of the plan
     * @throws NullPointerException if the store is {@code null}
     */
    public PlanInstanceResources(PlanInstanceStore store) {
        this.store = Objects.requireNonNull(store, "store");
        this.versions = new PlanInstanceVersions(store);
    }

    /**
     * Reads one version as the instance it is: its plan unit, its header and every composed file it names.
     *
     * @param version an instance version
     * @return the version, empty when it was never written or a file of it is unreadable
     */
    public Optional<PlanInstance> version(int version) {
        var plan = versions.planUnit(version).orElse(null);
        var text = store.readVersion(version).orElse(null);
        var instance = plan == null ? null : plan.instance();
        if (plan == null || text == null || instance == null) {
            return Optional.empty();
        }
        var header = HeaderJson.read(instance.json());
        var composed = new ArrayList<ComposedUnit>();
        for (var reference : header.inputs().templates()) {
            if (PlanInstanceComposer.PLAN_UNIT.equals(reference.unit())) {
                continue;
            }
            var unit = versions.composedUnit(version, reference.unit());
            if (unit.isEmpty()) {
                return Optional.empty();
            }
            composed.add(ComposedUnit.of(reference.unit(), unit.get()));
        }
        return Optional.of(new PlanInstance(version, header, plan, text, composed, null));
    }

    /**
     * The read of {@code pm://plan/{plan_id}/instance[/{version}]}: the version's DSL text with its header.
     *
     * @param version an instance version
     * @return the text of the version with the header it declares, empty when the version was never written
     */
    public Optional<VersionText> text(int version) {
        return version(version).map(instance -> new VersionText(instance.version(), instance.header(),
                instance.text()));
    }

    /**
     * The diagram of the version in force: the composed process of the version, its active state marked, its pruned
     * elements with their reason and its waived gates with their waiver id.
     *
     * @param versionInForce the value of {@code instance_version} of the plan's status
     * @param activeState    the plan's current state, or {@code null} when the plan has not entered one
     * @return the Mermaid diagram, empty when the version was never written
     */
    public Optional<String> diagram(int versionInForce, @Nullable String activeState) {
        return version(versionInForce)
                .map(instance -> WorkflowDiagramGenerator.renderInstanceMermaid(instance, activeState));
    }

    /**
     * The version in force with the composition event that produced it, for the status resource.
     *
     * @param versionInForce the value of {@code instance_version} of the plan's status
     * @return the version and its event, empty when the version was never written
     */
    public Optional<VersionStatus> status(int versionInForce) {
        return version(versionInForce).map(instance -> new VersionStatus(instance.version(),
                instance.header().event(), instance.header().composedAt()));
    }

    /**
     * The DSL text of one instance version and the header it carries.
     *
     * @param version    the instance version
     * @param header     the instance header of the version
     * @param text       the DSL text of the version's plan unit, every line ended by a line feed
     * @since 0.1
     */
    public record VersionText(int version, PlanInstanceHeader header, String text) {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public VersionText {
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * The version in force and the composition event that produced it.
     *
     * @param version    the instance version in force
     * @param event      the composition event of the version
     * @param composedAt when the version was composed
     * @since 0.1
     */
    public record VersionStatus(int version, CompositionEvent event, Instant composedAt) {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public VersionStatus {
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(composedAt, "composedAt");
        }
    }
}
