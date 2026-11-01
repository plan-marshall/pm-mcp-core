/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import org.jspecify.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * The {@code job_id} of a job record (PM-EXEC-4, PM-ARCH-7): the prefix of its kind, {@code -} and 16 random
 * lower-case hexadecimal digits, {@code ^[bcglpv]-[0-9a-f]{16}$}.
 * <p>
 * The id names the record {@code <job_id>.json} and the raw log {@code <job_id>.log} beside it in the {@code jobs/} of
 * the store that holds the job. A file name that does not parse as an id is never a job: a listing of {@code jobs/}
 * skips it. A new id is drawn at random; whether it is free is decided by the store under the lock of the scope that
 * owns the job ({@link JobRecordStore#allocate(de.planmarshall.core.service.LockTransaction, Kind, JobRecord.Scope)}).
 * <p>
 * This type owns the full kind vocabulary of the job records, the worker and the probe included. The engine port
 * {@link de.planmarshall.core.spi.JobStarter} starts only four of the kinds and keeps its own narrower vocabulary.
 *
 * @param value the id in its one written form
 * @since 0.1
 */
public record JobId(String value) {

    /** The suffix of the name of a job record. */
    public static final String RECORD_SUFFIX = ".json";

    /** The suffix of the name of a raw job log. */
    public static final String LOG_SUFFIX = ".log";

    private static final Pattern FORM = Pattern.compile("[bcglpv]-[0-9a-f]{16}");
    private static final int DRAWN_BYTES = 8;
    private static final RandomGenerator RANDOM = new SecureRandom();

    /**
     * The kind of a job, named by the first character of its id and by its name in the stores.
     *
     * @since 0.1
     */
    public enum Kind {
        /** A build job, prefix {@code b}. */
        BUILD('b', "build"),
        /** A CI job, prefix {@code c}. */
        CI('c', "ci"),
        /** A git job, prefix {@code g}. */
        GIT('g', "git"),
        /** A project script job, prefix {@code p}. */
        PROJECT_SCRIPT('p', "project_script"),
        /** A worker, a headless harness CLI the supervisor launches, prefix {@code l}. */
        LLM('l', "llm"),
        /** A probe of a program that is not enrolled yet, prefix {@code v}. */
        PROBE('v', "probe");

        private final char prefix;
        private final String wireName;

        Kind(char prefix, String wireName) {
            this.prefix = prefix;
            this.wireName = wireName;
        }

        /** @return the first character of the id of a job of this kind */
        public char prefix() {
            return prefix;
        }

        /** @return the name of the kind in the stores, for example {@code project_script} */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a kind in the stores
         * @return the kind of that name; empty if no kind has it
         */
        public static Optional<Kind> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(kind -> kind.wireName.equals(wireName)).findFirst();
        }

        private static Kind ofPrefix(char prefix) {
            return Arrays.stream(values()).filter(kind -> kind.prefix == prefix).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No job kind has the prefix " + prefix));
        }
    }

    /**
     * @throws NullPointerException     if the value is {@code null}
     * @throws IllegalArgumentException if the value is not a kind prefix, {@code -} and 16 lower-case hexadecimal
     *                                  digits
     */
    public JobId {
        Objects.requireNonNull(value, "value");
        if (!FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a job id (^[bcglpv]-[0-9a-f]{16}$): " + value);
        }
    }

    /**
     * @param value a string that may be a job id
     * @return the id; empty if the string is not one
     */
    public static Optional<JobId> parse(@Nullable String value) {
        return value != null && FORM.matcher(value).matches() ? Optional.of(new JobId(value)) : Optional.empty();
    }

    /**
     * @param fileName the name of a file in {@code jobs/}
     * @return the id whose record the file is; empty if the name is not {@code <job_id>.json}
     */
    public static Optional<JobId> ofRecordFileName(String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        if (!fileName.endsWith(RECORD_SUFFIX)) {
            return Optional.empty();
        }
        return parse(fileName.substring(0, fileName.length() - RECORD_SUFFIX.length()));
    }

    /**
     * @param kind the kind of the job
     * @return a new id with 16 random digits; it is free only once the store checked it
     */
    public static JobId random(Kind kind) {
        return random(kind, RANDOM);
    }

    /**
     * @param kind   the kind of the job
     * @param random the source of the digits
     * @return a new id with 16 digits drawn from the source
     */
    static JobId random(Kind kind, RandomGenerator random) {
        Objects.requireNonNull(kind, "kind");
        var bytes = new byte[DRAWN_BYTES];
        random.nextBytes(bytes);
        return new JobId(kind.prefix() + "-" + HexFormat.of().formatHex(bytes));
    }

    /** @return the kind the first character of the id names */
    public Kind kind() {
        return Kind.ofPrefix(value.charAt(0));
    }

    /** @return the name of the record of this job, {@code <job_id>.json} */
    public String recordFileName() {
        return value + RECORD_SUFFIX;
    }

    /** @return the name of the raw log of this job, {@code <job_id>.log} */
    public String logFileName() {
        return value + LOG_SUFFIX;
    }

    /** @return the id in its one written form */
    @Override
    public String toString() {
        return value;
    }
}
