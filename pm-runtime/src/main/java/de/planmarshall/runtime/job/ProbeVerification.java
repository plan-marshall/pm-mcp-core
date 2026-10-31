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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;

import de.planmarshall.runtime.job.JobRefusal.Reason;
import de.planmarshall.runtime.job.ProbeCandidate.CheckoutRoot;
import de.planmarshall.runtime.job.ProbeCandidate.DigestedFile;
import de.planmarshall.runtime.job.ProbeCandidate.JobPrivate;

/**
 * Step 0 of a probe job (PM-EXEC-1, PM-EXEC-3): the verification of a probe against its {@link ProbeCandidate
 * candidate record}, which replaces the enrolment check of the verify-not-resolve pipeline for a program that is not
 * enrolled yet. Its checks run in this order, and the first that refuses decides:
 * <ol>
 * <li>the digests: the executable and, for a script, its interpreter must have the SHA-256 the candidate record names
 * ({@code executable_digest_mismatch} otherwise); a file that is missing, a symbolic link or no regular file never
 * matches;</li>
 * <li>the working directory: for a probe of a checkout the canonical checkout root, which is a git top-level, and for
 * every other probe its own job-private directory {@code cwd/}; a directory that resolves elsewhere, a symbolic link
 * included, is refused ({@code path_traversal_rejected});</li>
 * <li>the argument vector: exactly the declared one ({@code argument_rejected} otherwise).</li>
 * </ol>
 * The consent of the operator to probes of the kind is a precondition of the caller: a probe without it is never
 * verified and never started. Nothing is resolved at launch: the verified vector starts with the absolute path of the
 * interpreter, or of the executable, as the candidate record names it.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Verify-Not-Resolve Execution
 * Pipeline, Probe Jobs).
 *
 * @since 0.1
 */
@UtilityClass
public class ProbeVerification {

    /** The directory whose presence makes a directory a git top-level: {@code .git}, a directory or a file. */
    static final String GIT = ".git";

    /**
     * The outcome of the verification of a probe.
     *
     * @since 0.1
     */
    public sealed interface Result permits Verified, Refused {
    }

    /**
     * The probe passed; it is launched exactly as verified.
     *
     * @param workingDirectory the canonical working directory
     * @param argv             the verified argument vector, its first element an absolute path of the candidate record
     * @since 0.1
     */
    public record Verified(Path workingDirectory, List<String> argv) implements Result {

        /** @throws NullPointerException if a component or an argument is {@code null} */
        public Verified {
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            argv = List.copyOf(Objects.requireNonNull(argv, "argv"));
        }
    }

    /**
     * The probe is refused; nothing is launched.
     *
     * @param refusal why
     * @since 0.1
     */
    public record Refused(JobRefusal refusal) implements Result {

        /** @throws NullPointerException if the refusal is {@code null} */
        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }

    /**
     * Verifies a probe against its candidate record.
     *
     * @param candidate           the candidate record
     * @param arguments           the arguments the probe is asked to run with, after the executable
     * @param workingDirectory    the working directory the probe is asked to run in
     * @param jobPrivateDirectory the directory {@code cwd/} of the probe's job-private directory, the working directory
     *                            of a probe that does not run in a checkout
     * @return the verified probe, or the refusal of the first check that refused it
     * @throws UncheckedIOException if a file of the candidate record exists and cannot be read
     */
    public static Result verify(ProbeCandidate candidate, List<String> arguments, Path workingDirectory,
            Path jobPrivateDirectory) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(jobPrivateDirectory, "jobPrivateDirectory");

        if (!matches(candidate.executable())) {
            return refused(Reason.EXECUTABLE_DIGEST_MISMATCH,
                    "the executable does not match the digest of its candidate record");
        }
        if (candidate.interpreter().isPresent() && !matches(candidate.interpreter().get())) {
            return refused(Reason.EXECUTABLE_DIGEST_MISMATCH,
                    "the interpreter does not match the digest of its candidate record");
        }

        var expected = switch (candidate.workingDirectory()) {
            case CheckoutRoot(var root) -> root;
            case JobPrivate _ -> jobPrivateDirectory;
        };
        var canonical = canonical(workingDirectory);
        if (canonical.isEmpty() || !canonical.equals(canonical(expected))
                || !canonical.get().equals(workingDirectory.toAbsolutePath().normalize())) {
            return refused(Reason.PATH_TRAVERSAL_REJECTED,
                    "the working directory is not the canonical working directory of the probe");
        }
        if (candidate.workingDirectory() instanceof CheckoutRoot
                && !Files.exists(canonical.get().resolve(GIT), LinkOption.NOFOLLOW_LINKS)) {
            return refused(Reason.PATH_TRAVERSAL_REJECTED, "the checkout root is no git top-level");
        }

        if (!candidate.arguments().equals(arguments)) {
            return refused(Reason.ARGUMENT_REJECTED, "the arguments are not the declared arguments of the probe");
        }
        return new Verified(canonical.get(), candidate.declaredArgv());
    }

    private static boolean matches(DigestedFile file) {
        return FileDigest.of(file.path()).matches(file.sha256());
    }

    /** @return the canonical path of a directory; empty if it does not exist or is no directory */
    private static Optional<Path> canonical(Path directory) {
        try {
            var real = directory.toRealPath();
            return Files.isDirectory(real) ? Optional.of(real) : Optional.empty();
        } catch (IOException _) {
            return Optional.empty();
        }
    }

    private static Refused refused(Reason reason, String detail) {
        return new Refused(JobRefusal.of(reason, detail));
    }
}
