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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import lombok.experimental.UtilityClass;

/**
 * The compiled table of the effective configuration keys of Store Schemas 07 and 08 (PM-EXT-6, PM-IMPL-5).
 * <p>
 * The table is code, not a copy of the schema in a resource: {@code doc/} is not on the classpath of a JAR, and a second
 * copy of the schema would drift from the first. It is pinned to the schema by
 * {@code EffectiveConfigKeysSchemaParityTest}, which walks the three schema files and fails on a key, an enumeration
 * value, a bound or a compiled default the table disagrees with.
 * <p>
 * Every addressable node of the three files is a key, not only a leaf: a node with members is a key of domain
 * {@link ConfigValueDomain.Object}, and a map node contributes the wildcard segment {@code *} that stands for exactly one
 * segment. The file stems {@code domains} and {@code build-map} are lists and maps of records no guard or unit reads, and
 * the four file-metadata members {@code format_version} (three files) and {@code revision} (the operator file) are not
 * configuration values; the parity test asserts each of them rather than leaving their absence implicit.
 *
 * @since 0.1
 */
@UtilityClass
public final class EffectiveConfigKeys {

    private static final ConfigValueDomain OBJECT = new ConfigValueDomain.Object();
    private static final ConfigValueDomain TEXT = new ConfigValueDomain.Text();
    private static final ConfigValueDomain BOOL = new ConfigValueDomain.Bool();

    /** The execution posture of an optional finalize step, or off. */
    private static final ConfigValueDomain LANE =
            new ConfigValueDomain.Enumeration(List.of("off", "minimal", "standard", "full"));

    /** The variant of a project integration: the server's own client or the provider's command-line tool. */
    private static final ConfigValueDomain INTEGRATION_VARIANT =
            new ConfigValueDomain.Enumeration(List.of("native", "cli"));

    /** The build verbs a project may name in a verification step. */
    private static final ConfigValueDomain BUILD_VERB = new ConfigValueDomain.Enumeration(List.of("clean", "compile",
            "test-compile", "module-tests", "integration-tests", "e2e", "coverage", "mutation", "benchmark",
            "quality-gate", "arch-gate", "verify", "install", "clean-install", "package"));

    /** {@code machine-config.json#/$defs/cleanup_duration}: days of at least seven, or never. */
    private static final ConfigValueDomain CLEANUP_DURATION = new ConfigValueDomain.Union(
            List.of(new ConfigValueDomain.IntegerRange(7, null), new ConfigValueDomain.Enumeration(List.of("never"))));

    private static final List<EffectiveConfigKey> TABLE = List.of(
            // project.json: the team file of repository and collaboration policy.
            key("project.base_branch", TEXT, false),
            key("project.branch_prefix", TEXT, true),
            key("project.ci_provider", new ConfigValueDomain.Enumeration(List.of("github", "gitlab", "forgejo")), false),
            key("project.integrations", OBJECT, false),
            key("project.integrations.sonar", OBJECT, false),
            key("project.integrations.sonar.organization", TEXT, false),
            key("project.integrations.sonar.project_key", TEXT, false),
            key("project.issues", OBJECT, false),
            key("project.issues.write_back", BOOL, true),
            key("project.merge_method", new ConfigValueDomain.Enumeration(List.of("squash", "merge", "rebase")), false),
            key("project.review", OBJECT, false),
            key("project.review.bot_registry", new ConfigValueDomain.ListOf(OBJECT), false),
            key("project.review.currency",
                    new ConfigValueDomain.Enumeration(List.of("exact_sha", "after_last_push")), true),
            key("project.review.operator_paths", new ConfigValueDomain.ListOf(TEXT), true),
            key("project.review.optional_bots", new ConfigValueDomain.ListOf(TEXT), true),
            key("project.review.required_bots", new ConfigValueDomain.ListOf(TEXT), false),

            // workflow.json: the team file of plan workflow policy.
            key("workflow.landing",
                    new ConfigValueDomain.Enumeration(List.of("landing.pull-request", "landing.main", "landing.files")),
                    false),
            key("workflow.plan", OBJECT, false),
            key("workflow.plan.phase-1-init", OBJECT, false),
            key("workflow.plan.phase-1-init.minimum_lane",
                    new ConfigValueDomain.Enumeration(List.of("light", "deep")), true),
            key("workflow.plan.phase-2-refine", OBJECT, false),
            key("workflow.plan.phase-2-refine.compatibility",
                    new ConfigValueDomain.Enumeration(List.of("breaking", "deprecation", "smart_and_ask")), true),
            key("workflow.plan.phase-2-refine.simplicity",
                    new ConfigValueDomain.Enumeration(List.of("lean", "pragmatic", "defensive")), true),
            key("workflow.plan.phase-5-execute", OBJECT, false),
            key("workflow.plan.phase-5-execute.final_verification", new ConfigValueDomain.ListOf(BUILD_VERB), false),
            key("workflow.plan.phase-5-execute.per_deliverable_verbs", new ConfigValueDomain.ListOf(BUILD_VERB), true),
            key("workflow.plan.phase-6-finalize", OBJECT, false),
            key("workflow.plan.phase-6-finalize.project_steps", OBJECT, false),
            key("workflow.plan.phase-6-finalize.project_steps.*", OBJECT, false),
            key("workflow.plan.phase-6-finalize.project_steps.*.lane", LANE, false),
            key("workflow.plan.phase-6-finalize.project_steps.*.order",
                    new ConfigValueDomain.IntegerRange(3, 989), false),
            key("workflow.plan.phase-6-finalize.project_steps.*.project_script_id", TEXT, false),
            key("workflow.plan.phase-6-finalize.steps", OBJECT, false),
            key("workflow.plan.phase-6-finalize.steps.*", OBJECT, false),
            key("workflow.plan.phase-6-finalize.steps.*.lane", LANE, false),
            key("workflow.plan.phase-6-finalize.steps.*.params", OBJECT, false),

            // local-config.json: the operator file of one project, its own members and the members of $defs.settings.
            key("local-config.integrations", OBJECT, false),
            key("local-config.integrations.git", OBJECT, false),
            key("local-config.integrations.git.variant", INTEGRATION_VARIANT, false),
            key("local-config.integrations.git.credential_key", TEXT, false),
            key("local-config.integrations.github", OBJECT, false),
            key("local-config.integrations.github.variant", INTEGRATION_VARIANT, false),
            key("local-config.integrations.github.credential_key", TEXT, false),
            key("local-config.integrations.gitlab", OBJECT, false),
            key("local-config.integrations.gitlab.variant", INTEGRATION_VARIANT, false),
            key("local-config.integrations.gitlab.credential_key", TEXT, false),
            key("local-config.integrations.forgejo", OBJECT, false),
            key("local-config.integrations.forgejo.credential_key", TEXT, false),
            key("local-config.integrations.sonar", OBJECT, false),
            key("local-config.integrations.sonar.credential_key", TEXT, false),
            key("local-config.integrations.provenance", OBJECT, false),
            key("local-config.integrations.provenance.url", TEXT, false),
            key("local-config.integrations.provenance.credential_key", TEXT, false),
            key("local-config.integrations.provenance.consented_at", TEXT, false),
            key("local-config.language", OBJECT, false),
            key("local-config.language.model", TEXT, false),
            key("local-config.diagrams", OBJECT, false),
            key("local-config.diagrams.format", new ConfigValueDomain.Enumeration(List.of("text", "mermaid")), false),
            key("local-config.plan_defaults", OBJECT, false),
            key("local-config.plan_defaults.method",
                    new ConfigValueDomain.Enumeration(List.of("method.test-first", "method.tests-after")), false),
            key("local-config.plan_defaults.asking", new ConfigValueDomain.Enumeration(
                    List.of("ask.every-phase", "ask.key-points", "ask.questions-only")), false),
            key("local-config.plan_defaults.posture",
                    new ConfigValueDomain.Enumeration(List.of("minimal", "standard", "full")), false),
            key("local-config.cleanup", OBJECT, false),
            key("local-config.cleanup.plans_after_days", CLEANUP_DURATION, true),
            key("local-config.cleanup.epics_after_days", CLEANUP_DURATION, true),
            key("local-config.cleanup.logs_after_days", CLEANUP_DURATION, true),
            key("local-config.upstream_mcp", OBJECT, false),
            key("local-config.upstream_mcp.servers", OBJECT, false),
            key("local-config.upstream_mcp.servers.*", OBJECT, false),
            key("local-config.upstream_mcp.servers.*.url", TEXT, false),
            key("local-config.upstream_mcp.servers.*.credential_key", TEXT, false),
            key("local-config.upstream_mcp.servers.*.tools", new ConfigValueDomain.ListOf(TEXT), false),
            key("local-config.upstream_mcp.servers.*.connect_timeout_seconds",
                    new ConfigValueDomain.IntegerRange(1, null), true),
            key("local-config.upstream_mcp.servers.*.read_timeout_seconds",
                    new ConfigValueDomain.IntegerRange(1, null), true),
            key("local-config.commit_identity", OBJECT, false),
            key("local-config.commit_identity.name", TEXT, false),
            key("local-config.commit_identity.email", TEXT, false),
            key("local-config.epic", OBJECT, false),
            key("local-config.epic.auto_start", BOOL, true),
            key("local-config.epic.overlap_max_files", new ConfigValueDomain.IntegerRange(0, 20), true),
            key("local-config.epic.parallelization_scope", new ConfigValueDomain.IntegerRange(1, null), true),
            key("local-config.qualification", OBJECT, false),
            key("local-config.qualification.agreement_level", new ConfigValueDomain.IntegerRange(0, 100), true),
            key("local-config.qualification.allowed_gap", new ConfigValueDomain.IntegerRange(0, 100), true),
            key("local-config.qualification.skip_roles", new ConfigValueDomain.ListOf(TEXT), true),
            key("local-config.qualification.skip_decisions", BOOL, true),
            key("local-config.commit_trailer", OBJECT, true),
            key("local-config.commit_trailer.name", TEXT, false),
            key("local-config.commit_trailer.email", TEXT, false),
            key("local-config.envelope_budget_tokens", new ConfigValueDomain.IntegerRange(10000, null), true),
            key("local-config.derivation_resolvers", OBJECT, false),
            key("local-config.derivation_resolvers.*", OBJECT, false),
            key("local-config.derivation_resolvers.*.enabled", BOOL, false),
            key("local-config.retention", OBJECT, false),
            key("local-config.retention.task_records_days", new ConfigValueDomain.IntegerRange(0, null), true),
            key("local-config.retention.retired_lesson_bodies_days", new ConfigValueDomain.IntegerRange(0, null), true),
            key("local-config.retention.arch_constraint_quiet_days", new ConfigValueDomain.IntegerRange(0, null), true),
            key("local-config.retention.workspace_rows_days", new ConfigValueDomain.IntegerRange(0, null), true));

    /**
     * @return every key of the table, in stem and schema order, immutable
     */
    public static List<EffectiveConfigKey> all() {
        return TABLE;
    }

    /**
     * @return the stems a configuration key names, immutable
     */
    public static Set<String> stems() {
        return EffectiveConfigKey.STEMS;
    }

    /**
     * Finds the key of an effective-key form: the exact key, or the key whose wildcard segment the form's segment stands
     * for. A form that is only a prefix of a key has no key, and a wildcard stands for exactly one segment.
     *
     * @param key an effective-key form
     * @return the key, or empty when the table holds none for the form
     */
    public static Optional<EffectiveConfigKey> find(String key) {
        var requested = Objects.requireNonNull(key, "key");
        for (var candidate : TABLE) {
            if (candidate.key().equals(requested)) {
                return Optional.of(candidate);
            }
        }
        for (var candidate : TABLE) {
            if (wildcardMatches(candidate.key(), requested)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Tells whether a registered key names the requested form through its wildcard segments: same length, every literal
     * segment equal, and every wildcard standing for exactly one non-empty segment that is not itself a wildcard.
     */
    private static boolean wildcardMatches(String registered, String requested) {
        var expected = registered.split("\\.", -1);
        var actual = requested.split("\\.", -1);
        if (expected.length != actual.length) {
            return false;
        }
        for (var index = 0; index < expected.length; index++) {
            if ("*".equals(expected[index])) {
                if (actual[index].isEmpty() || "*".equals(actual[index])) {
                    return false;
                }
            } else if (!expected[index].equals(actual[index])) {
                return false;
            }
        }
        return true;
    }

    private static EffectiveConfigKey key(String key, ConfigValueDomain domain, boolean hasCompiledDefault) {
        return new EffectiveConfigKey(key, domain, hasCompiledDefault);
    }
}
