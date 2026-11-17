/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportResult;
import de.planmarshall.core.role.qualification.GradingConsent;
import de.planmarshall.core.role.qualification.GradingOffer;
import de.planmarshall.core.role.qualification.GradingOutcome;
import de.planmarshall.core.role.qualification.GradingPlan;
import de.planmarshall.core.role.qualification.GradingProfile;
import de.planmarshall.core.role.qualification.ItemCheckout;
import de.planmarshall.core.role.qualification.JudgeConfiguration;

/**
 * The runtime side of {@code pm-operator roles qualify} (Model Work § Grading): the service method delegates the offer
 * and the run to the assembly's grading engine, and it hands the engine this module's two grading ports.
 */
@DisplayName("Qualification service")
class QualificationServiceTest {

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
    }

    @Test
    @DisplayName("the service passes the offer and the granted consent to the engine")
    void delegatesToTheEngine() {
        var plan = plan();
        var offer = GradingOffer.of(plan);
        var outcome = new GradingOutcome.Declined(true);
        QualificationService.GradingEngine engine = createMock(QualificationService.GradingEngine.class);
        expect(engine.offer(plan)).andReturn(offer);
        expect(engine.grade(plan, GradingConsent.GRANTED)).andReturn(outcome);
        replay(engine);
        var service = new QualificationService(engine);

        var answeredOffer = service.offer(plan);
        var answeredOutcome = service.grade(plan, GradingConsent.GRANTED);

        assertAll(
                () -> assertSame(offer, answeredOffer, "the offer is answered as it is"),
                () -> assertSame(outcome, answeredOutcome, "the outcome is answered as it is"));
        verify(engine);
    }

    @Test
    @DisplayName("the two ports are the adapter's harness and judge scorer")
    void portsAreTheAdapters() {
        var role = fixture.role(RoleClass.READ_ONLY, RoleForm.FRESH, 1);
        var judge = fixture.role(RoleClass.ZERO_TOOL, RoleForm.FRESH, 1);
        ItemTaskOffers offers = createMock(ItemTaskOffers.class);
        replay(offers);

        var harness = QualificationService.harness(role, fixture.supervisor(), offers, ItemWorktrees.none(),
                fixture.workdir);
        var scorer = QualificationService.scorer(judge, fixture.supervisor(), offers);

        assertAll(
                () -> assertInstanceOf(WorkerGradingHarness.class, harness, "the harness is the grading adapter"),
                () -> assertInstanceOf(WorkerJudgeScorer.class, scorer, "the scorer is the judge adapter"));
    }

    private GradingPlan plan() {
        var role = fixture.role(RoleClass.READ_ONLY, RoleForm.FRESH, 1);
        return GradingPlan.builder()
                .role(role)
                .items(List.of())
                .importResult(new ImportResult(List.of(), List.of()))
                .corpusRevision("corpus-1")
                .projectRepository(URI.create("https://example.org/project"))
                .profile(GradingProfile.single())
                .judge(new JudgeConfiguration(role, Set.of(), List.of()))
                .checkouts(ItemCheckout.none())
                .build();
    }
}
