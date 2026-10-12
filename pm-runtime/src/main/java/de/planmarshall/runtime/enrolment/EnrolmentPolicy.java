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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;


import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.Argv;
import de.planmarshall.core.store.ProjectEnrolment.BuildSystem;
import lombok.experimental.UtilityClass;

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
 * (job-runtime/02-confinement-and-environment.adoc). An absolute write path is refused when it is the base directory,
 * lies below it or contains it, by its normalized form and, when it exists, by its real path, against the base and its
 * real path. A path that is not absolute is not judged, because the enrolment does not name what it is relative to,
 * and neither is one that does not parse.</li>
 * <li>Build environment names: credential-pattern variable names, {@code DBUS_SESSION_BUS_ADDRESS},
 * {@code XDG_RUNTIME_DIR} and {@code MVND_DAEMON_STORAGE} are refused (job-runtime/02-confinement-and-environment.adoc,
 * "Job Environment"; cli-and-security/02-credentials-and-operator.adoc). A credential variable is a name that ends with
 * {@code _TOKEN}, {@code _PASSWORD}, {@code _SECRET} or {@code _KEY}, the patterns the specification lists; its list
 * ends with an ellipsis, and only the listed patterns are applied.</li>
 * </ol>
 * Not checked, because the specification does not define them concretely: the shell metacharacters of an argument
 * vector, whether the forbidden free flags apply to enrolled templates, and the unique project root
 * ({@code enrolment_root_conflict}, {@code project_store_root_mismatch}).
 *
 * @since 0.1
 */
@UtilityClass
class EnrolmentPolicy {

    /** The one placeholder an argument vector admits, and only inside its module-argument group. */
    static final String MODULE_PLACEHOLDER = "{module}";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^{}]+}");

    private static final List<String> CREDENTIAL_SUFFIXES = List.of("_TOKEN", "_PASSWORD", "_SECRET", "_KEY");

    private static final Set<String> REFUSED_ENVIRONMENT_NAMES =
            Set.of("DBUS_SESSION_BUS_ADDRESS", "XDG_RUNTIME_DIR", "MVND_DAEMON_STORAGE");

    /**
     * @param enrolment the enrolment to sign
     * @param base      the base directory of the runtime
     * @return why the enrolment violates the rules, one reason per violation, in a deterministic order (canonical
     *         templates, discovery templates, providers, write paths, environment names); empty if it complies
     */
    static List<String> violations(ProjectEnrolment enrolment, Path base) {
        Objects.requireNonNull(enrolment, "enrolment");
        var bases = withRealPath(Objects.requireNonNull(base, "base").toAbsolutePath().normalize());
        var violations = new ArrayList<String>();
        templates("build.canonical_templates", enrolment.build().canonicalTemplates(), violations);
        templates("build.discovery_templates", enrolment.build().discoveryTemplates(), violations);
        enrolment.providers().forEach((provider, verification) -> placeholders(
                "providers." + provider.wireName() + ".verify_argv", verification.verifyArgv(), violations));
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
                case Argv.Literal literal -> tokens(literal.value()).forEach(token -> violations.add(
                        "%s: placeholder %s outside the module-argument group".formatted(vector, token)));
                case Argv.ModuleGroup group -> group.arguments().stream()
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

    private static Optional<String> writePath(String value, List<Path> bases) {
        Path path;
        try {
            path = Path.of(value);
        } catch (InvalidPathException _) {
            return Optional.empty();
        }
        if (!path.isAbsolute()) {
            return Optional.empty();
        }
        for (var candidate : withRealPath(path.normalize())) {
            for (var base : bases) {
                if (candidate.startsWith(base) || base.startsWith(candidate)) {
                    return Optional.of("write_paths: %s is, lies below or contains the base directory %s"
                            .formatted(value, bases.getFirst()));
                }
            }
        }
        return Optional.empty();
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
