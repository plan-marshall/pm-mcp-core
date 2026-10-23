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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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
 * tests and never copied from a unit of the product, the sub-units it calls (the commit and the remediation unit from
 * the fixtures of the invariant tests, {@link InvariantFixtures}, and the three asking variants written here), the
 * registries of the verification, which come from the invariant fixtures as well, and a request per event over facts
 * that make no shipped rule hold.
 * <p>
 * The asking variants declare the shipped values of their schema: {@code ask.every-phase} waits after every phase but
 * the outline, {@code ask.key-points} and {@code ask.questions-only} after none ({@code wait_after = []});
 * {@code ask.questions-only} merges by itself ({@code self_merge = true}), the other two do not.
 * <p>
 * The cases of the lanes compose the merged plan unit of the invariant fixtures instead
 * ({@link #mergedComposer()}, {@link #mergedRequest}), whose phase files hold both lanes and their per-lane rework
 * routes.
 */
final class PlanFixtures {

    /** The classpath resource of the plan template. */
    static final String TEMPLATE_FILE = "plan/plan-template-fixture.pmwf";

    /** The classpath resource of the plan unit that models the per-lane phase calls and their rework. */
    static final String PER_LANE_FILE = "plan/per-lane-rework.pmwf";

    /** The identifiers of the phase units the per-lane plan unit calls. */
    private static final List<String> PER_LANE_PHASE_UNITS = List.of("phase-plan", "phase-execute");

    /** The call site of the asking axis in the template, which calls {@link #QUESTIONS_ONLY}. */
    static final String ASK_SITE = "phases.ask";

    /** The asking variant that waits after every phase but the outline and leaves the merge to the operator. */
    static final String EVERY_PHASE = "ask.every-phase";

    /** The asking variant that reviews the outline and leaves the merge to the operator. */
    static final String KEY_POINTS = "ask.key-points";

    /** The asking variant that reviews no outline and merges by itself, the shipped plan default. */
    static final String QUESTIONS_ONLY = "ask.questions-only";

    /** The text of each asking variant by its file name, in the order of {@link #subUnits()}. */
    private static final Map<String, String> VARIANT_TEXTS = variantTexts();

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
        return Stream.concat(Stream.of(InvariantFixtures.GIT_FILE, InvariantFixtures.REMEDIATION_FILE),
                VARIANT_TEXTS.keySet().stream()).map(file -> subUnit(file, subUnitText(file))).toList();
    }

    /**
     * @param file the file of a sub-unit of {@link #subUnits()} or of {@link #withAdjudication(CompositionRequest)}
     * @return its text
     */
    static String subUnitText(String file) {
        return VARIANT_TEXTS.containsKey(file) ? VARIANT_TEXTS.get(file) : InvariantFixtures.read(file);
    }

    /**
     * @param request a request of this class
     * @return the request whose sub-units also hold the adjudication unit {@code adjudicate.decisions} of the invariant
     *         fixtures, which no call site of the template calls
     */
    static CompositionRequest withAdjudication(CompositionRequest request) {
        var subUnits = new ArrayList<>(request.subUnits());
        subUnits.add(subUnit(InvariantFixtures.ADJUDICATION_FILE, subUnitText(InvariantFixtures.ADJUDICATION_FILE)));
        return new CompositionRequest(request.planId(), request.event(), request.template(), request.templates(),
                subUnits, request.facts(), request.configHash(), request.realizedFootprintDigest(),
                request.providersDigest(), request.waivers(), request.position(), request.previous(),
                request.verificationSteps(), request.finalizeSteps());
    }

    private static CompositionRequest.SubUnit subUnit(String file, String text) {
        var definition = parse(file, text, UnitOrigin.DEFAULT);
        return new CompositionRequest.SubUnit(new UnitReference(definition.id(), Optional.of(file),
                PlanFacts.sha256(text), UnitOrigin.DEFAULT), definition);
    }

    private static Map<String, String> variantTexts() {
        var texts = new LinkedHashMap<String, String>();
        texts.put(fileOf(EVERY_PHASE), variant(EVERY_PHASE, "[init, refine, plan, execute]", "true", "false"));
        texts.put(fileOf(KEY_POINTS), variant(KEY_POINTS, "[]", "true", "false"));
        texts.put(fileOf(QUESTIONS_ONLY), variant(QUESTIONS_ONLY, "[]", "false", "true"));
        return Collections.unmodifiableMap(texts);
    }

    private static String fileOf(String variant) {
        return variant.replace('.', '-') + ".pmwf";
    }

    /**
     * An asking variant written for these tests: one pass state and the {@code continue} terminal, declaring the
     * choices of the asking schema.
     */
    private static String variant(String id, String waitAfter, String outlineReview, String selfMerge) {
        var prefix = id.replace('.', '-');
        return """
                ---
                name: %1$s
                description: An asking variant written for the composer tests.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: continue
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow %1$s version 1 format 1
                scope subworkflow
                declare wait_after = %3$s
                declare outline_review = %4$s
                declare self_merge = %5$s
                entry main -> %2$s.pass

                state %2$s.pass
                    phase inherit
                    auto test.proceed
                        done -> %2$s.done

                state %2$s.done terminal continue success
                    phase inherit
                """.formatted(id, prefix, waitAfter, outlineReview, selfMerge);
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
     * @return a composer that evaluates no composition rule, for the cases about the binding of the axes alone
     */
    static PlanInstanceComposer composerWithoutRules() {
        return composer(((CompositionRules.Loaded) CompositionRules.parse(
                "{\"format_version\": 1, \"revision\": \"t0\", \"rules\": []}".getBytes(StandardCharsets.UTF_8)))
                .rules());
    }

    /**
     * @return facts that make no shipped rule hold: a deep plan of full posture with a push remote and an analysis
     *         provider, whose asking axis no level sets, so the shipped {@link #QUESTIONS_ONLY} merges by itself
     */
    static PlanFacts.Builder facts() {
        return PlanFacts.builder().lane(Lane.DEEP).posture(Posture.FULL).changeType("feature")
                .configuration("integrations.sonar", "sonarcloud").configuration("derived.push_remote", "origin");
    }

    /**
     * @param level   the level that sets the asking axis
     * @param variant the asking variant it sets
     * @return the facts of {@link #facts()} with the asking axis set at the level
     */
    static PlanFacts.Builder asking(PlanFacts.Level level, String variant) {
        return facts().axis(PlanFacts.Axis.ASKING, level, variant);
    }

    /**
     * @return the facts of {@link #facts()} with the asking axis set at all three levels: the plan's own value
     *         {@link #EVERY_PHASE}, the project's {@link #KEY_POINTS}, the machine's {@link #QUESTIONS_ONLY}
     */
    static PlanFacts.Builder askingAtEveryLevel() {
        return asking(PlanFacts.Level.PLAN, EVERY_PHASE).axis(PlanFacts.Axis.ASKING, PlanFacts.Level.PROJECT,
                KEY_POINTS).axis(PlanFacts.Axis.ASKING, PlanFacts.Level.MACHINE, QUESTIONS_ONLY);
    }

    /**
     * @param event the composition event
     * @return the facts of {@link #facts()} with the {@code post_run} fact the event composes: {@code returned} for a
     *         fallback, {@code empty_harvest} for an empty harvest, unset for every other event
     */
    static PlanFacts.Builder facts(CompositionEvent event) {
        return facts().postRun(postRunOf(event).orElse(null));
    }

    /**
     * @param event the composition event
     * @return the {@code post_run} fact a request of the event carries, empty for an event without one
     */
    static Optional<String> postRunOf(CompositionEvent event) {
        return switch (event) {
            case FALLBACK -> Optional.of(CompositionRequest.POST_RUN_RETURNED);
            case EMPTY_HARVEST -> Optional.of(CompositionRequest.POST_RUN_EMPTY_HARVEST);
            default -> Optional.empty();
        };
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

    /**
     * @return a composer with the shipped composition rules whose default units are the passing units of the invariant
     *         fixtures, the merged plan unit of their seven phase files with both lanes among them
     */
    static PlanInstanceComposer mergedComposer() {
        return new PlanInstanceComposer(context(), InvariantFixtures.passingUnits(UnitOrigin.DEFAULT).values(),
                InvariantFixtures.defaultRoster(), ((CompositionRules.Loaded) CompositionRules.shipped()).rules(),
                Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
    }

    /**
     * A request over the merged plan unit of the invariant fixtures, the project's units after the changes, and its
     * sub-units.
     *
     * @param event     the composition event
     * @param facts     the facts of the plan
     * @param previous  the version in force
     * @param position  the position of the plan
     * @param mutations the changes of the fixture files the project's units carry; empty for the passing units
     * @return the request; its templates are the seven phase files
     */
    static CompositionRequest mergedRequest(CompositionEvent event, PlanFacts facts, Optional<PlanInstance> previous,
            InstancePosition position, List<InvariantFixtures.Mutation> mutations) {
        var units = InvariantFixtures.mutatedUnits(UnitOrigin.PROJECT, mutations);
        var templates = InvariantFixtures.PLAN_FILES.stream().map(file -> new UnitReference("plan", Optional.of(file),
                PlanFacts.sha256(InvariantFixtures.read(file)), UnitOrigin.PROJECT)).toList();
        var subUnits = InvariantFixtures.SUB_UNIT_FILES.stream()
                .map(file -> subUnit(file, InvariantFixtures.read(file))).toList();
        return new CompositionRequest(PLAN_ID, event, units.get("plan"), templates, subUnits, facts,
                PlanFacts.sha256("effective configuration"), Optional.empty(), Optional.empty(), List.of(), position,
                previous, VERIFICATION_STEPS, FINALIZE_STEPS);
    }

    /**
     * @return a composer with the shipped composition rules whose default units are the per-lane plan unit and the
     *         phase units it calls
     */
    static PlanInstanceComposer perLaneComposer() {
        var defaults = new ArrayList<WorkflowDefinition>();
        defaults.add(parse(PER_LANE_FILE, read(PER_LANE_FILE), UnitOrigin.DEFAULT));
        phaseUnits().forEach(subUnit -> defaults.add(subUnit.definition()));
        return new PlanInstanceComposer(context(), defaults, InvariantFixtures.defaultRoster(),
                ((CompositionRules.Loaded) CompositionRules.shipped()).rules(), Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
    }

    /**
     * @param event    the composition event
     * @param facts    the facts of the plan
     * @param previous the version in force
     * @param position the position of the plan
     * @return the request over the per-lane plan unit, the project's unit, and the phase units it calls
     */
    static CompositionRequest perLaneRequest(CompositionEvent event, PlanFacts facts, Optional<PlanInstance> previous,
            InstancePosition position) {
        var text = read(PER_LANE_FILE);
        return new CompositionRequest(PLAN_ID, event, parse(PER_LANE_FILE, text, UnitOrigin.PROJECT),
                List.of(new UnitReference("plan", Optional.of(PER_LANE_FILE), PlanFacts.sha256(text),
                        UnitOrigin.PROJECT)), phaseUnits(), facts, PlanFacts.sha256("effective configuration"),
                Optional.empty(), Optional.empty(), List.of(), position, previous, VERIFICATION_STEPS,
                FINALIZE_STEPS);
    }

    /**
     * @return the phase units the per-lane plan unit calls, each with its reference
     */
    private static List<CompositionRequest.SubUnit> phaseUnits() {
        return PER_LANE_PHASE_UNITS.stream().map(id -> subUnit(id + ".pmwf", phaseUnit(id))).toList();
    }

    /**
     * A phase unit written for these tests: the exits a phase returns to the plan unit, so a call state of the
     * per-lane fixture routes them to its own lane.
     *
     * @param id the identifier of the phase unit
     * @return its text
     */
    private static String phaseUnit(String id) {
        var prefix = id.replace('.', '-');
        return """
                ---
                name: %1$s
                description: A phase call target written for the composer tests.
                type: subworkflow
                icon: workflow.default
                entry: begin
                exit: ready
                exit: fix_requested
                exit: loop_back
                exit: returned
                modification_date: 2026-10-21
                version: 0.1.0
                author: plan-marshall
                ---
                workflow %1$s version 1 format 1
                scope subworkflow
                entry begin -> %2$s.begin

                state %2$s.begin
                    phase inherit
                    auto test.gate
                        pass -> %2$s.ready
                        deep -> %2$s.deliver

                state %2$s.deliver
                    phase inherit
                    auto test.deliver
                        completed -> %2$s.fix
                        handed_back -> %2$s.loop
                        returned -> %2$s.returned

                state %2$s.ready terminal ready success
                    phase inherit

                state %2$s.fix terminal fix_requested success
                    phase inherit

                state %2$s.loop terminal loop_back success
                    phase inherit

                state %2$s.returned terminal returned failure
                    phase inherit
                """.formatted(id, prefix);
    }
}
