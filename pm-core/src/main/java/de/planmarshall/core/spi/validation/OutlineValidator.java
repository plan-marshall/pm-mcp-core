/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates a solution outline a model submits (PM-WF-6), with the rules the text decides on its own.
 * <p>
 * The outline is Markdown. Two of its lists are read line by line, each from its marker line to the next line that
 * starts with {@code **} or {@code #}:
 * <ul>
 * <li>below {@code **Affected files:**} every line is a bullet whose first token is a back-quoted path, followed by
 * one of the intents {@code read}, {@code write-new}, {@code write-replace}, {@code write-conditional} and
 * {@code delete} in parentheses;</li>
 * <li>below {@code **Added dependencies:**} every line is a bullet with one back-quoted coordinate
 * {@code maven:<groupId>:<artifactId>}, {@code npm:<name>} or {@code pypi:<name>}, without a version, and no
 * coordinate is named twice in the outline.</li>
 * </ul>
 * A line of such a list that is not a bullet of that form is a violation; it is never skipped, so a list cannot
 * lose an entry to a typing error.
 * <p>
 * Whether the tasks of an outline can be derived, whether their dependencies form a graph without a cycle, and
 * whether a deliverable declares the profiles its files require is not decided here: these checks need the task
 * model and the build extensions of a project.
 *
 * @since 0.1
 */
public final class OutlineValidator implements ContentValidator {

    /** The outline is empty or consists of white space only. */
    public static final String RULE_BLANK = "outline.blank";

    /** A line of an affected files list is not a bullet that starts with a back-quoted path. */
    public static final String RULE_AFFECTED_FILE_UNPARSED = "outline.affected-file-unparsed";

    /** An affected file does not end with one of the five intents in parentheses. */
    public static final String RULE_AFFECTED_FILE_INTENT = "outline.affected-file-intent";

    /** A line of an added dependencies list is not a bullet with one back-quoted coordinate. */
    public static final String RULE_DEPENDENCY_UNPARSED = "outline.dependency-unparsed";

    /** A coordinate names an ecosystem other than {@code maven}, {@code npm} and {@code pypi}. */
    public static final String RULE_DEPENDENCY_ECOSYSTEM = "outline.dependency-ecosystem";

    /** A coordinate is not of the form of its ecosystem, for example because it carries a version. */
    public static final String RULE_DEPENDENCY_COORDINATE = "outline.dependency-coordinate";

    /** A coordinate is named a second time in the outline. */
    public static final String RULE_DEPENDENCY_DUPLICATE = "outline.dependency-duplicate";

    private static final String AFFECTED_FILES = "**Affected files:**";

    private static final String ADDED_DEPENDENCIES = "**Added dependencies:**";

    private static final Pattern PATH_BULLET = Pattern.compile("\\s*- `[^`]+`.*");

    private static final Pattern AFFECTED_FILE = Pattern.compile(
            "\\s*- `[^`]+`\\s+\\((?:read|write-new|write-replace|write-conditional|delete)\\)\\s*");

    private static final Pattern DEPENDENCY = Pattern.compile("\\s*- `(?<ecosystem>[^`:]*):(?<name>[^`]*)`\\s*");

    private static final Pattern PYPI_SEPARATORS = Pattern.compile("[-_.]+");

    /** The form of a coordinate after its ecosystem prefix, by ecosystem. */
    private static final Map<String, Pattern> COORDINATES = Map.of(
            "maven", Pattern.compile("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+"),
            "npm", Pattern.compile("(?:@[a-z0-9~-][a-z0-9._~-]*/)?[a-z0-9~-][a-z0-9._~-]*"),
            "pypi", Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?"));

    private enum Section {
        OTHER, AFFECTED_FILES, ADDED_DEPENDENCIES
    }

    @Override
    public List<ContentViolation> validate(String text) {
        if (text.isBlank()) {
            return List.of(new ContentViolation(RULE_BLANK, 0, "the outline is blank"));
        }
        var lines = text.lines().toList();
        var violations = new ArrayList<ContentViolation>();
        var dependencyLines = new HashMap<String, Integer>();
        var section = Section.OTHER;
        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            var stripped = line.strip();
            if (stripped.startsWith("**") || stripped.startsWith("#")) {
                section = sectionOf(stripped);
            } else if (!stripped.isEmpty() && section == Section.AFFECTED_FILES) {
                checkAffectedFile(line, index + 1, violations);
            } else if (!stripped.isEmpty() && section == Section.ADDED_DEPENDENCIES) {
                checkDependency(line, index + 1, dependencyLines, violations);
            }
        }
        return List.copyOf(violations);
    }

    private static Section sectionOf(String marker) {
        return switch (marker) {
            case AFFECTED_FILES -> Section.AFFECTED_FILES;
            case ADDED_DEPENDENCIES -> Section.ADDED_DEPENDENCIES;
            default -> Section.OTHER;
        };
    }

    private static void checkAffectedFile(String line, int number, List<ContentViolation> violations) {
        if (AFFECTED_FILE.matcher(line).matches()) {
            return;
        }
        if (PATH_BULLET.matcher(line).matches()) {
            violations.add(new ContentViolation(RULE_AFFECTED_FILE_INTENT, number,
                    "the affected file '%s' does not end with one of the intents (read), (write-new), (write-replace), (write-conditional) and (delete)"
                            .formatted(line.strip())));
        } else {
            violations.add(new ContentViolation(RULE_AFFECTED_FILE_UNPARSED, number,
                    "the line '%s' of an affected files list is not a bullet with a back-quoted path"
                            .formatted(line.strip())));
        }
    }

    private static void checkDependency(String line, int number, Map<String, Integer> dependencyLines,
            List<ContentViolation> violations) {
        var matcher = DEPENDENCY.matcher(line);
        if (!matcher.matches()) {
            violations.add(new ContentViolation(RULE_DEPENDENCY_UNPARSED, number,
                    "the line '%s' of an added dependencies list is not a bullet with one back-quoted coordinate"
                            .formatted(line.strip())));
            return;
        }
        var ecosystem = matcher.group("ecosystem");
        var name = matcher.group("name");
        var form = COORDINATES.get(ecosystem);
        if (form == null) {
            violations.add(new ContentViolation(RULE_DEPENDENCY_ECOSYSTEM, number,
                    "the coordinate '%s:%s' names the ecosystem '%s', which is none of maven, npm and pypi"
                            .formatted(ecosystem, name, ecosystem)));
        } else if (!form.matcher(name).matches()) {
            violations.add(new ContentViolation(RULE_DEPENDENCY_COORDINATE, number,
                    "the coordinate '%s:%s' is not of the form of a %s coordinate without a version"
                            .formatted(ecosystem, name, ecosystem)));
        } else {
            var first = dependencyLines.putIfAbsent(ecosystem + ":" + comparable(ecosystem, name), number);
            if (first != null) {
                violations.add(new ContentViolation(RULE_DEPENDENCY_DUPLICATE, number,
                        "the coordinate '%s:%s' is already named on line %d".formatted(ecosystem, name, first)));
            }
        }
    }

    /**
     * @return the name in the form two names of the ecosystem are compared in: a Python package name is compared
     *         in lower case with every run of {@code -}, {@code _} and {@code .} read as one {@code -}
     */
    private static String comparable(String ecosystem, String name) {
        return "pypi".equals(ecosystem)
                ? PYPI_SEPARATORS.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("-")
                : name;
    }
}
