/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import de.planmarshall.core.profile.VerificationRecord;

/**
 * The verifying-binary control of the runtime check on load (PM-CLIENT-5, Profile Verification): it compares the
 * version and commit of the installed binary with the {@code tool} the verification record binds, so a record made by
 * another build of the tool is not believed.
 * <p>
 * The verifier runs the installed binary with a fixed argument vector, never with an argument taken from the record:
 * the record is untrusted input, and an argv it supplied would be a command the record chose. The output is read as
 * the version and the commit, and only an exact match of both is accepted. A binary that answers nothing, or answers
 * with fewer than two fields, is unreadable and refuses the record.
 *
 * @since 0.1
 */
public final class VerifyingBinaryCheck {

    /** The fixed argument vector the installed binary is asked for its identity with. */
    public static final List<String> FIXED_ARGV = List.of("--version");

    /** The bound on the installed binary's answer. */
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * Reads the identity the installed binary reports for the fixed argument vector.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface IdentitySource {

        /**
         * @param executable the installed binary
         * @param argv       the fixed argument vector
         * @return the identity the binary reported
         * @throws IOException if the binary could not be run or reported no identity
         */
        Identity read(Path executable, List<String> argv) throws IOException;
    }

    /**
     * The identity an installed binary reports.
     *
     * @param version the version
     * @param commit  the commit
     * @since 0.1
     */
    public record Identity(String version, String commit) {

        /** @throws NullPointerException if a value is {@code null} */
        public Identity {
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(commit, "commit");
        }
    }

    /** The closed verdict of a verifying-binary check. */
    public sealed interface Verdict permits Verdict.Match, Verdict.Mismatch, Verdict.Unreadable {

        /** The installed binary is the build the record binds. */
        record Match(Identity installed) implements Verdict {
        }

        /**
         * The installed binary is another build than the record binds.
         *
         * @param installed what the installed binary reported
         * @param recorded  what the record binds
         */
        record Mismatch(Identity installed, VerificationRecord.Tool recorded) implements Verdict {
        }

        /**
         * The installed binary could not be read.
         *
         * @param detail why no identity was read
         */
        record Unreadable(String detail) implements Verdict {
        }
    }

    private final Path executable;

    private final IdentitySource source;

    private VerifyingBinaryCheck(Path executable, IdentitySource source) {
        this.executable = executable;
        this.source = source;
    }

    /**
     * @param executable the installed binary
     * @return the check over the binary, reading its identity by running the fixed argument vector
     * @throws NullPointerException if the executable is {@code null}
     */
    public static VerifyingBinaryCheck of(Path executable) {
        return of(executable, VerifyingBinaryCheck::run);
    }

    /**
     * @param executable the installed binary
     * @param source     the source of the installed identity
     * @return the check over the binary
     * @throws NullPointerException if a value is {@code null}
     */
    public static VerifyingBinaryCheck of(Path executable, IdentitySource source) {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(source, "source");
        return new VerifyingBinaryCheck(executable, source);
    }

    /**
     * @param recorded the tool the verification record binds
     * @return the verdict over the installed binary
     * @throws NullPointerException if the recorded tool is {@code null}
     */
    public Verdict verify(VerificationRecord.Tool recorded) {
        Objects.requireNonNull(recorded, "recorded");
        Identity installed;
        try {
            installed = source.read(executable, FIXED_ARGV);
        } catch (IOException e) {
            return new Verdict.Unreadable(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        if (installed.version().equals(recorded.version()) && installed.commit().equals(recorded.commit())) {
            return new Verdict.Match(installed);
        }
        return new Verdict.Mismatch(installed, recorded);
    }

    private static Identity run(Path executable, List<String> argv) throws IOException {
        var command = new ArrayList<String>();
        command.add(executable.toString());
        command.addAll(argv);
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("the binary did not answer within " + TIMEOUT);
            }
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            var fields = output.split("\\s+");
            if (fields.length < 2) {
                throw new IOException("the binary reported no version and commit: '" + output + "'");
            }
            return new Identity(fields[0], fields[1]);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while reading the binary's identity", e);
        }
    }
}
