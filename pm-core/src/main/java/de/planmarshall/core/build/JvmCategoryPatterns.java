/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The compiled category table the JVM build parsers share.
 * <p>
 * The table is compiled once per tool, not built from strings at parse time. Each category holds patterns in definition
 * order and the first matching pattern wins, so the table decides the category of a message exactly as the ported
 * {@code plan-marshall} {@code _build_jvm_patterns.py} does: a pattern carrying a regular-expression metacharacter is a
 * case-insensitive regular expression matched anywhere in the message, and every other pattern is a case-insensitive
 * literal substring.
 * <p>
 * A bracketed javac tag is written escaped ({@code \[deprecation\]}). Left bare, the brackets form a character class
 * that matches any one of the tag's letters, so nearly every log line would match it; {@link #maven()} therefore treats a
 * message that carries none of the tag's characters as unclassified.
 * <p>
 * {@link #maven()} and {@link #gradle()} start from the shared JVM base table (compilation, test, dependency, Javadoc,
 * deprecation, unchecked and OpenRewrite-info patterns) and replace the lists the tool curates differently, keeping the
 * category order of the base table.
 * <p>
 * The class holds state, so it is a plain final class rather than a Lombok utility class.
 *
 * @since 0.1
 */
public final class JvmCategoryPatterns {

    private static final Pattern METACHARACTERS = Pattern.compile("[\\\\^$.*+?{}\\[\\]|()]");

    private static final String REWRITE_PREFIX = "rewrite:";

    private static final JvmCategoryPatterns MAVEN = compile(true);

    private static final JvmCategoryPatterns GRADLE = compile(false);

    private final List<Rule> rules;

    private JvmCategoryPatterns(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * The table of the Maven parser.
     *
     * @return the compiled Maven table
     */
    public static JvmCategoryPatterns maven() {
        return MAVEN;
    }

    /**
     * The table of the Gradle parser.
     *
     * @return the compiled Gradle table
     */
    public static JvmCategoryPatterns gradle() {
        return GRADLE;
    }

    /**
     * Categorises one message.
     *
     * @param message the message to categorise, with the tool's level prefix removed
     * @return the first category whose pattern matches, or {@link Category#OTHER} when none does
     * @throws NullPointerException if the message is null
     */
    public Category categorize(String message) {
        Objects.requireNonNull(message, "message");
        for (Rule rule : rules) {
            if (rule.matcher().matches(message)) {
                return rule.category();
            }
        }
        return Category.OTHER;
    }

    private static JvmCategoryPatterns compile(boolean maven) {
        Map<Category, List<String>> definitions = new LinkedHashMap<>(base());
        if (maven) {
            definitions.put(Category.OPENREWRITE_INFO,
                    List.of("org.openrewrite", "rewrite-maven-plugin", REWRITE_PREFIX));
        } else {
            definitions.putAll(gradleOverrides());
        }
        var rules = new ArrayList<Rule>();
        definitions.forEach((category, patterns) -> patterns.stream()
                .map(pattern -> new Rule(category, matcher(pattern)))
                .forEach(rules::add));
        return new JvmCategoryPatterns(rules);
    }

    private static Map<Category, List<String>> base() {
        var definitions = new LinkedHashMap<Category, List<String>>();
        definitions.put(Category.COMPILATION_ERROR,
                List.of("cannot find symbol", "incompatible types", "illegal start",
                        "class, interface, or enum expected", "unreported exception", "method does not override",
                        "not a statement", "package does not exist", "cannot be applied"));
        definitions.put(Category.TEST_FAILURE,
                List.of("tests run:", "failure!", "test failure", "assertionfailed", "expected:"));
        definitions.put(Category.DEPENDENCY_ERROR,
                List.of("could not resolve dependencies", "could not find artifact", "missing, no dependency",
                        "artifact not found", "non-resolvable"));
        definitions.put(Category.JAVADOC_WARNING,
                List.of("javadoc", "no @param", "no @return", "@param name", "missing @"));
        definitions.put(Category.DEPRECATION_WARNING, List.of("\\[deprecation\\]", "has been deprecated"));
        definitions.put(Category.UNCHECKED_WARNING, List.of("\\[unchecked\\]", "unchecked conversion"));
        definitions.put(Category.OPENREWRITE_INFO, List.of("org.openrewrite", REWRITE_PREFIX));
        return definitions;
    }

    private static Map<Category, List<String>> gradleOverrides() {
        var overrides = new LinkedHashMap<Category, List<String>>();
        overrides.put(Category.COMPILATION_ERROR,
                List.of("error:\\s+cannot find symbol", "error:\\s+incompatible types", "error:\\s+illegal start",
                        "error:\\s+';' expected", "error:\\s+class .* is public",
                        "error:\\s+package .* does not exist", "error:\\s+method .* cannot be applied",
                        "error:\\s+unreported exception",
                        "error:\\s+variable .* might not have been initialized", "error:\\s+cannot access",
                        "Unresolved reference", "Type mismatch", "Smart cast to .* is impossible",
                        "None of the following candidates is applicable", "Overload resolution ambiguity",
                        "Val cannot be reassigned", "Execution failed for task ':.*:compileJava'",
                        "Execution failed for task ':.*:compileKotlin'"));
        overrides.put(Category.TEST_FAILURE,
                List.of(">\\s+\\d+ tests? completed, \\d+ failed", "FAILED", "AssertionFailedError", "AssertionError",
                        "Execution failed for task ':.*:test'"));
        overrides.put(Category.DEPENDENCY_ERROR,
                List.of("Could not resolve", "Could not find", "Could not download", "Failed to resolve",
                        "Cannot resolve external dependency"));
        overrides.put(Category.JAVADOC_WARNING,
                List.of("warning:\\s+no @param", "warning:\\s+no @return", "warning:\\s+missing @", "javadoc",
                        "Execution failed for task ':.*:javadoc'"));
        overrides.put(Category.DEPRECATION_WARNING, List.of("\\[deprecation\\]", "has been deprecated", "is deprecated"));
        overrides.put(Category.UNCHECKED_WARNING, List.of("\\[unchecked\\]", "unchecked conversion", "unchecked call"));
        overrides.put(Category.OPENREWRITE_INFO,
                List.of("org\\.openrewrite", "rewrite-gradle-plugin", REWRITE_PREFIX));
        return overrides;
    }

    private static Matcher matcher(String pattern) {
        if (METACHARACTERS.matcher(pattern).find()) {
            var compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            return new RegexMatcher(compiled);
        }
        return new SubstringMatcher(pattern.toLowerCase(Locale.ROOT));
    }

    private record Rule(Category category, Matcher matcher) {
    }

    private sealed interface Matcher {

        boolean matches(String message);
    }

    private record SubstringMatcher(String literal) implements Matcher {

        @Override
        public boolean matches(String message) {
            return message.toLowerCase(Locale.ROOT).contains(literal);
        }
    }

    private record RegexMatcher(Pattern pattern) implements Matcher {

        @Override
        public boolean matches(String message) {
            return pattern.matcher(message).find();
        }
    }
}
