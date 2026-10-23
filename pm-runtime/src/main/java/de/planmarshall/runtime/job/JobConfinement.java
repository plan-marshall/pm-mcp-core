/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * The filesystem confinement a job is launched with (PM-SEC-5): the write set, the extra read paths and the paths
 * excluded from the read set, as the {@code pm-exec} launcher takes them.
 * <p>
 * The launcher grants read and execute access to the whole filesystem except every {@code --deny-read} path, and write
 * access only to the {@code --write} paths. Every {@code --read} path is granted in addition to the enumerated read set;
 * it is how the runtime names the job-private directory of a worker or a probe and the runtime socket of a worker,
 * which lie inside the denied base directory.
 * <p>
 * The launcher applies the sets on Linux with Landlock and, where the platform provides it, with its own sandbox
 * profile; on a platform without a ruleset it records {@code confinement: unavailable}. This type only composes the
 * argument vector and never claims the ruleset was applied.
 * <p>
 * Specification: {@code doc/specification/job-runtime/02-confinement-and-environment.adoc} (Linux Filesystem
 * Confinement, Read set, Build Daemons, Container Engine, Session Bus, Probe Jobs).
 *
 * @param writePaths  the write set; absolute, normalized, sorted, without duplicates
 * @param readPaths   the paths granted read access in addition to the enumerated read set
 * @param deniedPaths the paths excluded from the read set
 * @since 0.1
 */
public record JobConfinement(List<Path> writePaths, List<Path> readPaths, List<Path> deniedPaths) {

    /** The option adding a path to the write set, as {@code pm-exec} parses it. */
    public static final String WRITE = "--write";

    /** The option adding a path to the read set, as {@code pm-exec} parses it. */
    public static final String READ = "--read";

    /** The option excluding a path from the read set, as {@code pm-exec} parses it. */
    public static final String DENY_READ = "--deny-read";

    /** The end of the options of {@code pm-exec}. */
    public static final String END_OF_OPTIONS = "--";

    /**
     * @throws NullPointerException if a list or an element is {@code null}
     */
    public JobConfinement {
        writePaths = sorted(writePaths, "writePaths");
        readPaths = sorted(readPaths, "readPaths");
        deniedPaths = sorted(deniedPaths, "deniedPaths");
    }

    /** @return a confinement that grants nothing and denies nothing */
    public static JobConfinement none() {
        return new JobConfinement(List.of(), List.of(), List.of());
    }

    /**
     * The argument vector the launcher takes before its {@code --} separator: every option followed by its path. The
     * order is stable so a launch is reproducible.
     *
     * @return the options and their paths
     */
    public List<String> launcherArguments() {
        var arguments = new ArrayList<String>(
                (writePaths.size() + readPaths.size() + deniedPaths.size()) * 2);
        append(arguments, WRITE, writePaths);
        append(arguments, READ, readPaths);
        append(arguments, DENY_READ, deniedPaths);
        return List.copyOf(arguments);
    }

    private static void append(List<String> arguments, String option, List<Path> paths) {
        for (var path : paths) {
            arguments.add(option);
            arguments.add(path.toString());
        }
    }

    private static List<Path> sorted(List<Path> paths, String name) {
        Objects.requireNonNull(paths, name);
        var unique = new LinkedHashSet<Path>();
        for (var path : paths) {
            unique.add(Objects.requireNonNull(path, name).toAbsolutePath().normalize());
        }
        var sorted = new ArrayList<>(unique);
        sorted.sort(Comparator.comparing(Path::toString));
        return List.copyOf(sorted);
    }
}
