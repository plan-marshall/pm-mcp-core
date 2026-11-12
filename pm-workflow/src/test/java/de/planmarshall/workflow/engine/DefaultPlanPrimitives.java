/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.CycleMeasure;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.workflow.verify.InvariantFixtures;

/**
 * The executable scripted primitives of the fixture corpus {@code unit-fixtures/defaults/}: one per primitive id that
 * {@link InvariantFixtures#registry()} declares, the evaluation primitives of the entity kinds among them, each the
 * twin of its declared primitive. A twin takes its outcome type, waivable gates, artifacts, job declaration, cycle
 * measure and exhaustion outcome from the declared primitive, so the same units verify against it; its parameter
 * record is the empty {@link EngineFixtures.NoParams}, which the binder of the engine tests binds.
 * <p>
 * Each twin returns the next outcome of the script a test set for it and its declared first outcome when the script
 * is empty, and records the scope of every run.
 * <p>
 * One awaited event differs from the declared twin: the gather of the Q-Gate remediation unit ({@value #GATHER}) is a
 * {@code composite_job}, and the engine resumes a resting composite job only by the event its primitive awaits; the
 * declared primitive awaits none, which leaves the gather without an event, so the twin awaits the passing of time.
 */
// Every datum in this class is a contract: the primitive ids and outcome wire names of the fixture corpus.
final class DefaultPlanPrimitives {

    /** The composite gather of the remediation unit, the one twin that awaits another event than its declaration. */
    static final String GATHER = "test.gather";

    private final Map<String, Scripted<?>> primitives = new LinkedHashMap<>();

    private final PrimitiveRegistry registry;

    DefaultPlanPrimitives() {
        var declared = InvariantFixtures.registry();
        for (var id : declared.ids()) {
            primitives.put(id, twin(declared.find(id).orElseThrow()));
        }
        registry = new PrimitiveRegistry(List.copyOf(primitives.values()));
    }

    private static <P extends Record, R extends Enum<R> & PrimitiveOutcome> Scripted<R> twin(
            Primitive<P, R> declared) {
        return new Scripted<>(declared);
    }

    /** @return the registry of the twins, which the engine and its validator share */
    PrimitiveRegistry registry() {
        return registry;
    }

    /**
     * @param primitiveId a primitive of the corpus
     * @param outcomes    the wire names of the outcomes of its next runs, in order
     * @return these primitives
     * @throws IllegalArgumentException if the primitive is not one of the corpus or an outcome is not one of its own
     */
    DefaultPlanPrimitives script(String primitiveId, String... outcomes) {
        var primitive = primitiveOf(primitiveId);
        Arrays.stream(outcomes).forEach(primitive::then);
        return this;
    }

    /**
     * Runs an evaluation primitive, as the return evaluation of a task state does.
     *
     * @param primitiveId the evaluation primitive of an entity kind
     * @param scopeId     the scope it runs for
     * @return the wire name of the outcome it returned
     */
    String evaluate(String primitiveId, Optional<String> scopeId) {
        return primitiveOf(primitiveId).execute(() -> scopeId, new EngineFixtures.NoParams()).outcome().wireName();
    }

    /**
     * @param primitiveId a primitive of the corpus
     * @return the scope of every run of the primitive, in order
     */
    List<Optional<String>> scopes(String primitiveId) {
        return List.copyOf(primitiveOf(primitiveId).scopes);
    }

    private Scripted<?> primitiveOf(String primitiveId) {
        var primitive = primitives.get(primitiveId);
        if (primitive == null) {
            throw new IllegalArgumentException("'%s' is no primitive of the corpus".formatted(primitiveId));
        }
        return primitive;
    }

    /**
     * The twin of one declared primitive: its declarations, a script of outcomes and the scopes it ran for.
     *
     * @param <R> the outcome enum of the declared primitive
     */
    static final class Scripted<R extends Enum<R> & PrimitiveOutcome> implements Primitive<EngineFixtures.NoParams, R> {

        private final Primitive<?, R> declared;
        private final Deque<R> script = new ArrayDeque<>();
        private final List<Optional<String>> scopes = new ArrayList<>();

        Scripted(Primitive<?, R> declared) {
            this.declared = Objects.requireNonNull(declared, "declared");
        }

        /** Adds the outcome of the given wire name to the script. */
        void then(String wireName) {
            script.add(Arrays.stream(outcomeType().getEnumConstants())
                    .filter(outcome -> outcome.wireName().equals(wireName)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("'%s' is no outcome of '%s'"
                            .formatted(wireName, id()))));
        }

        @Override
        public String id() {
            return declared.id();
        }

        @Override
        public Class<EngineFixtures.NoParams> parameterType() {
            return EngineFixtures.NoParams.class;
        }

        @Override
        public Class<R> outcomeType() {
            return declared.outcomeType();
        }

        @Override
        public PrimitiveResult<R> execute(WorkflowExecutionContext context, EngineFixtures.NoParams parameters) {
            scopes.add(context.scopeId());
            var next = script.poll();
            return new PrimitiveResult<>(next != null ? next : outcomeType().getEnumConstants()[0], Map.of(), null);
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return GATHER.equals(id()) ? AwaitedEvent.TIME : declared.awaitedEvent();
        }

        @Override
        public Set<String> producedArtifacts() {
            return declared.producedArtifacts();
        }

        @Override
        public Set<String> requiredArtifacts() {
            return declared.requiredArtifacts();
        }

        @Override
        public Set<String> waivableGates() {
            return declared.waivableGates();
        }

        @Override
        public boolean enqueuesJob() {
            return declared.enqueuesJob();
        }

        @Override
        public CycleMeasure cycleMeasure() {
            return declared.cycleMeasure();
        }

        @Override
        public Optional<String> exhaustionOutcome() {
            return declared.exhaustionOutcome();
        }
    }
}
