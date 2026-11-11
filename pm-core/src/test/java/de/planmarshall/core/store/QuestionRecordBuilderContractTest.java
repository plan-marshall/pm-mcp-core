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

import de.planmarshall.core.store.QuestionRecord.Answer;
import de.planmarshall.core.store.QuestionRecord.AnswerKind;
import de.planmarshall.core.store.QuestionRecord.Blocks;
import de.planmarshall.core.store.QuestionRecord.Decision;
import de.planmarshall.core.store.QuestionRecord.Evidence;
import de.planmarshall.core.store.QuestionRecord.EvidenceKind;
import de.planmarshall.core.store.QuestionRecord.Interpretation;
import de.planmarshall.core.store.QuestionRecord.InterpretationResult;
import de.planmarshall.core.store.QuestionRecord.Origin;
import de.planmarshall.core.store.QuestionRecord.QuestionClass;
import de.planmarshall.core.store.QuestionRecord.Reversibility;

@DisplayName("Question record builder contract")
class QuestionRecordBuilderContractTest {

    private static final Instant AT = Instant.parse("2026-10-22T10:00:00Z");

    private static Decision decision() {
        return new Decision("Review the outline", "Outline review", "",
                List.of(new Evidence(EvidenceKind.FILE, "docs/outline.adoc", null)), Blocks.PLAN,
                Reversibility.REVERSIBLE);
    }

    @Test
    @DisplayName("the builder builds the constructor's question record")
    void builderBuildsQuestionRecord() {
        var canonical = new QuestionRecord(QuestionEvent.ASKED, "Q-001", QuestionClass.REVIEW, Origin.SERVER,
                "setup.entry", null, null, "Q-001", null, 1, decision(), "{}", null, null, null, null, null,
                null, AT);

        var built = QuestionRecord.builder().event(canonical.event()).questionId(canonical.questionId())
                .questionClass(canonical.questionClass()).origin(canonical.origin())
                .sourceEntity(canonical.sourceEntity()).askingRole(canonical.askingRole())
                .phase(canonical.phase()).questionSet(canonical.questionSet())
                .planTaskId(canonical.planTaskId()).revision(canonical.revision())
                .decision(canonical.decision()).envelope(canonical.envelope())
                .decisionId(canonical.decisionId()).answer(canonical.answer()).channel(canonical.channel())
                .deviceId(canonical.deviceId()).amendmentId(canonical.amendmentId())
                .reason(canonical.reason()).at(canonical.at()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new QuestionRecord(null, canonical.questionId(), canonical.questionClass(),
                        canonical.origin(), canonical.sourceEntity(), canonical.askingRole(),
                        canonical.phase(), canonical.questionSet(), canonical.planTaskId(),
                        canonical.revision(), canonical.decision(), canonical.envelope(),
                        canonical.decisionId(), canonical.answer(), canonical.channel(),
                        canonical.deviceId(), canonical.amendmentId(), canonical.reason(), canonical.at()),
                "the constructor refuses an absent event");
        var incomplete = QuestionRecord.builder().questionId(canonical.questionId())
                .questionClass(canonical.questionClass()).origin(canonical.origin())
                .sourceEntity(canonical.sourceEntity()).askingRole(canonical.askingRole())
                .phase(canonical.phase()).questionSet(canonical.questionSet())
                .planTaskId(canonical.planTaskId()).revision(canonical.revision())
                .decision(canonical.decision()).envelope(canonical.envelope())
                .decisionId(canonical.decisionId()).answer(canonical.answer())
                .channel(canonical.channel()).deviceId(canonical.deviceId())
                .amendmentId(canonical.amendmentId()).reason(canonical.reason())
                .at(canonical.at());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent event");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent event with the same message"));
    }

    @Test
    @DisplayName("the builder builds the constructor's decision")
    void builderBuildsDecision() {
        var canonical = decision();

        var built = Decision.builder().title(canonical.title()).header(canonical.header())
                .description(canonical.description()).evidence(canonical.evidence())
                .blocks(canonical.blocks()).reversibility(canonical.reversibility()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Decision(null, canonical.header(), canonical.description(), canonical.evidence(),
                        canonical.blocks(), canonical.reversibility()),
                "the constructor refuses an absent title");
        var incomplete = Decision.builder().header(canonical.header())
                .description(canonical.description()).evidence(canonical.evidence())
                .blocks(canonical.blocks()).reversibility(canonical.reversibility());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent title");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent title with the same message"));
    }

    @Test
    @DisplayName("the builder builds the constructor's interpretation")
    void builderBuildsInterpretation() {
        var canonical = new Interpretation(InterpretationResult.OPTION, null,
                List.of("the operator chose the first option"), "qt-0123456789abcdef");

        var built = Interpretation.builder().result(canonical.result()).condition(canonical.condition())
                .passages(canonical.passages()).queueTaskId(canonical.queueTaskId()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Interpretation(null, canonical.condition(), canonical.passages(),
                        canonical.queueTaskId()),
                "the constructor refuses an absent result");
        var incomplete = Interpretation.builder().condition(canonical.condition())
                .passages(canonical.passages()).queueTaskId(canonical.queueTaskId());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent result");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent result with the same message"));
    }

    @Test
    @DisplayName("the builder builds the constructor's answer")
    void builderBuildsAnswer() {
        var canonical = new Answer(AnswerKind.CHOSEN, List.of("keep"), null, null, null, null, false);

        var built = Answer.builder().kind(canonical.kind()).branchKeys(canonical.branchKeys())
                .value(canonical.value()).ownWords(canonical.ownWords()).note(canonical.note())
                .interpretation(canonical.interpretation()).afterChat(canonical.afterChat()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Answer(null, canonical.branchKeys(), canonical.value(), canonical.ownWords(),
                        canonical.note(), canonical.interpretation(), canonical.afterChat()),
                "the constructor refuses an absent kind");
        var incomplete = Answer.builder().branchKeys(canonical.branchKeys()).value(canonical.value())
                .ownWords(canonical.ownWords()).note(canonical.note())
                .interpretation(canonical.interpretation()).afterChat(canonical.afterChat());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent kind");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent kind with the same message"));
    }
}
