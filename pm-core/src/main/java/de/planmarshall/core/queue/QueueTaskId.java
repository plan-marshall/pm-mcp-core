/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * The {@code queue_task_id} of a queue task record (Store Schemas § Queue Task Record): {@code qt-} and 16 random
 * lower-case hexadecimal digits, {@code ^qt-[0-9a-f]{16}$}.
 * <p>
 * The id is the name of the record file {@code queue/<queue_task_id>.json}, and a consultation task's id is its
 * {@code consultation_id}. A file name that does not parse as an id is never a queue task: a listing of the queue
 * skips it. A new id is drawn at random; whether it is free is decided by the {@link QueueStore} under the queue lock.
 *
 * @param value the id in its one written form
 * @since 0.1
 */
public record QueueTaskId(String value) {

    /** The suffix of the name of a queue task record. */
    public static final String RECORD_SUFFIX = ".json";

    private static final Pattern FORM = Pattern.compile("qt-[0-9a-f]{16}");
    private static final int DRAWN_BYTES = 8;
    private static final RandomGenerator RANDOM = new SecureRandom();

    /**
     * @throws NullPointerException     if the value is {@code null}
     * @throws IllegalArgumentException if the value is not {@code qt-} and 16 lower-case hexadecimal digits
     */
    public QueueTaskId {
        Objects.requireNonNull(value, "value");
        if (!FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a queue task id (^qt-[0-9a-f]{16}$): " + value);
        }
    }

    /**
     * @param value a string that may be a queue task id
     * @return the id; empty if the string is not one
     */
    public static Optional<QueueTaskId> parse(String value) {
        return value != null && FORM.matcher(value).matches() ? Optional.of(new QueueTaskId(value))
                : Optional.empty();
    }

    /**
     * @param fileName the name of a file in the queue directory
     * @return the id whose record the file is; empty if the name is not {@code <queue_task_id>.json}
     */
    public static Optional<QueueTaskId> ofRecordFileName(String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        if (!fileName.endsWith(RECORD_SUFFIX)) {
            return Optional.empty();
        }
        return parse(fileName.substring(0, fileName.length() - RECORD_SUFFIX.length()));
    }

    /**
     * @return a new id with 16 random digits; it is free only once the store checked it
     */
    public static QueueTaskId random() {
        return random(RANDOM);
    }

    /**
     * @param random the source of the digits
     * @return a new id with 16 digits drawn from the source
     */
    static QueueTaskId random(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        var bytes = new byte[DRAWN_BYTES];
        random.nextBytes(bytes);
        return new QueueTaskId("qt-" + HexFormat.of().formatHex(bytes));
    }

    /** @return the name of the record of this task, {@code <queue_task_id>.json} */
    public String recordFileName() {
        return value + RECORD_SUFFIX;
    }

    /** @return the id in its one written form */
    @Override
    public String toString() {
        return value;
    }
}
