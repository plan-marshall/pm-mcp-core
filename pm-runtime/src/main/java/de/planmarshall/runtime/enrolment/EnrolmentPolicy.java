/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.enrolment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.ArgumentSchema;
import de.planmarshall.core.store.ProjectEnrolment.Argv;
import de.planmarshall.core.store.ProjectEnrolment.BuildSystem;

/**
 * The enrolment policy (PM-EXEC-1, PM-EXEC-2): the rules the runtime applies to an enrolment before it signs it, at
 * {@code enrol} and at the re-baseline, so it never signs an enrolment that violates them. A violation is refused as
 * {@code enrolment_invalid}.
 * <p>
 * Only the rules the specification states in concrete terms are checked:
 * <ol>
 * <li>Placeholders of an argument vector ({@code build.canonical_templates}, {@code build.discovery_templates},
 * {@code providers[].verify_argv}): {@code {module}} is the only placeholder, and it may appear only inside the
 * module-argument group; a template with any other placeholder is refused (domain-extensions/06-configuration.adoc,
 * "Typed Placeholder Validation" and "Optional Module-Argument Group"). A placeholder is a brace-delimited token
 * {@code {...}}; a lone brace and {@code {}} are not placeholders.</li>
 * <li>Write paths: never under the base directory, and the write set never contains it
 * (job-runtime/02-confinement-and-environment.adoc). An absolute write path is judged by its normalized form and by
 * the real path of its longest existing ancestor (a dangling symbolic link counts as existing) with the names below
 * it that do not exist yet appended, so a missing leaf below a symbolic link into the base directory is caught; it is
 * refused when either is the base directory, lies below it or contains it, against the base and its real path. A
 * {@code ..} below that ancestor is refused, because nothing exists to resolve it against, and so is a path whose
 * ancestor cannot be resolved (a dangling or looping link, no permission): the policy fails closed there. A path that
 * is not absolute is not judged, because the enrolment does not name what it is relative to, and neither is one that
 * does not parse.</li>
 * <li>Build environment names: credential-pattern variable names, {@code DBUS_SESSION_BUS_ADDRESS},
 * {@code XDG_RUNTIME_DIR} and {@code MVND_DAEMON_STORAGE} are refused (job-runtime/02-confinement-and-environment.adoc,
 * "Job Environment"; credentials-and-enrolment/01-provider-credentials.adoc). A credential variable is a name that ends with
 * {@code _TOKEN}, {@code _PASSWORD}, {@code _SECRET} or {@code _KEY}, the patterns the specification lists; its list
 * ends with an ellipsis, and only the listed patterns are applied.</li>
 * <li>Argument schemas: a {@code pattern} is matched by a backtracking engine whose cost grows with the length of the
 * string it is matched against, so a schema that constrains a string by a pattern admits a {@code maxLength} of at
 * most {@value ArgumentSchema#PATTERN_MAX_LENGTH_CEILING} code points. A schema above that ceiling, at its root or in
 * one of its properties, is refused: the length the engine is given is then bounded by the schema's admission as well
 * as by the enrolling project's choice (job-runtime/03-parsers-and-gates.adoc, "Ingestion Validator").</li>
 * </ol>
 * The shell metacharacters and the forbidden free flags are not refused at enrolment: they apply to what a caller
 * supplies to a job and are refused at job time by the argument check of {@code runtime.job}, the last stage of the
 * verify-not-resolve pipeline; the enrolled templates are the operator's and are not filtered. Not checked, because
 * the specification does not define it concretely: the unique project root ({@code enrolment_root_conflict},
 * {@code project_store_root_mismatch}).
 *
 * @since 0.1
 */
@UtilityClass
class EnrolmentPolicy {

    /** The one placeholder an argument vector admits, and only inside its module-argument group. */
    static final String MODULE_PLACEHOLDER = "{module}";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^{}]+}");

    private static final String ARGUMENT_SCHEMA = ".argument_schema";

    private static final String ABOVE_CEILING =
            ": maxLength %d is above the ceiling %d of a schema with a pattern";

    private static final String WRITE_PATHS = "write_paths: ";

    private static final String BELOW_ANCESTOR = " below its last existing ancestor ";

    private static final String BASE_VIOLATION = WRITE_PATHS + "%s is, lies below or contains the base directory %s";

    private static final String THROUGH_PARENT = WRITE_PATHS + "%s goes through '..'" + BELOW_ANCESTOR + "%s";

    private static final String UNRESOLVABLE = WRITE_PATHS + "%s cannot be resolved" + BELOW_ANCESTOR + "%s";

    private static final String PARENT = "..";

    private static final String CURRENT = ".";

    private static final List<String> CREDENTIAL_SUFFIXES = List.of("_TOKEN", "_PASSWORD", "_SECRET", "_KEY");

    private static final Set<String> REFUSED_ENVIRONMENT_NAMES =
            Set.of("DBUS_SESSION_BUS_ADDRESS", "XDG_RUNTIME_DIR", "MVND_DAEMON_STORAGE");

    /**
     * @param enrolment the enrolment to sign
     * @param base      the base directory of the runtime
     * @return why the enrolment violates the rules, one reason per violation, in a deterministic order (canonical
     *         templates, discovery templates, providers, argument schemas, write paths, environment names); empty if
     *         it complies
     */
    static List<String> violations(ProjectEnrolment enrolment, Path base) {
        Objects.requireNonNull(enrolment, "enrolment");
        var bases = withRealPath(Objects.requireNonNull(base, "base").toAbsolutePath().normalize());
        var violations = new ArrayList<String>();
        templates("build.canonical_templates", enrolment.build().canonicalTemplates(), violations);
        templates("build.discovery_templates", enrolment.build().discoveryTemplates(), violations);
        enrolment.providers().forEach((provider, verification) -> placeholders(
                "providers." + provider.wireName() + ".verify_argv", verification.verifyArgv(), violations));
        enrolment.projectScripts().forEach((id, script) -> schema(
                "project_scripts." + id + ARGUMENT_SCHEMA, script.argumentSchema(), violations));
        for (var writePath : enrolment.writePaths()) {
            writePath(writePath, bases).ifPresent(violations::add);
        }
        for (var name : enrolment.buildEnvironment().keySet()) {
            environmentName(name).ifPresent(violations::add);
        }
        return List.copyOf(violations);
    }

    private static void templates(String place, Map<BuildSystem, SortedMap<String, Argv>> templates,
            List<String> violations) {
        templates.forEach((system, vectors) -> vectors.forEach((name, argv) -> placeholders(
                place + "." + system.wireName() + "." + name, argv, violations)));
    }

    private static void placeholders(String vector, Argv argv, List<String> violations) {
        for (var element : argv.elements()) {
            switch (element) {
                case Argv.Literal(var value) -> tokens(value).forEach(token -> violations.add(
                        "%s: placeholder %s outside the module-argument group".formatted(vector, token)));
                case Argv.ModuleGroup(var arguments) -> arguments.stream()
                        .flatMap(argument -> tokens(argument).stream())
                        .filter(token -> !MODULE_PLACEHOLDER.equals(token))
                        .forEach(token -> violations.add(
                                "%s: placeholder %s is not %s".formatted(vector, token, MODULE_PLACEHOLDER)));
            }
        }
    }

    private static List<String> tokens(String argument) {
        var matcher = PLACEHOLDER.matcher(argument);
        var tokens = new ArrayList<String>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    /**
     * Refuses a schema, at its root or in one of its properties, that matches a string by a pattern — a schema of type
     * {@code string} or an untyped one, both of which the validator applies its pattern to — and admits a
     * {@code maxLength} above {@link ArgumentSchema#PATTERN_MAX_LENGTH_CEILING}.
     */
    private static void schema(String place, ArgumentSchema schema, List<String> violations) {
        var maxLength = schema.maxLength();
        var matchesStrings = schema.type() == null || schema.type() == ProjectEnrolment.SchemaType.STRING;
        if (matchesStrings && schema.pattern() != null && maxLength != null
                && maxLength > ArgumentSchema.PATTERN_MAX_LENGTH_CEILING) {
            violations.add(place + ABOVE_CEILING.formatted(maxLength, ArgumentSchema.PATTERN_MAX_LENGTH_CEILING));
        }
        var properties = schema.properties();
        if (properties != null) {
            properties.forEach((name, property) -> schema(place + "." + name, property, violations));
        }
    }

    private static Optional<String> writePath(String value, List<Path> bases) {
        return absolutePath(value).flatMap(path -> judge(value, path, bases));
    }

    /** @return the path the value names, if it parses and is absolute; empty otherwise, which is not judged */
    private static Optional<Path> absolutePath(String value) {
        try {
            var path = Path.of(value);
            return path.isAbsolute() ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException _) {
            return Optional.empty();
        }
    }

    /**
     * Judges an absolute write path by its lexical form and by the real path of its longest existing ancestor with the
     * names below it appended; a {@code ..} below that ancestor and an ancestor that cannot be resolved are refused.
     */
    private static Optional<String> judge(String value, Path path, List<Path> bases) {
        var existing = longestExistingAncestor(path);
        var remainder = remainder(path, existing);
        if (remainder.contains(PARENT)) {
            return Optional.of(THROUGH_PARENT.formatted(value, existing));
        }
        var real = realPath(existing);
        if (real.isEmpty()) {
            return Optional.of(UNRESOLVABLE.formatted(value, existing));
        }
        var resolved = real.get();
        for (var name : remainder) {
            resolved = resolved.resolve(name);
        }
        var violates = Stream.of(path.normalize(), resolved.normalize()).anyMatch(candidate -> bases.stream()
                .anyMatch(base -> candidate.startsWith(base) || base.startsWith(candidate)));
        return violates ? Optional.of(BASE_VIOLATION.formatted(value, bases.getFirst())) : Optional.empty();
    }

    /**
     * @return the longest prefix of the path, as given, that exists without following a final symbolic link (a
     *         dangling link counts as existing); the file system root ends the walk
     */
    private static Path longestExistingAncestor(Path path) {
        var existing = path;
        while (existing.getParent() != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        return existing;
    }

    /** @return the names of the path below its existing ancestor, without {@code .} */
    private static List<String> remainder(Path path, Path existing) {
        return IntStream.range(existing.getNameCount(), path.getNameCount())
                .mapToObj(index -> path.getName(index).toString())
                .filter(name -> !CURRENT.equals(name))
                .toList();
    }

    /** @return the real path of the existing ancestor; empty if it cannot be resolved (dangling, loop, permission) */
    private static Optional<Path> realPath(Path existing) {
        try {
            return Optional.of(existing.toRealPath());
        } catch (IOException _) {
            return Optional.empty();
        }
    }

    /** @return the path, followed by its real path when it exists and differs */
    private static List<Path> withRealPath(Path path) {
        if (!Files.exists(path)) {
            return List.of(path);
        }
        try {
            var real = path.toRealPath();
            return real.equals(path) ? List.of(path) : List.of(path, real);
        } catch (IOException _) {
            return List.of(path);
        }
    }

    private static Optional<String> environmentName(String name) {
        if (REFUSED_ENVIRONMENT_NAMES.contains(name)) {
            return Optional.of("build_environment: %s is refused for a job".formatted(name));
        }
        if (CREDENTIAL_SUFFIXES.stream().anyMatch(name::endsWith)) {
            return Optional.of("build_environment: %s is a credential variable".formatted(name));
        }
        return Optional.empty();
    }
}
