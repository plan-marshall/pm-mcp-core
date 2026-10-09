/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import de.planmarshall.core.spi.PrimitiveRegistrationException.Reason;

/**
 * The primitives of one assembly, by id (PM-IMPL-5).
 * <p>
 * The registry is filled through its constructor and by nothing else: whoever assembles the server passes the
 * primitives, and a test passes the ones it needs. It looks nothing up on the classpath.
 * <p>
 * The constructor checks every primitive before the registry exists, so a registry never holds a primitive it
 * would have refused, and one malformed primitive leaves no registry at all. What it checks is what the
 * declarations of a primitive decide on their own:
 * <ul>
 * <li>its name, which no other primitive of the registry has;</li>
 * <li>its parameter type, a record class whose free parameters are texts with bounds that can hold;</li>
 * <li>its outcome type, a closed enum whose outcomes each have a class and a wire name of their own that is not
 * reserved for the engine;</li>
 * <li>its job: it enqueues one exactly when one of its outcomes starts one;</li>
 * <li>its cycle measure, which it declares, and its exhaustion outcome: a primitive with an attempt cap names one
 * of its outcomes of class {@link OutcomeClass#FAILURE}, and every other primitive names none;</li>
 * <li>its awaited event, which it declares.</li>
 * </ul>
 *
 * @since 0.1
 */
public final class PrimitiveRegistry {

    /** The wire name of the control outcome with which the engine leaves a sub-workflow. */
    private static final String RESERVED_WIRE_NAME = "stepped_out";

    private static final String IDENTIFIER = "[a-z][a-z0-9_-]{0,31}";

    private static final Pattern WIRE_NAME = Pattern.compile(IDENTIFIER);

    private static final Pattern PRIMITIVE_ID = Pattern.compile(IDENTIFIER + "\\." + IDENTIFIER);

    private final Map<String, Primitive<?, ?>> primitivesById;

    /**
     * @param primitives the primitives of the assembly
     * @throws PrimitiveRegistrationException if a primitive is malformed or two primitives have the same id; the
     *         exception names the first primitive refused, in the order of the argument
     * @throws NullPointerException if the collection or one of its elements is {@code null}
     */
    public PrimitiveRegistry(Collection<? extends Primitive<?, ?>> primitives) {
        Objects.requireNonNull(primitives, "primitives");
        var accepted = new LinkedHashMap<String, Primitive<?, ?>>();
        for (Primitive<?, ?> primitive : primitives) {
            Objects.requireNonNull(primitive, "primitive");
            var id = checkedId(primitive);
            if (accepted.putIfAbsent(id, primitive) != null) {
                throw new PrimitiveRegistrationException(id, Reason.DUPLICATE_ID, "is registered twice");
            }
            checkParameterType(id, primitive);
            var outcomes = checkedOutcomes(id, primitive);
            checkJobSideEffect(id, primitive, outcomes);
            checkCycle(id, primitive, outcomes);
            if (primitive.awaitedEvent() == null) {
                throw new PrimitiveRegistrationException(id, Reason.MISSING_AWAITED_EVENT,
                        "declares no awaited event");
            }
        }
        primitivesById = Collections.unmodifiableMap(accepted);
    }

    /**
     * @param id the id of a primitive
     * @return the primitive with the id; empty when the registry holds none
     */
    public Optional<Primitive<?, ?>> find(String id) {
        return Optional.ofNullable(primitivesById.get(id));
    }

    /**
     * @return the ids of the registered primitives, in the order they were passed; unmodifiable
     */
    public Set<String> ids() {
        return primitivesById.keySet();
    }

    private static String checkedId(Primitive<?, ?> primitive) {
        var id = primitive.id();
        if (id == null || !PRIMITIVE_ID.matcher(id).matches()) {
            throw new PrimitiveRegistrationException(id, Reason.MALFORMED_ID,
                    "has an id that is not two lower-case identifiers joined by a dot");
        }
        return id;
    }

    private static void checkParameterType(String id, Primitive<?, ?> primitive) {
        Class<?> type = primitive.parameterType();
        if (type == null || !type.isRecord()) {
            throw new PrimitiveRegistrationException(id, Reason.PARAMETER_TYPE_NOT_A_RECORD,
                    "declares the parameter type %s, which is not a record class".formatted(nameOf(type)));
        }
        for (RecordComponent component : type.getRecordComponents()) {
            var freeParam = component.getAnnotation(FreeParam.class);
            if (freeParam != null) {
                checkFreeParameter(id, component, freeParam);
            }
        }
    }

    private static void checkFreeParameter(String id, RecordComponent component, FreeParam freeParam) {
        if (component.getType() != String.class) {
            throw malformedFreeParameter(id, component,
                    "is of the type %s and not a text".formatted(component.getType().getName()));
        }
        if (freeParam.minLength() < 0) {
            throw malformedFreeParameter(id, component,
                    "has the negative least length %d".formatted(freeParam.minLength()));
        }
        if (freeParam.maxLength() < 0) {
            throw malformedFreeParameter(id, component,
                    "has the negative greatest length %d".formatted(freeParam.maxLength()));
        }
        if (freeParam.minLength() > freeParam.maxLength()) {
            throw malformedFreeParameter(id, component, "has the least length %d above the greatest length %d"
                    .formatted(freeParam.minLength(), freeParam.maxLength()));
        }
    }

    private static PrimitiveRegistrationException malformedFreeParameter(String id, RecordComponent component,
            String detail) {
        return new PrimitiveRegistrationException(id, Reason.MALFORMED_FREE_PARAMETER,
                "declares the free parameter '%s', which %s".formatted(component.getName(), detail));
    }

    private static List<PrimitiveOutcome> checkedOutcomes(String id, Primitive<?, ?> primitive) {
        Class<?> type = primitive.outcomeType();
        if (type == null || !type.isEnum() || !PrimitiveOutcome.class.isAssignableFrom(type)) {
            throw new PrimitiveRegistrationException(id, Reason.OPEN_OUTCOME_SET,
                    "declares the outcome type %s, which is not an enum of outcomes".formatted(nameOf(type)));
        }
        var outcomes = Stream.of(type.getEnumConstants()).map(PrimitiveOutcome.class::cast).toList();
        if (outcomes.isEmpty()) {
            throw new PrimitiveRegistrationException(id, Reason.OPEN_OUTCOME_SET,
                    "declares the outcome type %s, which has no constant".formatted(type.getName()));
        }
        var seen = new LinkedHashMap<String, PrimitiveOutcome>();
        for (PrimitiveOutcome outcome : outcomes) {
            var wireName = outcome.wireName();
            if (wireName == null || !WIRE_NAME.matcher(wireName).matches()) {
                throw new PrimitiveRegistrationException(id, Reason.MALFORMED_WIRE_NAME,
                        "has the outcome %s with the wire name '%s', which is not a lower-case identifier"
                                .formatted(outcome, wireName));
            }
            if (RESERVED_WIRE_NAME.equals(wireName)) {
                throw new PrimitiveRegistrationException(id, Reason.RESERVED_WIRE_NAME,
                        "has the outcome %s with the wire name '%s', which only the engine returns"
                                .formatted(outcome, wireName));
            }
            var first = seen.putIfAbsent(wireName, outcome);
            if (first != null) {
                throw new PrimitiveRegistrationException(id, Reason.DUPLICATE_WIRE_NAME,
                        "has the outcomes %s and %s with the same wire name '%s'".formatted(first, outcome, wireName));
            }
            if (outcome.outcomeClass() == null) {
                throw new PrimitiveRegistrationException(id, Reason.MISSING_OUTCOME_CLASS,
                        "has the outcome %s without an outcome class".formatted(outcome));
            }
        }
        return outcomes;
    }

    private static void checkJobSideEffect(String id, Primitive<?, ?> primitive, List<PrimitiveOutcome> outcomes) {
        var jobStarting = outcomes.stream().filter(PrimitiveOutcome::startsJob).findFirst();
        if (primitive.enqueuesJob() && jobStarting.isEmpty()) {
            throw new PrimitiveRegistrationException(id, Reason.UNDECLARED_JOB_SIDE_EFFECT,
                    "enqueues a job but has no job-starting outcome");
        }
        if (!primitive.enqueuesJob() && jobStarting.isPresent()) {
            throw new PrimitiveRegistrationException(id, Reason.UNDECLARED_JOB_SIDE_EFFECT,
                    "has the job-starting outcome %s but does not declare that it enqueues a job"
                            .formatted(jobStarting.get()));
        }
    }

    private static void checkCycle(String id, Primitive<?, ?> primitive, List<PrimitiveOutcome> outcomes) {
        var measure = primitive.cycleMeasure();
        if (measure == null) {
            throw new PrimitiveRegistrationException(id, Reason.MISSING_CYCLE_MEASURE, "declares no cycle measure");
        }
        var exhaustions = declaredExhaustions(primitive);
        if (measure != CycleMeasure.ATTEMPT_CAP) {
            if (!exhaustions.isEmpty()) {
                throw new PrimitiveRegistrationException(id, Reason.EXHAUSTION_OUTCOME_WITHOUT_ATTEMPT_CAP,
                        "declares the exhaustion outcome '%s' but the cycle measure %s, which no cap exhausts"
                                .formatted(exhaustions.getFirst(), measure));
            }
            return;
        }
        var exhausts = outcomes.stream().anyMatch(outcome -> exhaustions.contains(outcome.wireName())
                && outcome.outcomeClass() == OutcomeClass.FAILURE);
        if (!exhausts) {
            throw new PrimitiveRegistrationException(id, Reason.ATTEMPT_CAP_WITHOUT_FAILURE_EXHAUSTION,
                    "declares an attempt cap but no exhaustion outcome among its outcomes of class FAILURE");
        }
    }

    /**
     * Reads the exhaustion outcome of a primitive as the wire names it declares, none or one. A primitive that
     * returns no {@link Optional} at all declares none, like one that returns an empty one, so the checks of the
     * cycle never hold an {@link Optional} that may be missing.
     */
    private static List<String> declaredExhaustions(Primitive<?, ?> primitive) {
        return Stream.ofNullable(primitive.exhaustionOutcome()).flatMap(Optional::stream).toList();
    }

    private static String nameOf(Class<?> type) {
        return type == null ? "null" : type.getName();
    }
}
