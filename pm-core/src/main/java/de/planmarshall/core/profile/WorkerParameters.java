/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * The worker and connection values of one loaded client profile, typed (PM-WORK-3, PM-CLIENT-2; Client Profiles, Worker
 * Block).
 * <p>
 * The worker block holds the per-host values of the task protocol and of supervision, and the connection block the
 * host's timing facts. The supervisor and the task protocol read them through this one projection, never by parsing the
 * profile document themselves; {@link #of(LoadedProfile)} takes the already-validated document and carries its declared
 * values over.
 * <p>
 * The projection refuses a value an eligible host requires but does not declare, so a {@code worker.eligible: true}
 * profile always yields the silence grace and the idle wake-ups detection and idle recycling read. Two kinds of absence
 * stay representable, because a compiled profile really has them: an acknowledgement deadline, which a host declares
 * only where it measured one ({@code claude} declares none, {@code opencode} declares {@code 270}), and every worker
 * value of an ineligible host. While {@link #eligible()} is {@code false}, no value other than {@code eligible} has an
 * effect (Client Profiles, Worker Block): a value the profile does not declare reads as {@code 0} or {@code false}, and
 * the supervisor reads none of them until the verification-record gate admitted the harness.
 *
 * @param eligible              whether the harness may host workers at all
 * @param ackDeadlineSeconds    the time from an offer to its acknowledgement after which the supervisor ends the
 *                              worker; empty where the host declares none
 * @param ackInformational      whether a missing acknowledgement ends no worker, so that silence alone decides
 * @param silenceGraceSeconds   the time beyond the bounded wait without a call after which the supervisor ends the
 *                              worker
 * @param idleWakeups           the consecutive wait answers without a task after which a warm worker is recycled
 * @param boundedWaitSeconds    the host's bounded wait window of one call
 * @param progressResetsTimeout whether a progress frame resets the host's idle timeout
 * @since 0.1
 */
public record WorkerParameters(boolean eligible, OptionalInt ackDeadlineSeconds, boolean ackInformational,
int silenceGraceSeconds, int idleWakeups, int boundedWaitSeconds, boolean progressResetsTimeout) {

    /** The suffix of a profile document, as a refusal names it. */
    private static final String PROFILE_SUFFIX = ".json";

    private static final String WORKER = "worker";
    private static final String CONNECTION = "connection";
    private static final String NAME_ELIGIBLE = "eligible";
    private static final String NAME_ACK_DEADLINE_SECONDS = "ack_deadline_seconds";
    private static final String NAME_ACK_INFORMATIONAL = "ack_informational";
    private static final String NAME_SILENCE_GRACE_SECONDS = "silence_grace_seconds";
    private static final String NAME_IDLE_WAKEUPS = "idle_wakeups";
    private static final String NAME_BOUNDED_WAIT_SECONDS = "bounded_wait_seconds";
    private static final String NAME_PROGRESS_RESETS_TIMEOUT = "progress_resets_timeout";

    /** Copies the optional deadline. */
    public WorkerParameters {
        Objects.requireNonNull(ackDeadlineSeconds, "ackDeadlineSeconds");
    }

    /**
     * @param profile a profile the loader read and validated
     * @return the worker parameters of that profile
     * @throws ProfileRefusal if the profile has no worker or connection block, or an eligible profile does not declare a
     *                        value the supervisor requires
     */
    public static WorkerParameters of(LoadedProfile profile) {
        Objects.requireNonNull(profile, "profile");
        var document = profile.document();
        var worker = document.member(WORKER)
                .orElseThrow(() -> refusal(profile, "/" + WORKER, "a profile names its worker block"));
        var connection = document.member(CONNECTION)
                .orElseThrow(() -> refusal(profile, "/" + CONNECTION, "a profile names its connection block"));
        var eligible = worker.member(NAME_ELIGIBLE).flatMap(JsonNode::booleanValue).orElse(false);
        return new WorkerParameters(eligible,
                optionalInteger(WORKER, worker, NAME_ACK_DEADLINE_SECONDS, profile),
                bool(WORKER, worker, NAME_ACK_INFORMATIONAL, profile, eligible),
                integer(WORKER, worker, NAME_SILENCE_GRACE_SECONDS, profile, eligible),
                integer(WORKER, worker, NAME_IDLE_WAKEUPS, profile, eligible),
                integer(CONNECTION, connection, NAME_BOUNDED_WAIT_SECONDS, profile, true),
                bool(CONNECTION, connection, NAME_PROGRESS_RESETS_TIMEOUT, profile, true));
    }

    /**
     * @return the silence limit in seconds: the connection's bounded wait plus the silence grace, so an operator
     *         override of the bounded wait moves both
     */
    public int silenceLimitSeconds() {
        return boundedWaitSeconds + silenceGraceSeconds;
    }

    private static OptionalInt optionalInteger(String blockName, JsonNode block, String name, LoadedProfile profile) {
        var value = block.member(name).orElse(JsonNode.nullValue());
        if (value.isNull()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.toIntExact(value.integerValue().orElseThrow(
                () -> refusal(profile, "/%s/%s".formatted(blockName, name), "the declared value is no integer"))));
    }

    private static int integer(String blockName, JsonNode block, String name, LoadedProfile profile, boolean required) {
        var value = optionalInteger(blockName, block, name, profile);
        if (value.isPresent()) {
            return value.getAsInt();
        }
        if (!required) {
            return 0;
        }
        throw refusal(profile, "/%s/%s".formatted(blockName, name),
                "an eligible worker host declares its '%s'".formatted(name));
    }

    private static boolean bool(String blockName, JsonNode block, String name, LoadedProfile profile,
            boolean required) {
        var value = block.member(name).orElse(JsonNode.nullValue());
        if (value.isNull()) {
            if (!required) {
                return false;
            }
            throw refusal(profile, "/%s/%s".formatted(blockName, name),
                    "an eligible worker host declares its '%s'".formatted(name));
        }
        return value.booleanValue().orElseThrow(
                () -> refusal(profile, "/%s/%s".formatted(blockName, name), "the declared value is no boolean"));
    }

    private static ProfileRefusal refusal(LoadedProfile profile, String pointer, String detail) {
        return ProfileRefusal.invalid(profile.clientId() + PROFILE_SUFFIX, pointer, detail);
    }
}
