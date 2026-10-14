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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;
import de.planmarshall.workflow.plan.PlanInstanceHeader.Lane;
import de.planmarshall.workflow.plan.PlanInstanceHeader.Posture;
import de.planmarshall.workflow.unit.VerificationContext;
import de.planmarshall.workflow.verify.InvariantFixtures;
import de.planmarshall.workflow.verify.QuestionClassRegistry;
import de.planmarshall.workflow.verify.SchemaRegistry;

/**
 * The inputs of the composer tests: the plan template {@code plan/plan-template-fixture.pmwf}, written for these
 * tests and never copied from a unit of the product, the sub-units it calls and the registries of the verification,
 * which come from the fixtures of the invariant tests ({@link InvariantFixtures}), and a request per event over facts
 * that make no shipped rule hold.
 */
final class PlanFixtures {

    /** The classpath resource of the plan template. */
    static final String TEMPLATE_FILE = "plan/plan-template-fixture.pmwf";

    /** The plan of every request. */
    static final String PLAN_ID = "plan-fixture";

    /** The instant of every composition. */
    static final Instant COMPOSED_AT = Instant.parse("2026-10-14T08:00:00Z");

    /** The state of the template that calls the Q-Gate remediation unit. */
    static final String REMEDIATION_SITE = "finalize.remediate";

    /** The Phase 5 verification steps of the plan. */
    static final List<String> VERIFICATION_STEPS = List.of("verify:quality-gate", "verify:module-tests");

    /** The Phase 6 steps of the plan with their order. */
    static final List<CompositionRequest.Step> FINALIZE_STEPS = List.of(
            new CompositionRequest.Step("default:finalize-step-simplify", 5),
            new CompositionRequest.Step("default:pre-push-quality-gate", 10),
            new CompositionRequest.Step("default:push", 11),
            new CompositionRequest.Step("default:create-pr", 20),
            new CompositionRequest.Step("default:ci-verify", 22),
            new CompositionRequest.Step("default:automatic-review", 30),
            new CompositionRequest.Step("default:analysis-roundtrip", 40),
            new CompositionRequest.Step("default:branch-cleanup", 70),
            new CompositionRequest.Step("default:lessons-capture", 991),
            new CompositionRequest.Step("default:emit-landing", 1000),
            new CompositionRequest.Step("default:archive-plan", 1100));

    private PlanFixtures() {
    }

    /**
     * @param resource a classpath resource
     * @return its text
     */
    static String read(String resource) {
        try (var in = PlanFixtures.class.getClassLoader().getResourceAsStream(resource)) {
            Objects.requireNonNull(in, () -> "missing fixture " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param file   the file name, the resource name of every location of the tree
     * @param source the text; it must follow the grammar
     * @param origin the origin of the unit
     * @return the tree
     */
    static WorkflowDefinition parse(String file, String source, UnitOrigin origin) {
        var result = WorkflowParser.parse(file, source, origin);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> file + " was refused: " + result).definition();
    }

    /**
     * @return the plan template as the project owns it
     */
    static WorkflowDefinition template() {
        return parse(TEMPLATE_FILE, read(TEMPLATE_FILE), UnitOrigin.PROJECT);
    }

    /**
     * @return the sub-units the template calls, with their references
     */
    static List<CompositionRequest.SubUnit> subUnits() {
        return List.of(subUnit(InvariantFixtures.GIT_FILE), subUnit(InvariantFixtures.REMEDIATION_FILE));
    }

    private static CompositionRequest.SubUnit subUnit(String file) {
        var text = InvariantFixtures.read(file);
        var definition = parse(file, text, UnitOrigin.DEFAULT);
        return new CompositionRequest.SubUnit(new UnitReference(definition.id(), Optional.of(file),
                PlanFacts.sha256(text), UnitOrigin.DEFAULT), definition);
    }

    /**
     * @return the registries of the verification and the entity unit the remediation unit names
     */
    static VerificationContext context() {
        return new VerificationContext(InvariantFixtures.registry(), InvariantFixtures.entityKinds(),
                QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), List.of(InvariantFixtures.entityUnit()));
    }

    /**
     * @param rules the composition rules
     * @return a composer whose default units are the template and its sub-units, over the test roster
     */
    static PlanInstanceComposer composer(CompositionRules rules) {
        var defaults = new ArrayList<WorkflowDefinition>();
        defaults.add(parse(TEMPLATE_FILE, read(TEMPLATE_FILE), UnitOrigin.DEFAULT));
        subUnits().forEach(subUnit -> defaults.add(subUnit.definition()));
        return new PlanInstanceComposer(context(), defaults, InvariantFixtures.defaultRoster(), rules,
                Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
    }

    /**
     * @return a composer with the shipped composition rules
     */
    static PlanInstanceComposer composer() {
        return composer(((CompositionRules.Loaded) CompositionRules.shipped()).rules());
    }

    /**
     * @return facts that make no shipped rule hold: a deep plan of full posture with a push remote, an analysis
     *         provider and the merge policy on
     */
    static PlanFacts.Builder facts() {
        return PlanFacts.builder().lane(Lane.DEEP).posture(Posture.FULL).changeType("feature")
                .configuration("integrations.sonar", "sonarcloud").configuration("derived.push_remote", "origin")
                .configuration("merge.auto_merge", "true");
    }

    /**
     * @param event    the composition event
     * @param facts    the facts of the plan
     * @param previous the version in force
     * @param waivers  the waivers in force
     * @param position the position of the plan
     * @return the request over the template and its sub-units
     */
    static CompositionRequest request(CompositionEvent event, PlanFacts facts, Optional<PlanInstance> previous,
            List<Waiver> waivers, InstancePosition position) {
        return request(PLAN_ID, event, facts, previous, waivers, position);
    }

    /**
     * @param planId   the plan
     * @param event    the composition event
     * @param facts    the facts of the plan
     * @param previous the version in force
     * @param waivers  the waivers in force
     * @param position the position of the plan
     * @return the request over the template and its sub-units
     */
    static CompositionRequest request(String planId, CompositionEvent event, PlanFacts facts,
            Optional<PlanInstance> previous, List<Waiver> waivers, InstancePosition position) {
        var text = read(TEMPLATE_FILE);
        return new CompositionRequest(planId, event, template(), List.of(new UnitReference("plan",
                        Optional.of(TEMPLATE_FILE), PlanFacts.sha256(text), UnitOrigin.PROJECT)), subUnits(), facts,
                PlanFacts.sha256("effective configuration"), Optional.empty(), Optional.empty(), waivers, position,
                previous, VERIFICATION_STEPS, FINALIZE_STEPS);
    }

    /**
     * @param facts the facts of the plan
     * @return the request of version 1 at the start state
     */
    static CompositionRequest start(PlanFacts facts) {
        return request(CompositionEvent.START, facts, Optional.empty(), List.of(),
                InstancePosition.at("finalize.push-barrier"));
    }
}
