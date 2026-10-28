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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The plan's intent record, the model-supplied document the server accepts at {@code submit:refine}, the light
 * {@code submit:outline} and a re-planning that changes intent (Store Schemas § Intent Record; Phase Workflows § The
 * Intent Record).
 * <p>
 * This type is the {@code intent-record} named parameter schema: it refuses a document the JSON schema refuses, and it
 * checks the rules the specification puts beyond the schema — the EARS form of every criterion, the triple a
 * {@code fix} criterion carries, true deciders, the riskiest assumption, unique identifiers and the preservation of
 * every source claim. The identifiers are those of the Identifier Registry ({@code intent item identifier}): the
 * criterion {@code AC-<n>}, non-goal {@code NG-<n>}, constraint {@code CN-<n>}, decision {@code DC-<n>}, assumption
 * {@code AN-<n>} and deferred item {@code DF-<n>}, each stable across revisions.
 * <p>
 * The type validates one revision in isolation. The server checks that need the plan — a decision's question record,
 * a capability's fingerprint in the tree, the required claims of the epic row — are those of {@link IntentAcceptance}.
 *
 * @param why             the problem, who has it and why now, 20 to 1000 characters
 * @param goal            the outcome sentence a check can decide, 10 to 300 characters
 * @param criteria        one to 30 acceptance criteria
 * @param currentBehaviour the behaviour a {@code fix} criterion replaces; {@code null} when the record has no fix
 * @param nonGoals        one to 20 non-goals
 * @param constraints     up to 20 constraints, each ruling something out
 * @param decisions       up to 30 decisions
 * @param assumptions     up to 20 assumptions
 * @param openQuestions   up to 20 open questions
 * @param deferred        up to 20 deferred items
 * @param sourceClaims    one to 60 source claims, each landed or dropped with a reason
 * @since 0.1
 */
public record IntentRecord(String why, String goal, List<Criterion> criteria, String currentBehaviour,
List<NonGoal> nonGoals, List<Constraint> constraints, List<Decision> decisions, List<Assumption> assumptions,
List<OpenQuestion> openQuestions, List<Deferred> deferred, List<SourceClaim> sourceClaims) {

    /** The pattern of a criterion identifier. */
    public static final Pattern CRITERION_ID = Pattern.compile("AC-\\d{1,4}");

    /** The pattern of a non-goal identifier. */
    public static final Pattern NON_GOAL_ID = Pattern.compile("NG-\\d{1,4}");

    /** The pattern of a constraint identifier. */
    public static final Pattern CONSTRAINT_ID = Pattern.compile("CN-\\d{1,4}");

    /** The pattern of a decision identifier. */
    public static final Pattern DECISION_ID = Pattern.compile("DC-\\d{1,4}");

    /** The pattern of an assumption identifier. */
    public static final Pattern ASSUMPTION_ID = Pattern.compile("AN-\\d{1,4}");

    /** The pattern of a deferred-item identifier. */
    public static final Pattern DEFERRED_ID = Pattern.compile("DF-\\d{1,4}");

    /** The pattern of a requirement's base fingerprint. */
    public static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    private static final Set<String> DROPPED_OR_GOAL = Set.of("goal", "why", "dropped");

    private static final String THEN_THE = " THEN THE ";

    /**
     * Copies the lists into immutable lists and checks the record's rules.
     *
     * @throws IllegalArgumentException if a member is out of its bounds or a rule is broken
     * @throws NullPointerException     if a value is {@code null}
     */
    public IntentRecord {
        bounded(why, "why", 20, 1000);
        bounded(goal, "goal", 10, 300);
        criteria = copy(criteria, "criteria", 1, 30);
        currentBehaviour = optionalBounded(currentBehaviour, "current_behaviour", 10, 500);
        nonGoals = copy(nonGoals, "non_goals", 1, 20);
        constraints = copy(constraints, "constraints", 0, 20);
        decisions = copy(decisions, "decisions", 0, 30);
        assumptions = copy(assumptions, "assumptions", 0, 20);
        openQuestions = copy(openQuestions, "open_questions", 0, 20);
        deferred = copy(deferred, "deferred", 0, 20);
        sourceClaims = copy(sourceClaims, "source_claims", 1, 60);
        uniqueIdentifiers(criteria, nonGoals, constraints, decisions, assumptions, deferred);
        fixTriple(criteria, currentBehaviour);
        assumptionsRule(assumptions);
        preservation(sourceClaims, criteria, nonGoals, constraints, decisions, assumptions, deferred);
    }

    /**
     * @return the identifiers of every item of the record, in member order
     */
    public List<String> identifiers() {
        return List.copyOf(collectIds(criteria, nonGoals, constraints, decisions, assumptions, deferred));
    }

    /**
     * @param id an item identifier
     * @return the criterion of that identifier; empty when none has it
     */
    public Optional<Criterion> criterion(String id) {
        return criteria.stream().filter(item -> item.id().equals(id)).findFirst();
    }

    /**
     * @param id an item identifier
     * @return whether an item of the record carries the identifier
     */
    public boolean holds(String id) {
        return identifiers().contains(id);
    }

    /**
     * @param text the claim text
     * @return whether a source claim is the exact text
     */
    public boolean holdsClaim(String text) {
        return sourceClaims.stream().anyMatch(claim -> claim.claim().equals(text));
    }

    private static void uniqueIdentifiers(List<Criterion> criteria, List<NonGoal> nonGoals,
            List<Constraint> constraints, List<Decision> decisions, List<Assumption> assumptions,
            List<Deferred> deferred) {
        var seen = new HashSet<String>();
        var duplicates = new LinkedHashSet<String>();
        for (var id : collectIds(criteria, nonGoals, constraints, decisions, assumptions, deferred)) {
            if (!seen.add(id)) {
                duplicates.add(id);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("the identifiers %s are used more than once".formatted(duplicates));
        }
    }

    private static List<String> collectIds(List<Criterion> criteria, List<NonGoal> nonGoals,
            List<Constraint> constraints, List<Decision> decisions, List<Assumption> assumptions,
            List<Deferred> deferred) {
        var ids = new ArrayList<String>();
        criteria.forEach(item -> ids.add(item.id()));
        nonGoals.forEach(item -> ids.add(item.id()));
        constraints.forEach(item -> ids.add(item.id()));
        decisions.forEach(item -> ids.add(item.id()));
        assumptions.forEach(item -> ids.add(item.id()));
        deferred.forEach(item -> ids.add(item.id()));
        return ids;
    }

    private static void fixTriple(List<Criterion> criteria, String currentBehaviour) {
        var carriesFix = criteria.stream().anyMatch(item -> item.kind() == CriterionKind.FIX);
        if (!carriesFix) {
            if (currentBehaviour != null) {
                throw new IllegalArgumentException(
                        "a record without a fix criterion carries no current_behaviour");
            }
            return;
        }
        if (currentBehaviour == null) {
            throw new IllegalArgumentException("a record with a fix criterion names its current_behaviour");
        }
        if (!currentBehaviour.startsWith("WHEN ") || !currentBehaviour.contains(THEN_THE)
                || currentBehaviour.substring(currentBehaviour.indexOf(THEN_THE) + THEN_THE.length())
                .isBlank()) {
            throw new IllegalArgumentException(
                    "current_behaviour is written 'WHEN <condition> THEN THE <system> <behaviour>': "
                            + currentBehaviour);
        }
        if (criteria.stream().noneMatch(item -> item.kind() == CriterionKind.UNCHANGED)) {
            throw new IllegalArgumentException("a record with a fix criterion holds at least one unchanged criterion");
        }
    }

    private static void assumptionsRule(List<Assumption> assumptions) {
        var riskiest = assumptions.stream().filter(Assumption::riskiest).toList();
        if (riskiest.size() > 1) {
            throw new IllegalArgumentException("at most one assumption is marked riskiest");
        }
        if (!riskiest.isEmpty() && riskiest.getFirst().test() == null) {
            throw new IllegalArgumentException("the riskiest assumption carries its test");
        }
        var unverified = assumptions.stream().anyMatch(item -> item.status() == AssumptionStatus.UNVERIFIED);
        if (unverified && riskiest.isEmpty()) {
            throw new IllegalArgumentException(
                    "a record with an unverified assumption marks exactly one riskiest assumption");
        }
    }

    private static void preservation(List<SourceClaim> claims, List<Criterion> criteria, List<NonGoal> nonGoals,
            List<Constraint> constraints, List<Decision> decisions, List<Assumption> assumptions,
            List<Deferred> deferred) {
        var ids = new HashSet<>(collectIds(criteria, nonGoals, constraints, decisions, assumptions, deferred));
        for (var claim : claims) {
            if (!DROPPED_OR_GOAL.contains(claim.landedIn()) && !ids.contains(claim.landedIn())) {
                throw new IllegalArgumentException(
                        "source_claims entry '%s' lands in '%s', which is no item of the record"
                                .formatted(claim.claim(), claim.landedIn()));
            }
            if ("dropped".equals(claim.landedIn()) && claim.reason() == null) {
                throw new IllegalArgumentException(
                        "the dropped source claim '%s' carries its reason".formatted(claim.claim()));
            }
        }
    }

    private static String bounded(String value, String member, int min, int max) {
        Objects.requireNonNull(value, member);
        if (value.length() < min || value.length() > max) {
            throw new IllegalArgumentException(
                    "%s must be %d to %d characters: %d".formatted(member, min, max, value.length()));
        }
        return value;
    }

    private static String optionalBounded(String value, String member, int min, int max) {
        if (value == null) {
            return null;
        }
        return bounded(value, member, min, max);
    }

    private static <T> List<T> copy(List<T> values, String member, int min, int max) {
        Objects.requireNonNull(values, member);
        if (values.size() < min || values.size() > max) {
            throw new IllegalArgumentException(
                    "%s must hold %d to %d items: %d".formatted(member, min, max, values.size()));
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(member + " holds a null item");
        }
        return List.copyOf(values);
    }

    /**
     * The kinds of an acceptance criterion.
     *
     * @since 0.1
     */
    public enum CriterionKind {
        /** Behaviour the plan adds. */
        NEW("new"),
        /** Behaviour the plan fixes. */
        FIX("fix"),
        /** Behaviour the plan keeps. */
        UNCHANGED("unchanged");

        private final String wireName;

        CriterionKind(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<CriterionKind> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * How a criterion is verified.
     *
     * @since 0.1
     */
    public enum VerifiedBy {
        /** A test holds the criterion. */
        TEST("test"),
        /** A demonstration holds the criterion. */
        DEMONSTRATION("demonstration"),
        /** An inspection holds the criterion. */
        INSPECTION("inspection");

        private final String wireName;

        VerifiedBy(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<VerifiedBy> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * One acceptance criterion.
     *
     * @param id         the identifier {@code AC-<n>}
     * @param text       the statement in EARS form
     * @param kind       whether the plan adds, fixes or keeps the behaviour
     * @param verifiedBy how the criterion is verified
     * @param capability the capability requirement the criterion changes; {@code null} for the plan's own criterion
     * @since 0.1
     */
    public record Criterion(String id, String text, CriterionKind kind, VerifiedBy verifiedBy,
    CapabilityLink capability) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds or the text is not in EARS form
         * @throws NullPointerException     if a value is {@code null}
         */
        public Criterion {
            Objects.requireNonNull(id, "id");
            if (!CRITERION_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("a criterion id is AC-<n>: " + id);
            }
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(verifiedBy, "verifiedBy");
            var checked = bounded(text, "criteria[].text", 10, 500);
            EarsForm.violation(checked, kind == CriterionKind.UNCHANGED)
                    .ifPresent(reason -> {
                        throw new IllegalArgumentException("the criterion '%s' is not in EARS form: %s".formatted(id,
                                reason));
                    });
        }
    }

    /**
     * The capability requirement a criterion changes.
     *
     * @param capability the capability name
     * @param requirement the requirement name within the capability
     * @param change     whether the requirement is added, modified or removed
     * @param base       for modified and removed, the requirement's base fingerprint; {@code null} for added
     * @since 0.1
     */
    public record CapabilityLink(String capability, String requirement, CapabilityChange change, String base) {

        private static final Pattern NAME =
                Pattern.compile("[a-z0-9][a-z0-9-]{0,62}[a-z0-9](/[a-z0-9][a-z0-9-]{0,62}[a-z0-9]){0,3}");

        /**
         * @throws IllegalArgumentException if a name is malformed or the base does not go with the change
         * @throws NullPointerException     if a value is {@code null}
         */
        public CapabilityLink {
            Objects.requireNonNull(capability, "capability");
            Objects.requireNonNull(requirement, "requirement");
            Objects.requireNonNull(change, "change");
            if (!NAME.matcher(capability).matches()) {
                throw new IllegalArgumentException("not a capability name: " + capability);
            }
            if (requirement.length() < 3 || requirement.length() > 120) {
                throw new IllegalArgumentException("a requirement name is 3 to 120 characters: " + requirement);
            }
            if (change == CapabilityChange.ADDED && base != null) {
                throw new IllegalArgumentException("an added requirement carries no base fingerprint");
            }
            if (change != CapabilityChange.ADDED && (base == null || !FINGERPRINT.matcher(base).matches())) {
                throw new IllegalArgumentException(
                        "a %s requirement carries its 64-digit base fingerprint".formatted(change.wireName()));
            }
        }
    }

    /** The changes a criterion makes to a capability requirement. */
    public enum CapabilityChange {
        /** The requirement is new. */
        ADDED("added"),
        /** The requirement exists and changes. */
        MODIFIED("modified"),
        /** The requirement is removed. */
        REMOVED("removed");

        private final String wireName;

        CapabilityChange(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<CapabilityChange> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * One non-goal.
     *
     * @param id   the identifier {@code NG-<n>}
     * @param text what the plan deliberately does not do
     * @since 0.1
     */
    public record NonGoal(String id, String text) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public NonGoal {
            Objects.requireNonNull(id, "id");
            if (!NON_GOAL_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("a non-goal id is NG-<n>: " + id);
            }
            bounded(text, "non_goals[].text", 5, 500);
        }
    }

    /**
     * One constraint: something the plan must never do, however it meets its criteria.
     *
     * @param id       the identifier {@code CN-<n>}
     * @param text     what is ruled out
     * @param source   where the constraint comes from
     * @param evidence for {@code code}, the path or {@code pm://} URI that shows it; may be {@code null} otherwise
     * @since 0.1
     */
    public record Constraint(String id, String text, ConstraintSource source, String evidence) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds or a code constraint carries no evidence
         * @throws NullPointerException     if a value is {@code null}
         */
        public Constraint {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(source, "source");
            if (!CONSTRAINT_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("a constraint id is CN-<n>: " + id);
            }
            bounded(text, "constraints[].text", 5, 500);
            if (evidence != null && evidence.length() > 512) {
                throw new IllegalArgumentException("constraints[].evidence is at most 512 characters");
            }
            if (source == ConstraintSource.CODE && evidence == null) {
                throw new IllegalArgumentException("the code constraint '%s' names its evidence".formatted(id));
            }
        }
    }

    /** Where a constraint comes from. */
    public enum ConstraintSource {
        /** The codebase shows it. */
        CODE("code"),
        /** The operator stated it. */
        OPERATOR("operator"),
        /** An external source states it. */
        EXTERNAL("external");

        private final String wireName;

        ConstraintSource(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<ConstraintSource> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * One decision of the record.
     *
     * @param id             the identifier {@code DC-<n>}
     * @param text           the decision
     * @param optionsWeighed the options weighed, at most five
     * @param decider        who decided
     * @param questionId     for {@code operator} and {@code quorum}, the answered part; {@code null} otherwise
     * @param evidence       for {@code fact}, the path or {@code pm://} URI; {@code null} otherwise
     * @param adr            the architecture decision record the decision follows or contradicts; may be {@code null}
     * @since 0.1
     */
    public record Decision(String id, String text, List<String> optionsWeighed, Decider decider, String questionId,
    String evidence, AdrRef adr) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds, the decider's part is missing, or a
         *                                  contradicting decision is not the operator's
         * @throws NullPointerException     if a value is {@code null}
         */
        public Decision {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(decider, "decider");
            if (!DECISION_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("a decision id is DC-<n>: " + id);
            }
            bounded(text, "decisions[].text", 5, 500);
            optionsWeighed = copy(optionsWeighed, "decisions[].options_weighed", 0, 5);
            optionsWeighed.forEach(option -> bounded(option, "decisions[].options_weighed[]", 2, 200));
            if ((decider == Decider.OPERATOR || decider == Decider.QUORUM) && questionId == null) {
                throw new IllegalArgumentException(
                        "the %s decision '%s' names its question_id".formatted(decider.wireName(), id));
            }
            if (decider != Decider.OPERATOR && decider != Decider.QUORUM && questionId != null) {
                throw new IllegalArgumentException(
                        "the %s decision '%s' carries no question_id".formatted(decider.wireName(), id));
            }
            if (decider == Decider.FACT && evidence == null) {
                throw new IllegalArgumentException("the fact decision '%s' names its evidence".formatted(id));
            }
            if (decider != Decider.FACT && evidence != null) {
                throw new IllegalArgumentException(
                        "the %s decision '%s' carries no evidence".formatted(decider.wireName(), id));
            }
            if (evidence != null && evidence.length() > 512) {
                throw new IllegalArgumentException("decisions[].evidence is at most 512 characters");
            }
            if (adr != null && adr.relation() == AdrRelation.CONTRADICTS && decider != Decider.OPERATOR) {
                throw new IllegalArgumentException(
                        "the decision '%s' contradicts %s and is only the operator's".formatted(id, adr.adrId()));
            }
        }
    }

    /** Who decided a decision of the record. */
    public enum Decider {
        /** The operator answered a question part. */
        OPERATOR("operator"),
        /** A quorum answered a question part. */
        QUORUM("quorum"),
        /** The role took the call. */
        MODEL("model"),
        /** Exploring the tree found it. */
        FACT("fact");

        private final String wireName;

        Decider(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Decider> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The architecture decision record a decision follows or contradicts.
     *
     * @param adrId    the record's identifier
     * @param relation whether the decision follows or contradicts it
     * @since 0.1
     */
    public record AdrRef(String adrId, AdrRelation relation) {

        /**
         * @throws IllegalArgumentException if the identifier is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public AdrRef {
            Objects.requireNonNull(adrId, "adrId");
            Objects.requireNonNull(relation, "relation");
            if (adrId.isEmpty() || adrId.length() > 128) {
                throw new IllegalArgumentException("an ADR identifier is 1 to 128 characters: " + adrId);
            }
        }
    }

    /** How a decision relates to an architecture decision record. */
    public enum AdrRelation {
        /** The decision follows the record. */
        FOLLOWS("follows"),
        /** The decision departs from the record. */
        CONTRADICTS("contradicts");

        private final String wireName;

        AdrRelation(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<AdrRelation> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * One assumption.
     *
     * @param id       the identifier {@code AN-<n>}
     * @param text     what is assumed
     * @param status   whether the assumption is verified
     * @param riskiest whether this is the riskiest assumption of the record
     * @param test     for the riskiest assumption, the cheap test that would refute it
     * @since 0.1
     */
    public record Assumption(String id, String text, AssumptionStatus status, boolean riskiest, String test) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public Assumption {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
            if (!ASSUMPTION_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("an assumption id is AN-<n>: " + id);
            }
            bounded(text, "assumptions[].text", 5, 500);
            if (test != null && test.length() > 500) {
                throw new IllegalArgumentException("assumptions[].test is at most 500 characters");
            }
        }
    }

    /** Whether an assumption is verified. */
    public enum AssumptionStatus {
        /** A test or an exploration verified it. */
        VERIFIED("verified"),
        /** It rests on nothing yet. */
        UNVERIFIED("unverified");

        private final String wireName;

        AssumptionStatus(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<AssumptionStatus> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * A question the record leaves open.
     *
     * @param text     the question
     * @param resolver who can answer it: exploration or an external party
     * @since 0.1
     */
    public record OpenQuestion(String text, Resolver resolver) {

        /**
         * @throws IllegalArgumentException if the text is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public OpenQuestion {
            Objects.requireNonNull(resolver, "resolver");
            bounded(text, "open_questions[].text", 10, 500);
        }
    }

    /** Who resolves an open question. */
    public enum Resolver {
        /** Exploration of the repository resolves it. */
        EXPLORATION("exploration"),
        /** An external party resolves it. */
        EXTERNAL("external");

        private final String wireName;

        Resolver(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Resolver> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * One deferred item: work split off from this plan.
     *
     * @param id     the identifier {@code DF-<n>}
     * @param text   what is deferred
     * @param reason why it is not part of this plan
     * @since 0.1
     */
    public record Deferred(String id, String text, String reason) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public Deferred {
            Objects.requireNonNull(id, "id");
            if (!DEFERRED_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("a deferred-item id is DF-<n>: " + id);
            }
            bounded(text, "deferred[].text", 5, 500);
            bounded(reason, "deferred[].reason", 5, 500);
        }
    }

    /**
     * One source claim and where it landed.
     *
     * @param claim    the claim
     * @param landedIn the identifier of the item it landed in, {@code goal}, {@code why}, or {@code dropped}
     * @param reason   for {@code dropped}, the reason; {@code null} otherwise
     * @since 0.1
     */
    public record SourceClaim(String claim, String landedIn, String reason) {

        private static final Pattern LANDED_IN = Pattern.compile("((AC|NG|CN|DC|AN|DF)-\\d{1,4}|goal|why|dropped)");

        /**
         * @throws IllegalArgumentException if a member is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public SourceClaim {
            Objects.requireNonNull(landedIn, "landedIn");
            bounded(claim, "source_claims[].claim", 5, 500);
            if (!LANDED_IN.matcher(landedIn).matches()) {
                throw new IllegalArgumentException("a source claim lands in an item id, goal, why or dropped: "
                        + landedIn);
            }
            if (reason != null && reason.length() > 500) {
                throw new IllegalArgumentException("source_claims[].reason is at most 500 characters");
            }
        }
    }
}
