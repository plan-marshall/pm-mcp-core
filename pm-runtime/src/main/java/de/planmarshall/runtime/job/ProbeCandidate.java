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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import de.planmarshall.core.store.JobRecord.Scope;

/**
 * The candidate record of a probe (PM-EXEC-1, PM-EXEC-3): what a scan of the machine or the detection of a project's
 * onboarding wrote about a program it found and did not enrol yet, and what a probe of it may run. A probe job is
 * verified against this record instead of an enrolment ({@link ProbeVerification}).
 * <p>
 * The record names the executable by its absolute path and SHA-256, and for a script the interpreter that runs it
 * likewise; the kind of the probe, which decides its fixed budget ({@link ProbeBudget}); the declared argument vector
 * after the executable; the exit codes the probe declares as its success; the scope that owns the probe job, the
 * machine or an onboarding entry; and the working directory, the checkout root of the onboarding or the job-private
 * directory of the probe.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Verify-Not-Resolve Execution
 * Pipeline, Probe Jobs); {@code doc/specification/job-runtime/02-confinement-and-environment.adoc} (Probe Jobs).
 *
 * @param kind             the kind of the probe
 * @param executable       the executable the probe runs
 * @param interpreter      the interpreter that runs the executable, for a script; empty otherwise
 * @param arguments        the declared arguments after the executable
 * @param successExitCodes the exit codes the probe declares as its success; not empty
 * @param scope            the scope that owns the probe job: the machine, or an onboarding entry
 * @param workingDirectory where the probe runs
 * @since 0.1
 */
public record ProbeCandidate(Kind kind, DigestedFile executable, Optional<DigestedFile> interpreter,
List<String> arguments, Set<Integer> successExitCodes, Scope scope, WorkingDirectory workingDirectory) {

    /**
     * The kind of a probe, which names its budget key {@code probe:<kind>}.
     *
     * @since 0.1
     */
    public enum Kind {
        /** The version probe of a harness or a host tool. */
        VERSION("version"),
        /** The models probe of a harness. */
        MODELS("models"),
        /** The login probe of a harness. */
        LOGIN("login"),
        /** A script of a checkout in a project's onboarding. */
        SCRIPT("script");

        private final String wireName;

        Kind(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the kind in a budget key */
        public String wireName() {
            return wireName;
        }

        /** @return the budget key of a probe of this kind, {@code probe:<kind>} */
        public String budgetKey() {
            return "probe:" + wireName;
        }

        /**
         * @param wireName the name of a kind
         * @return the kind of that name; empty if no kind has it
         */
        public static Optional<Kind> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(kind -> kind.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * A file named by its absolute path and its SHA-256.
     *
     * @param path   the absolute path of the file
     * @param sha256 its SHA-256, lower-case hexadecimal
     * @since 0.1
     */
    public record DigestedFile(Path path, String sha256) {

        private static final Pattern SHA256_PATTERN =Pattern.compile("[0-9a-f]{64}");

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the path is not absolute or the digest is no lower-case SHA-256
         */
        public DigestedFile {
            Objects.requireNonNull(path, "path");
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException("A probed file is named by its absolute path: " + path);
            }
            Objects.requireNonNull(sha256, "sha256");
            if (!SHA256_PATTERN.matcher(sha256).matches()) {
                throw new IllegalArgumentException("Not a lower-case SHA-256: " + sha256);
            }
        }
    }

    /**
     * Where a probe runs.
     *
     * @since 0.1
     */
    public sealed interface WorkingDirectory permits CheckoutRoot, JobPrivate {
    }

    /**
     * The checkout root of a project's onboarding, which must be a canonical git top-level.
     *
     * @param root the checkout root
     * @since 0.1
     */
    public record CheckoutRoot(Path root) implements WorkingDirectory {

        /**
         * @throws NullPointerException     if the root is {@code null}
         * @throws IllegalArgumentException if the root is not absolute
         */
        public CheckoutRoot {
            Objects.requireNonNull(root, "root");
            if (!root.isAbsolute()) {
                throw new IllegalArgumentException("A checkout root is absolute: " + root);
            }
        }
    }

    /**
     * The empty subdirectory {@code cwd/} of the probe's job-private directory {@code <base>/run/jobs/<job_id>/}, for
     * a probe of a harness or a tool found on the machine.
     *
     * @since 0.1
     */
    public record JobPrivate() implements WorkingDirectory {
    }

    /**
     * @throws NullPointerException     if a component, an argument or an exit code is {@code null}
     * @throws IllegalArgumentException if no success exit code is declared, the scope is neither the machine nor an
     *                                  onboarding entry, or a checkout root is named for a probe of the machine
     */
    public ProbeCandidate {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(interpreter, "interpreter");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        successExitCodes = Set.copyOf(Objects.requireNonNull(successExitCodes, "successExitCodes"));
        if (successExitCodes.isEmpty()) {
            throw new IllegalArgumentException("A probe declares at least one exit code as its success");
        }
        Objects.requireNonNull(scope, "scope");
        if (scope.type() != Scope.Type.MACHINE && scope.type() != Scope.Type.ENTRY) {
            throw new IllegalArgumentException("A probe belongs to the machine or an onboarding entry, not to a "
                    + scope.type().wireName());
        }
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        if (workingDirectory instanceof CheckoutRoot && scope.type() != Scope.Type.ENTRY) {
            throw new IllegalArgumentException("Only a probe of an onboarding entry runs in a checkout root");
        }
    }

    /** @return the argument vector the probe declares: the interpreter, if any, the executable and the arguments */
    public List<String> declaredArgv() {
        var argv = new ArrayList<String>(arguments.size() + 2);
        interpreter.ifPresent(file -> argv.add(file.path().toString()));
        argv.add(executable.path().toString());
        argv.addAll(arguments);
        return List.copyOf(argv);
    }
}
