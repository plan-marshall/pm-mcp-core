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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.With;

import de.planmarshall.core.store.HarnessUsage.UnmeasuredReason;

/**
 * One worker-idle row of a {@code metrics.json} (Store Schemas § Metrics &amp; Quality Report,
 * {@code workerIdleRow}): the usage of one warm worker's job that served no task — its start and its idle wake-ups.
 * <p>
 * The row belongs to no plan and lies in the workspace scope's {@code metrics.json}, so it is reported apart from a
 * plan's figures; its population is {@code worker_idle}, which no other figure shares.
 *
 * @param jobId          the warm worker
 * @param role           the role of the worker
 * @param harness        the harness of the worker
 * @param harnessVersion the harness version probed at its spawn
 * @param model          the model passed to the harness
 * @param usage          the usage figures of the idle turns
 * @param idleWakeups    the wake-ups without a task over the worker's generation
 * @param recordedAt     the instant the row was recorded
 * @since 0.1
 */
@With
@Builder(toBuilder = true)
public record WorkerIdleRow(String jobId, String role, String harness, String harnessVersion, String model,
HarnessUsage usage, int idleWakeups, Instant recordedAt) {

    /** The kind of every row this record writes. */
    public static final String KIND = "worker_idle";

    /** The population of every row of this kind. */
    public static final String POPULATION = "worker_idle";

    private static final Pattern JOB_ID_PATTERN = Pattern.compile("l-[0-9a-f]{16}");
    private static final Pattern ROLE_PATTERN = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final Pattern HARNESS_PATTERN = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final int MAX_HARNESS_VERSION = 64;
    private static final int MAX_MODEL = 128;

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if an identifier breaks its grammar, a string is too long, or the counts are
     *                                  negative
     */
    public WorkerIdleRow {
        requirePattern(jobId, JOB_ID_PATTERN, "job_id");
        requirePattern(role, ROLE_PATTERN, "role");
        requirePattern(harness, HARNESS_PATTERN, "harness");
        Objects.requireNonNull(harnessVersion, "harness_version");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(recordedAt, "recorded_at");
        if (harnessVersion.length() > MAX_HARNESS_VERSION) {
            throw new IllegalArgumentException("'harness_version' is longer than " + MAX_HARNESS_VERSION);
        }
        if (model.isEmpty() || model.length() > MAX_MODEL) {
            throw new IllegalArgumentException("'model' is empty or longer than " + MAX_MODEL);
        }
        if (idleWakeups < 0) {
            throw new IllegalArgumentException("'idle_wakeups' must not be negative: " + idleWakeups);
        }
    }

    /** @return the total tokens of the row, empty where the store records {@code unmeasured} */
    public OptionalLong totalTokens() {
        return usage.totalTokens();
    }

    /** @return why a figure of the row is unmeasured, empty when every applicable figure is measured */
    public Optional<UnmeasuredReason> unmeasuredReason() {
        return usage.unmeasuredReason();
    }

    private static void requirePattern(String value, Pattern pattern, String name) {
        Objects.requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is not an identifier: %s".formatted(name, value));
        }
    }
}
