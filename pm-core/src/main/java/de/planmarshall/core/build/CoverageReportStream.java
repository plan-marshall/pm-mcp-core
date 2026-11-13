/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import de.planmarshall.core.config.LimitProperties;

/**
 * The bounded read of a coverage report file: a stream that counts the bytes it hands out and refuses to go past a cap.
 * <p>
 * A coverage report is untrusted input written by the operator's own build, and the parsers stream it rather than read it
 * whole. This stream is what bounds that read: once more than the cap has been read, the next read throws an
 * {@link IOException} whose message names the cap, so the parser refuses the report instead of reading on. A report at or
 * below the cap is read unchanged.
 * <p>
 * The cap ({@link #MAX_BYTES}) and the longest LCOV line ({@link #MAX_LINE_LENGTH}) are data of {@link #LIMITS}, read when
 * the class initializes and never defaulted, so only editing that resource changes them. The line limit is read as an
 * {@code int} through {@link LimitProperties#readInt(String, String)}, which refuses a value outside that range by name.
 *
 * @since 0.1
 */
final class CoverageReportStream extends FilterInputStream {

    /** The classpath location of the limits of a coverage report's read. */
    static final String LIMITS = "/de/planmarshall/core/build/coverage-limits.properties";

    /** The key of the most bytes of a coverage report that are read. */
    static final String MAX_BYTES_KEY = "pm.core.build.coverage-max-bytes";

    /** The key of the most characters of one line of an LCOV report. */
    static final String MAX_LINE_LENGTH_KEY = "pm.core.build.coverage-max-line-length";

    /**
     * The most bytes a coverage report may hold; a longer report is refused.
     * <p>
     * Read from {@link #LIMITS} when the class initializes, so the value is changed by editing that resource.
     */
    static final long MAX_BYTES = LimitProperties.read(LIMITS, MAX_BYTES_KEY);

    /**
     * The most characters one line of an LCOV report may hold; a longer line is refused.
     * <p>
     * Read from {@link #LIMITS} when the class initializes, so the value is changed by editing that resource.
     */
    static final int MAX_LINE_LENGTH = LimitProperties.readInt(LIMITS, MAX_LINE_LENGTH_KEY);

    private final long limit;

    private long count;

    /**
     * Bounds the given stream by {@link #MAX_BYTES}.
     *
     * @param source the stream of the report file
     */
    CoverageReportStream(InputStream source) {
        this(source, MAX_BYTES);
    }

    /**
     * Bounds the given stream by the given number of bytes; a test passes a small bound.
     *
     * @param source the stream of the report file
     * @param limit  the most bytes that may be read
     */
    CoverageReportStream(InputStream source, long limit) {
        super(source);
        this.limit = limit;
    }

    @Override
    public int read() throws IOException {
        refuseIfPastLimit();
        int value = super.read();
        if (value >= 0) {
            counted(1);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        refuseIfPastLimit();
        int read = super.read(buffer, offset, (int) Math.min(length, room()));
        if (read > 0) {
            counted(read);
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        refuseIfPastLimit();
        long skipped = super.skip(Math.min(n, room()));
        if (skipped > 0) {
            counted(skipped);
        }
        return skipped;
    }

    /**
     * Refuses a mark, so a reset can never make the count run behind the bytes read.
     *
     * @return {@code false}
     */
    @Override
    public boolean markSupported() {
        return false;
    }

    /**
     * Refuses a reset, because the stream supports no mark.
     *
     * @throws IOException always
     */
    @Override
    public void reset() throws IOException {
        throw new IOException("mark/reset is not supported");
    }

    /**
     * Returns how many bytes one read may take: the bytes left below the cap and one more, so a report that is longer
     * than the cap is detected by the read that crosses it and never read further. The one more saturates at
     * {@link Long#MAX_VALUE}, so a cap at the top of the range yields the largest read rather than a negative one.
     *
     * @return the bytes one read may take, at least one
     */
    private long room() {
        long left = limit - count;
        return left == Long.MAX_VALUE ? left : left + 1;
    }

    private void counted(long bytes) throws IOException {
        count += bytes;
        refuseIfPastLimit();
    }

    private void refuseIfPastLimit() throws IOException {
        if (count > limit) {
            throw new IOException("the report is larger than the limit of " + limit + " bytes");
        }
    }
}
