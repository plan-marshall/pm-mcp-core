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

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The plan's solution outline, the model-supplied document the server accepts at {@code submit:outline} (Store Schemas
 * § Outline Record; Phase Workflows § Phase 3 Validation). {@code outline.md} is rendered from it and never read back;
 * the type is the {@code outline-record} named parameter schema.
 * <p>
 * A deliverable names the criteria and decisions of the intent record it implements in {@code satisfies} (Phase
 * Workflows § 6 {@code satisfies} Coverage), which {@link de.planmarshall.core.spi.validation.OutlineValidator} checks
 * against the record when the record-based overload runs.
 *
 * @param summary      what the solution is, 20 to 2000 characters
 * @param overview     an optional text diagram of the solution; may be {@code null}
 * @param scopeEstimate the size of the change
 * @param deliverables one to 50 deliverables
 * @since 0.1
 */
public record OutlineRecord(String summary, @Nullable String overview, ScopeEstimate scopeEstimate,
List<Deliverable> deliverables) {

    /** The pattern of an entry in a deliverable's {@code satisfies}. */
    public static final Pattern SATISFIES_ID = Pattern.compile("(AC|DC)-\\d{1,4}");

    /** The pattern of an added dependency's coordinate. */
    public static final Pattern COORDINATE = Pattern.compile("(maven|npm|pypi):.+");

    /** The wire name of the verification profile and of the verification member. */
    private static final String VERIFICATION_WIRE = "verification";

    /**
     * Copies the deliverables into an immutable list and checks the outline's rules.
     *
     * @throws IllegalArgumentException if a member is out of its bounds or a deliverable is inconsistent
     * @throws NullPointerException     if a value is {@code null}
     */
    public OutlineRecord {
        Objects.requireNonNull(summary, "summary");
        if (summary.length() < 20 || summary.length() > 2000) {
            throw new IllegalArgumentException("summary is 20 to 2000 characters: " + summary.length());
        }
        if (overview != null && overview.length() > 8000) {
            throw new IllegalArgumentException("overview is at most 8000 characters: " + overview.length());
        }
        Objects.requireNonNull(scopeEstimate, "scopeEstimate");
        Objects.requireNonNull(deliverables, "deliverables");
        if (deliverables.isEmpty() || deliverables.size() > 50) {
            throw new IllegalArgumentException("deliverables holds 1 to 50 items: " + deliverables.size());
        }
        deliverables = List.copyOf(deliverables);
        checkDeliverables(deliverables);
    }

    private static void checkDeliverables(List<Deliverable> deliverables) {
        var numbers = new HashSet<Integer>();
        for (var deliverable : deliverables) {
            if (!numbers.add(deliverable.number())) {
                throw new IllegalArgumentException("the deliverable number %d is used twice"
                        .formatted(deliverable.number()));
            }
        }
        for (var deliverable : deliverables) {
            checkDependencies(deliverable, numbers);
        }
    }

    private static void checkDependencies(Deliverable deliverable, Set<Integer> numbers) {
        if (deliverable.depends().size() != new HashSet<>(deliverable.depends()).size()) {
            throw new IllegalArgumentException(
                    "the deliverable %d names a dependency more than once".formatted(deliverable.number()));
        }
        for (var dependency : deliverable.depends()) {
            if (!numbers.contains(dependency)) {
                throw new IllegalArgumentException("the deliverable %d depends on %d, which the outline does not hold"
                        .formatted(deliverable.number(), dependency));
            }
            if (dependency == deliverable.number()) {
                throw new IllegalArgumentException(
                        "the deliverable %d depends on itself".formatted(deliverable.number()));
            }
        }
    }

    /**
     * @param number a deliverable number
     * @return the deliverable of that number; empty when none has it
     */
    public Optional<Deliverable> deliverable(int number) {
        return deliverables.stream().filter(item -> item.number() == number).findFirst();
    }

    /**
     * @param number a deliverable number
     * @return every deliverable the deliverable depends on, directly or through others
     */
    public List<Integer> transitiveDependencies(int number) {
        var found = new LinkedHashSet<Integer>();
        var pending = new ArrayDeque<>(deliverable(number).map(Deliverable::depends).orElseGet(List::of));
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (found.add(current)) {
                deliverable(current).ifPresent(item -> pending.addAll(item.depends()));
            }
        }
        return List.copyOf(found);
    }

    /** The size of the change. */
    public enum ScopeEstimate {
        /** No source change. */
        NONE("none"),
        /** One small, localized change. */
        SURGICAL("surgical"),
        /** One module. */
        SINGLE_MODULE("single_module"),
        /** Several modules. */
        MULTI_MODULE("multi_module"),
        /** A change across the project. */
        BROAD("broad");

        private final String wireName;

        ScopeEstimate(String wireName) {
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
        public static Optional<ScopeEstimate> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** The kind of change a deliverable makes. */
    public enum ChangeType {
        /** An analysis, no production change. */
        ANALYSIS("analysis"),
        /** A new capability. */
        FEATURE("feature"),
        /** An extension of a capability. */
        ENHANCEMENT("enhancement"),
        /** A defect fix. */
        BUG_FIX("bug_fix"),
        /** A reduction of technical debt. */
        TECH_DEBT("tech_debt"),
        /** A verification without a change. */
        VERIFICATION(VERIFICATION_WIRE);

        private final String wireName;

        ChangeType(String wireName) {
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
        public static Optional<ChangeType> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** How a deliverable is executed. */
    public enum ExecutionMode {
        /** Without a model. */
        AUTOMATED("automated"),
        /** By the operator. */
        MANUAL("manual"),
        /** Both. */
        MIXED("mixed");

        private final String wireName;

        ExecutionMode(String wireName) {
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
        public static Optional<ExecutionMode> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** The execution profile a deliverable requires. */
    public enum Profile {
        /** Production code. */
        IMPLEMENTATION("implementation"),
        /** Module tests. */
        MODULE_TESTING("module_testing"),
        /** Multi-module and end-to-end tests. */
        INTEGRATION_TESTING("integration_testing"),
        /** A verification without a change. */
        VERIFICATION(VERIFICATION_WIRE);

        private final String wireName;

        Profile(String wireName) {
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
        public static Optional<Profile> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** What a deliverable does with an affected file. */
    public enum FileIntent {
        /** Reads it. */
        READ("read"),
        /** Creates it. */
        WRITE_NEW("write-new"),
        /** Replaces its content. */
        WRITE_REPLACE("write-replace"),
        /** Writes it when a gate fires. */
        WRITE_CONDITIONAL("write-conditional"),
        /** Deletes it. */
        DELETE("delete");

        private final String wireName;

        FileIntent(String wireName) {
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
        public static Optional<FileIntent> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * One deliverable of the outline.
     *
     * @param number          the deliverable number, 1 to 99, unique in the outline
     * @param title           the title, 3 to 120 characters
     * @param intentGloss     one sentence of what the deliverable does, copied into its tasks
     * @param changeType      the kind of change
     * @param executionMode   how the deliverable is executed
     * @param domain          the domain of the deliverable
     * @param module          the module of the deliverable
     * @param depends         the deliverables it depends on
     * @param designNotes     the design of the change; may be {@code null}
     * @param profiles        the execution profiles, at least one
     * @param affectedFiles   the files the deliverable touches
     * @param changePerFile   the change per file, 10 to 8000 characters
     * @param verification    the commands that verify the deliverable
     * @param satisfies       the criteria and decisions of the intent record it implements
     * @param addedDependencies the third-party dependencies it adds
     * @param interfaces      the contracts it shares
     * @param keyLinks        how its code is wired in
     * @since 0.1
     */
    public record Deliverable(int number, String title, String intentGloss, ChangeType changeType,
    ExecutionMode executionMode, String domain, String module, List<Integer> depends, @Nullable String designNotes,
    List<Profile> profiles, List<AffectedFile> affectedFiles, String changePerFile,
    List<Verification> verification, List<String> satisfies, List<AddedDependency> addedDependencies,
    List<Interface> interfaces, List<KeyLink> keyLinks) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds or a list is inconsistent
         * @throws NullPointerException     if a value is {@code null}
         */
        public Deliverable {
            if (number < 1 || number > 99) {
                throw new IllegalArgumentException("a deliverable number is 1 to 99: " + number);
            }
            bounded(title, "deliverables[].title", 3, 120);
            bounded(intentGloss, "deliverables[].intent_gloss", 5, 200);
            Objects.requireNonNull(changeType, "changeType");
            Objects.requireNonNull(executionMode, "executionMode");
            bounded(domain, "deliverables[].domain", 1, 64);
            bounded(module, "deliverables[].module", 1, 128);
            Objects.requireNonNull(depends, "depends");
            depends = List.copyOf(depends);
            if (designNotes != null && designNotes.length() > 4000) {
                throw new IllegalArgumentException("design_notes is at most 4000 characters: " + designNotes.length());
            }
            Objects.requireNonNull(profiles, "profiles");
            profiles = List.copyOf(profiles);
            if (profiles.isEmpty() || new HashSet<>(profiles).size() != profiles.size()) {
                throw new IllegalArgumentException(
                        "the deliverable %d names its profiles once each and at least one".formatted(number));
            }
            Objects.requireNonNull(affectedFiles, "affectedFiles");
            affectedFiles = checkedList(affectedFiles, "affected_files", 200);
            bounded(changePerFile, "deliverables[].change_per_file", 10, 8000);
            Objects.requireNonNull(verification, VERIFICATION_WIRE);
            verification = checkedList(verification, VERIFICATION_WIRE, 10);
            Objects.requireNonNull(satisfies, "satisfies");
            satisfies = List.copyOf(satisfies);
            if (satisfies.size() > 40 || new HashSet<>(satisfies).size() != satisfies.size()) {
                throw new IllegalArgumentException(
                        "the deliverable %d names at most 40 satisfies entries once each".formatted(number));
            }
            for (var entry : satisfies) {
                if (!SATISFIES_ID.matcher(entry).matches()) {
                    throw new IllegalArgumentException(
                            "the deliverable %d satisfies '%s', which is no AC-<n> or DC-<n>".formatted(number, entry));
                }
            }
            Objects.requireNonNull(addedDependencies, "addedDependencies");
            addedDependencies = checkedList(addedDependencies, "added_dependencies", 20);
            Objects.requireNonNull(interfaces, "interfaces");
            interfaces = checkedList(interfaces, "interfaces", 30);
            Objects.requireNonNull(keyLinks, "keyLinks");
            keyLinks = checkedList(keyLinks, "key_links", 20);
        }

        /**
         * @param id a criterion or decision identifier
         * @return whether the deliverable names it in {@code satisfies}
         */
        public boolean satisfies(String id) {
            return satisfies.contains(id);
        }

        private static <T> List<T> checkedList(List<T> values, String member, int max) {
            if (values.size() > max) {
                throw new IllegalArgumentException("%s holds at most %d items: %d".formatted(member, max,
                        values.size()));
            }
            if (values.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(member + " holds a null item");
            }
            return List.copyOf(values);
        }
    }

    /**
     * One file a deliverable touches.
     *
     * @param path   the repository-relative path
     * @param intent what the deliverable does with it
     * @param gate   for {@code write-conditional}, the gate whose firing changes the file; {@code null} otherwise
     * @since 0.1
     */
    public record AffectedFile(String path, FileIntent intent, @Nullable String gate) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds or the gate does not go with the intent
         * @throws NullPointerException     if a value is {@code null}
         */
        public AffectedFile {
            bounded(path, "affected_files[].path", 1, 512);
            Objects.requireNonNull(intent, "intent");
            if (gate != null && gate.length() > 200) {
                throw new IllegalArgumentException("affected_files[].gate is at most 200 characters");
            }
            if (intent == FileIntent.WRITE_CONDITIONAL && gate == null) {
                throw new IllegalArgumentException(
                        "the write-conditional file '%s' names its gate".formatted(path));
            }
            if (intent != FileIntent.WRITE_CONDITIONAL && gate != null) {
                throw new IllegalArgumentException(
                        "the file '%s' carries a gate, but only a write-conditional file has one".formatted(path));
            }
        }
    }

    /**
     * One verification command of a deliverable.
     *
     * @param command   the command
     * @param criterion the criterion the command decides
     * @since 0.1
     */
    public record Verification(String command, String criterion) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public Verification {
            bounded(command, "verification[].command", 1, 1000);
            bounded(criterion, "verification[].criterion", 5, 500);
        }
    }

    /**
     * One third-party dependency a deliverable adds.
     *
     * @param coordinate           the coordinate, {@code maven:}, {@code npm:} or {@code pypi:} prefixed
     * @param why                  why it is needed
     * @param rejectedAlternative  the simpler alternative considered and why it was rejected
     * @since 0.1
     */
    public record AddedDependency(String coordinate, String why, String rejectedAlternative) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public AddedDependency {
            bounded(coordinate, "added_dependencies[].coordinate", 1, 256);
            if (!COORDINATE.matcher(coordinate).matches()) {
                throw new IllegalArgumentException(
                        "the coordinate '%s' names none of maven, npm and pypi".formatted(coordinate));
            }
            bounded(why, "added_dependencies[].why", 10, 500);
            bounded(rejectedAlternative, "added_dependencies[].rejected_alternative", 10, 500);
        }
    }

    /** The direction of a shared contract. */
    public enum Direction {
        /** The deliverable produces the symbol. */
        PRODUCES("produces"),
        /** The deliverable consumes the symbol. */
        CONSUMES("consumes");

        private final String wireName;

        Direction(String wireName) {
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
        public static Optional<Direction> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * One shared contract of a deliverable.
     *
     * @param direction whether the deliverable produces or consumes the symbol
     * @param symbol    the fully qualified name
     * @param signature the exact signature
     * @since 0.1
     */
    public record Interface(Direction direction, String symbol, String signature) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public Interface {
            Objects.requireNonNull(direction, "direction");
            bounded(symbol, "interfaces[].symbol", 1, 300);
            bounded(signature, "interfaces[].signature", 1, 1000);
        }
    }

    /**
     * How a deliverable's code is wired into the system.
     *
     * @param from    a path of the deliverable's write set
     * @param to      the symbol or path it connects to
     * @param pattern the regular expression that matches in {@code from} when the link exists
     * @since 0.1
     */
    public record KeyLink(String from, String to, String pattern) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public KeyLink {
            bounded(from, "key_links[].from", 1, 512);
            bounded(to, "key_links[].to", 1, 300);
            bounded(pattern, "key_links[].pattern", 1, 500);
        }
    }

    private static String bounded(String value, String member, int min, int max) {
        Objects.requireNonNull(value, member);
        if (value.length() < min || value.length() > max) {
            throw new IllegalArgumentException(
                    "%s is %d to %d characters: %d".formatted(member, min, max, value.length()));
        }
        return value;
    }
}
