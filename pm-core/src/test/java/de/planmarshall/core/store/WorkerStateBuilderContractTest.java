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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.WorkerState.ContextTokens;
import de.planmarshall.core.store.WorkerState.Form;
import de.planmarshall.core.store.WorkerState.RoleClass;
import de.planmarshall.core.store.WorkerState.State;
import de.planmarshall.core.store.WorkerState.Workdir;

@DisplayName("Worker state builder contract")
class WorkerStateBuilderContractTest {

    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant AT = Instant.parse("2026-10-22T10:00:00Z");

    @Test
    @DisplayName("the builder builds the constructor's worker state")
    void builderBuildsWorkerState() {
        var canonical = new WorkerState("writer", DIGEST, RoleClass.WRITER, Form.FRESH, "opencode", "1.2.3",
                "claude", null, null, new JobId("l-0123456789abcdef"),
                new Workdir(Workdir.Kind.PROJECT_CHECKOUT, null), DIGEST, DIGEST, AT, State.WAITING, AT,
                null, List.of(), null, 0, ContextTokens.NOT_MEASURED, List.of(), null, false, null);

        var built = WorkerState.builder().role(canonical.role())
                .configurationDigest(canonical.configurationDigest())
                .roleClass(canonical.roleClass()).form(canonical.form()).harness(canonical.harness())
                .harnessVersion(canonical.harnessVersion()).model(canonical.model())
                .effort(canonical.effort()).effortNarrowedFrom(canonical.effortNarrowedFrom())
                .generationId(canonical.generationId()).workdir(canonical.workdir())
                .promptDigest(canonical.promptDigest()).jobTokenSha256(canonical.jobTokenSha256())
                .connectedAt(canonical.connectedAt()).state(canonical.state())
                .stateAt(canonical.stateAt()).task(canonical.task())
                .servedTasks(canonical.servedTasks()).worktreeMark(canonical.worktreeMark())
                .idleWakeups(canonical.idleWakeups()).contextTokens(canonical.contextTokens())
                .deliveredSkills(canonical.deliveredSkills()).endReason(canonical.endReason())
                .startFailure(canonical.startFailure()).harnessError(canonical.harnessError()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new WorkerState(null, canonical.configurationDigest(), canonical.roleClass(),
                        canonical.form(), canonical.harness(), canonical.harnessVersion(),
                        canonical.model(), canonical.effort(), canonical.effortNarrowedFrom(),
                        canonical.generationId(), canonical.workdir(), canonical.promptDigest(),
                        canonical.jobTokenSha256(), canonical.connectedAt(), canonical.state(),
                        canonical.stateAt(), canonical.task(), canonical.servedTasks(),
                        canonical.worktreeMark(), canonical.idleWakeups(), canonical.contextTokens(),
                        canonical.deliveredSkills(), canonical.endReason(), canonical.startFailure(),
                        canonical.harnessError()),
                "the constructor refuses an absent role");
        var incomplete = WorkerState.builder().configurationDigest(canonical.configurationDigest())
                .roleClass(canonical.roleClass()).form(canonical.form())
                .harness(canonical.harness()).harnessVersion(canonical.harnessVersion())
                .model(canonical.model()).effort(canonical.effort())
                .effortNarrowedFrom(canonical.effortNarrowedFrom())
                .generationId(canonical.generationId()).workdir(canonical.workdir())
                .promptDigest(canonical.promptDigest())
                .jobTokenSha256(canonical.jobTokenSha256())
                .connectedAt(canonical.connectedAt()).state(canonical.state())
                .stateAt(canonical.stateAt()).task(canonical.task())
                .servedTasks(canonical.servedTasks()).worktreeMark(canonical.worktreeMark())
                .idleWakeups(canonical.idleWakeups()).contextTokens(canonical.contextTokens())
                .deliveredSkills(canonical.deliveredSkills()).endReason(canonical.endReason())
                .startFailure(canonical.startFailure()).harnessError(canonical.harnessError());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent role");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent role with the same message"));
    }
}
