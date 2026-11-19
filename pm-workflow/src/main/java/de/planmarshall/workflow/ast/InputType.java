/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import java.util.HashSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The type of an input a unit declares in its frontmatter (PM-WF-16): one of the three closed types a call site can bind
 * from a literal value, the same three the body {@code param} declarations carried before they moved into the
 * frontmatter.
 * <p>
 * The optional form is not part of the type: an {@link InputDecl} carries it as a trailing {@code ?} in the source and
 * as its {@code optional} component in the tree, so an absent input of an optional declaration has the semantics its
 * description documents.
 *
 * @since 0.1
 */
public sealed interface InputType
        permits InputType.Enumeration, InputType.IntRange, InputType.EnumList,
        InputType.Text, InputType.CommitMessage, InputType.Path, InputType.PathList {

    /**
     * @return the source form of the type, as the frontmatter declares it: {@code enum[...]}, {@code int} with its
     *         optional range, or {@code list<enum[...]>}
     */
    String sourceText();

    /**
     * {@code enum[...]}: one of a closed set of values.
     *
     * @param values the admitted values in declaration order, none of them twice
     */
    record Enumeration(List<String> values) implements InputType {

        /**
         * Copies the values into an immutable list and refuses a value that occurs twice or none at all.
         *
         * @param values the admitted values in declaration order, none of them twice
         * @throws IllegalArgumentException if no value is present or a value occurs more than once
         */
        public Enumeration {
            values = copyDistinct(values);
        }

        @Override
        public String sourceText() {
            return "enum[" + String.join("|", values) + "]";
        }
    }

    /**
     * {@code int}: an integer with optional bounds. The source form of the bounds is the range {@code [<low>..<high>]},
     * so the two bounds are both present or both {@code null}.
     *
     * @param min the least value, {@code null} exactly when {@code max} is
     * @param max the greatest value, {@code null} exactly when {@code min} is
     */
    record IntRange(@Nullable Integer min, @Nullable Integer max) implements InputType {

        /**
         * Refuses a single bound.
         *
         * @param min the least value, {@code null} exactly when {@code max} is
         * @param max the greatest value, {@code null} exactly when {@code min} is
         * @throws IllegalArgumentException if exactly one of the two bounds is present
         */
        public IntRange {
            requireBothOrNone(min, max);
        }

        @Override
        public String sourceText() {
            return min == null ? "int" : "int[" + min + ".." + max + "]";
        }
    }

    /**
     * {@code list<enum[...]>}: a list of distinct members of a closed set of values. The type admits the empty list as a
     * value, so a declaration's {@code input_default} may be {@code []}; a call site binds a non-empty list.
     *
     * @param values the admitted element values in declaration order, none of them twice
     */
    record EnumList(List<String> values) implements InputType {

        /**
         * Copies the values into an immutable list and refuses a value that occurs twice or none at all.
         *
         * @param values the admitted element values in declaration order
         * @throws IllegalArgumentException if no value is present or a value occurs more than once
         */
        public EnumList {
            values = copyDistinct(values);
        }

        @Override
        public String sourceText() {
            return "list<enum[" + String.join("|", values) + "]>";
        }
    }

    /**
     * {@code text[lo..hi]}: a text whose length in code points lies within the bounds.
     *
     * @param min the least number of code points, {@code null} exactly when {@code max} is
     * @param max the greatest number of code points, {@code null} exactly when {@code min} is
     */
    record Text(@Nullable Integer min, @Nullable Integer max) implements InputType {
        public Text {
            requireBothOrNone(min, max);
        }

        @Override
        public String sourceText() {
            return min == null ? "text" : "text[" + min + ".." + max + "]";
        }
    }

    /**
     * {@code commit_message[lo..hi]}: a text whose length in code points lies within the bounds and which is a
     * Conventional Commit without a {@code Co-Authored-By:} line.
     *
     * @param min the least number of code points, {@code null} exactly when {@code max} is
     * @param max the greatest number of code points, {@code null} exactly when {@code min} is
     */
    record CommitMessage(@Nullable Integer min, @Nullable Integer max) implements InputType {
        public CommitMessage {
            requireBothOrNone(min, max);
        }

        @Override
        public String sourceText() {
            return min == null ? "commit_message" : "commit_message[" + min + ".." + max + "]";
        }
    }

    /** {@code path}: a relative path inside the worktree, subject to the path rules of Decision 3. */
    record Path() implements InputType {
        @Override
        public String sourceText() {
            return "path";
        }
    }

    /**
     * {@code list<path>[lo..hi]}: a list of distinct relative paths, each subject to the path rules, with a count
     * within the bounds.
     *
     * @param min the least number of entries, {@code null} exactly when {@code max} is
     * @param max the greatest number of entries, {@code null} exactly when {@code min} is
     */
    record PathList(@Nullable Integer min, @Nullable Integer max) implements InputType {
        public PathList {
            requireBothOrNone(min, max);
        }

        @Override
        public String sourceText() {
            return min == null ? "list<path>" : "list<path>[" + min + ".." + max + "]";
        }
    }

    private static void requireBothOrNone(@Nullable Integer min, @Nullable Integer max) {
        if ((min == null) != (max == null)) {
            throw new IllegalArgumentException("a range needs both bounds or none, but only the %s bound is present"
                    .formatted(min != null ? "low" : "high"));
        }
    }

    private static List<String> copyDistinct(List<String> values) {
        var copied = List.copyOf(values);
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("a closed value set needs at least one value");
        }
        var seen = new HashSet<String>();
        for (var value : copied) {
            if (!seen.add(value)) {
                throw new IllegalArgumentException("the value '%s' occurs twice in the set".formatted(value));
            }
        }
        return copied;
    }
}
