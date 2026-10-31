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

import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.StrictJson;

/**
 * The launch report of {@code pm-exec} (PM-EXEC-4): the one line holding one JSON object the launcher writes to
 * {@code stderr} right before {@code execve}, so it is the first line of the job log. It names the job PID, which is
 * its process-group id, the process start instant, the confinement applied and the Landlock ABI of the ruleset.
 * <p>
 * Exactly the first line of the log is the report: {@link #read(String)} takes it, and the job's own output is
 * everything after it, byte for byte. A first line of the job's own output that looks like a report stays in the
 * output, because only one line is ever taken. A log whose first line is no report keeps all of its content: the
 * launcher failed before {@code execve}, and with its exit code {@code 64} (usage), {@code 126} (confinement could not
 * be applied) or {@code 127} ({@code execve} failed) that is a launcher failure ({@link #isLauncherFailure(Reading,
 * int)}), whose fate the outcome derivation takes from the exit code.
 *
 * @param pid              the PID of the job, equal to its process-group id
 * @param processStartedAt the start instant of the process
 * @param confinement      the confinement applied to the job
 * @param landlockAbi      the Landlock ABI version of the applied ruleset; {@code null} when none was applied
 * @since 0.1
 */
public record LaunchReport(long pid, Instant processStartedAt, Confinement confinement,
@Nullable Integer landlockAbi) {

    /** The exit codes of the launcher itself: usage, confinement not applied, {@code execve} failed. */
    public static final Set<Integer> LAUNCHER_FAILURE_EXITS = Set.of(64, 126, 127);

    /** The lowest Landlock ABI a ruleset is recorded with; ABI 1 is the confinement {@code partial(abi=1)}. */
    private static final int MIN_LANDLOCK_ABI = 2;

    private static final String PID_MEMBER = "pid";
    private static final String PROCESS_STARTED_AT = "process_started_at";
    private static final String CONFINEMENT_MEMBER = "confinement";
    private static final String LANDLOCK_ABI = "landlock_abi";

    /**
     * @throws NullPointerException     if the start instant or the confinement is {@code null}
     * @throws IllegalArgumentException if the PID is less than 1
     */
    public LaunchReport {
        if (pid < 1) {
            throw new IllegalArgumentException("pid must be at least 1: " + pid);
        }
        Objects.requireNonNull(processStartedAt, "processStartedAt");
        Objects.requireNonNull(confinement, CONFINEMENT_MEMBER);
    }

    /**
     * What reading a job log for its launch report found.
     *
     * @since 0.1
     */
    public sealed interface Reading permits Reported, Unreported {

        /** @return the output of the job: the log after the report, or the whole log when there is no report */
        String jobOutput();
    }

    /**
     * The first line of the log was the report; it is stripped from the job's output.
     *
     * @param report    the report
     * @param jobOutput the log after the first line
     * @since 0.1
     */
    public record Reported(LaunchReport report, String jobOutput) implements Reading {

        /** @throws NullPointerException if a component is {@code null} */
        public Reported {
            Objects.requireNonNull(report, "report");
            Objects.requireNonNull(jobOutput, "jobOutput");
        }
    }

    /**
     * The log carries no report: it is empty, or its first line does not parse as one. Nothing is stripped.
     *
     * @param detail    why there is no report
     * @param jobOutput the whole log
     * @since 0.1
     */
    public record Unreported(String detail, String jobOutput) implements Reading {

        /** @throws NullPointerException if a component is {@code null} */
        public Unreported {
            Objects.requireNonNull(detail, "detail");
            Objects.requireNonNull(jobOutput, "jobOutput");
        }
    }

    /**
     * Takes the first line of a job log as the launch report.
     *
     * @param log the job log as written, launch report included
     * @return the report and the job's output after it, or why there is no report and the whole log
     */
    public static Reading read(String log) {
        Objects.requireNonNull(log, "log");
        var end = log.indexOf('\n');
        if (end < 0) {
            return new Unreported("the log holds no complete first line", log);
        }
        var firstLine = log.substring(0, end);
        try {
            return new Reported(parse(firstLine), log.substring(end + 1));
        } catch (StrictJson.Malformed | IllegalArgumentException e) {
            return new Unreported("the first line is no launch report: " + e.getMessage(), log);
        } catch (IOException e) {
            return new Unreported("the first line is no JSON object: " + e.getMessage(), log);
        }
    }

    /**
     * @param reading  what reading the job log found
     * @param exitCode the exit code of the job's process
     * @return whether the launcher failed before {@code execve}: no report, and one of the launcher's own exit codes
     */
    public static boolean isLauncherFailure(Reading reading, int exitCode) {
        return Objects.requireNonNull(reading, "reading") instanceof Unreported
                && LAUNCHER_FAILURE_EXITS.contains(exitCode);
    }

    private static LaunchReport parse(String line) throws IOException {
        @Nullable Long pid = null;
        @Nullable Instant startedAt = null;
        @Nullable Confinement confinement = null;
        @Nullable Integer landlockAbi = null;
        var seen = new HashSet<String>();
        try (var parser = StrictJson.factory().createParser(line)) {
            parser.nextToken();
            StrictJson.requireObject(parser, "launch report");
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = StrictJson.nextField(parser, seen);
                switch (field) {
                    case PID_MEMBER -> pid = StrictJson.integer(parser, field);
                    case PROCESS_STARTED_AT -> startedAt = instant(StrictJson.string(parser, field));
                    case CONFINEMENT_MEMBER -> confinement = confinement(StrictJson.string(parser, field));
                    case LANDLOCK_ABI -> landlockAbi = parser.currentToken() == JsonToken.VALUE_NULL ? null
                            : abi(StrictJson.integer(parser, field));
                    default -> throw StrictJson.undeclared(field);
                }
            }
            if (parser.nextToken() != null) {
                throw StrictJson.unreadable("content after the launch report");
            }
        }
        StrictJson.requireFields(seen, "launch report", PID_MEMBER, PROCESS_STARTED_AT, CONFINEMENT_MEMBER,
                LANDLOCK_ABI);
        return new LaunchReport(pid, startedAt, confinement, landlockAbi);
    }

    private static Instant instant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeException e) {
            throw StrictJson.unreadable("'%s' is not an instant: %s".formatted(PROCESS_STARTED_AT, text), e);
        }
    }

    private static int abi(long value) {
        if (value < MIN_LANDLOCK_ABI || value > Integer.MAX_VALUE) {
            throw StrictJson.unreadable("'%s' is no ABI version of an applied ruleset: %d".formatted(LANDLOCK_ABI,
                    value));
        }
        return (int) value;
    }

    private static Confinement confinement(String wireName) {
        return Confinement.fromWireName(wireName).orElseThrow(() -> StrictJson.unreadable(
                "'%s' has the undeclared value '%s'".formatted(CONFINEMENT_MEMBER, wireName)));
    }
}
