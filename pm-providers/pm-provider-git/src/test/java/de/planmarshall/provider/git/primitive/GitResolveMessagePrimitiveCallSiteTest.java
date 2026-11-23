/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.provider.git.primitive.GitResolveMessagePrimitive.CallSite;
import de.planmarshall.provider.git.primitive.GitResolveMessagePrimitive.MessageMode;
import de.planmarshall.provider.git.primitive.GitResolveMessagePrimitive.Params;

/**
 * {@code git.resolve-message}: the fixed message of every call site of {@code step_or_fallback}, its variants, a call
 * site without its input and the name of the state it carries.
 */
@EnableGeneratorController
@DisplayName("GitResolveMessagePrimitive: call sites of step_or_fallback")
class GitResolveMessagePrimitiveCallSiteTest extends GitResolveMessagePrimitiveFixtures {

    @ParameterizedTest(name = "{0}")
    @EnumSource(CallSite.class)
    @DisplayName("each call site resolves its fixed message from the bound inputs")
    void fixedMessage(CallSite callSite) {
        var stepId = stepIds.next();
        var epicId = ids.next();
        var unitId = ids.next();
        var prTitle = messages.next().lines().findFirst().orElseThrow();
        var body = titles.next();
        var baseBranch = ids.next();
        var parameters = Params.builder().messageMode(MessageMode.STEP_OR_FALLBACK).callSite(callSite)
                .stepId(stepId).epicId(epicId).unitId(unitId).prTitle(prTitle).body(body).baseBranch(baseBranch)
                .build();

        // The fixed messages are the literals of the specification (Stage 3).
        var expected = switch (callSite) {
            case FINALIZE_SETTLE_COMMIT -> "chore(finalize): apply %s changes".formatted(stepId);
            case FINALIZE_LAND_MAIN -> prTitle + "\n\n" + body;
            case EPIC_COMMIT_LEDGER -> "chore(orchestrator): update %s ledger".formatted(epicId);
            case DECOMPOSITION_COMMIT -> "chore(orchestrator): record %s decomposition".formatted(epicId);
            case LAND_REMOVAL -> "chore(land): remove documents moved into a source directory";
            case LAND_UNIT -> "chore(land): place %s".formatted(unitId);
            case LAND_SETUP -> "chore(land): apply project setup changes";
            case FINALIZE_HAND_BACK -> "chore(finalize): settle changes at hand-back";
            case ADR_RENUMBERING -> "chore(finalize): renumber ADRs against %s".formatted(baseBranch);
        };

        assertEquals(expected, readyMessage(run(parameters)), "The call site's fixed message is resolved");
    }

    @Test
    @DisplayName("the variants of a call site follow its bound flags and inputs")
    void variants() {
        var epicId = ids.next();
        var unitId = ids.next();
        var prTitle = messages.next().lines().findFirst().orElseThrow();
        var builder = Params.builder().messageMode(MessageMode.STEP_OR_FALLBACK);
        replay(site, context);

        var archive = readyMessage(primitive.execute(context,
                builder.callSite(CallSite.EPIC_COMMIT_LEDGER).epicId(epicId).archiving(true).build()));
        var remove = readyMessage(primitive.execute(context,
                builder.callSite(CallSite.LAND_UNIT).unitId(unitId).removing(true).build()));
        var subjectOnly = readyMessage(primitive.execute(context,
                builder.callSite(CallSite.FINALIZE_LAND_MAIN).prTitle(prTitle).build()));
        var settle = readyMessage(primitive.execute(context,
                builder.callSite(CallSite.FINALIZE_SETTLE_COMMIT).build()));
        verify(site, context);

        // The variant messages are the literals of the specification (Stage 3).
        assertAll("call-site variants",
                () -> assertEquals("chore(orchestrator): archive %s ledger".formatted(epicId), archive,
                        "An archiving epic archives its ledger"),
                () -> assertEquals("chore(land): remove %s".formatted(unitId), remove,
                        "A removed unit is landed as a removal"),
                () -> assertEquals(prTitle, subjectOnly, "Without a body the title is the whole message"),
                () -> assertEquals("chore(finalize): settle changes", settle,
                        "A trigger without a step settles changes"));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(CallSite.class)
    @DisplayName("without inputs a call site whose message names one goes to the task, any other is ready")
    void missingInput(CallSite callSite) {
        var parameters = Params.builder().messageMode(MessageMode.STEP_OR_FALLBACK).callSite(callSite).build();

        var result = run(parameters);

        messageWithoutInputs(callSite).ifPresentOrElse(
                expected -> assertEquals(expected, readyMessage(result),
                        "A call site without inputs keeps its message"),
                () -> assertNeedsInteractive(result));
    }

    /**
     * Whether the fixed message of a call site names an input: empty when it does, else the message it resolves to with
     * no input bound. The switch has no default branch, so a new call site breaks the build until its answer is given
     * here.
     */
    private static Optional<String> messageWithoutInputs(CallSite callSite) {
        // The fixed messages are the literals of the specification (Stage 3).
        return switch (callSite) {
            case FINALIZE_LAND_MAIN, EPIC_COMMIT_LEDGER, DECOMPOSITION_COMMIT, LAND_UNIT, ADR_RENUMBERING ->
                Optional.empty();
            case FINALIZE_SETTLE_COMMIT -> Optional.of("chore(finalize): settle changes");
            case LAND_REMOVAL -> Optional.of("chore(land): remove documents moved into a source directory");
            case LAND_SETUP -> Optional.of("chore(land): apply project setup changes");
            case FINALIZE_HAND_BACK -> Optional.of("chore(finalize): settle changes at hand-back");
        };
    }

    @Test
    @DisplayName("a bound message replaces the fixed message of the call site")
    void boundMessageWins() {
        var bound = messages.next();
        var parameters = Params.builder().messageMode(MessageMode.STEP_OR_FALLBACK).message(bound)
                .callSite(CallSite.LAND_SETUP).build();

        assertEquals(bound, readyMessage(run(parameters)), "The bound message replaces the fixed one");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(CallSite.class)
    @DisplayName("each call site carries the name of its state")
    void callSiteWireNames(CallSite callSite) {
        // The call-site names are the states of the matrix of the specification.
        var expected = switch (callSite) {
            case FINALIZE_SETTLE_COMMIT -> "finalize.settle-commit";
            case FINALIZE_LAND_MAIN -> "finalize.land-main";
            case EPIC_COMMIT_LEDGER -> "epic.commit-ledger";
            case DECOMPOSITION_COMMIT -> "decomposition.commit";
            case LAND_REMOVAL -> "git.land-removal";
            case LAND_UNIT -> "git.land-unit";
            case LAND_SETUP -> "git.land-setup";
            case FINALIZE_HAND_BACK -> "finalize.hand-back";
            case ADR_RENUMBERING -> "adr-renumbering";
        };

        assertEquals(expected, callSite.wireName(), "The call site carries the name of its state");
    }
}
