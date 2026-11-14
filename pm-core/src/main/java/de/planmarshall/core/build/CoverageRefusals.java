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

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;

/**
 * The refusal construction the four coverage parsers share.
 * <p>
 * Every parser answers a report it cannot read with the same shape: a {@link CoverageParseResult.Refused} whose reason
 * opens with the format's identifier, so a caller that reads only the reason still knows which parser refused it. The
 * helper exists once so the four parsers cannot spell the prefix differently.
 * <p>
 * It is also the one seam every refusal passes, so it is where the echo of report content is bounded: the reason keeps
 * the format prefix and at most the first {@value #MAX_DETAIL_LENGTH} characters of the echoed content, a file name is
 * cut before its failure kind is appended so the kind always survives, and the parsers' own echo sites stay as they are.
 *
 * @since 0.1
 */
@UtilityClass
class CoverageRefusals {

    /**
     * The most characters of a refusal's echoed content: a detail is cut to it, and a file name is cut to it before the
     * failure kind is appended, so the reason can carry this many characters plus the kind.
     */
    static final int MAX_DETAIL_LENGTH = 256;

    /**
     * Builds the refusal for a report the parser could not read.
     *
     * @param format the format that was refused
     * @param cause  the failure that ended the read
     * @return the refusal, its reason opening with the format identifier
     */
    static CoverageParseResult.Refused of(CoverageFormat format, Exception cause) {
        String message = cause.getMessage();
        String detail = message == null ? cause.getClass().getSimpleName() : message;
        return refused(format, cut(detail));
    }

    /**
     * Builds the refusal for a report file the parser could not read, naming the file by its file name only.
     * <p>
     * A file-system failure ({@link NoSuchFileException}, {@link AccessDeniedException} and every other
     * {@link FileSystemException}) carries the full path in its message, so its message is never passed through: the
     * reason names the file name and the kind of failure, never the directory. Any other I/O failure — a malformed report,
     * the bound of {@link CoverageReportStream} — is refused with its own message, as {@link #of(CoverageFormat, Exception)}
     * does.
     *
     * @param format     the format that was refused
     * @param reportFile the report file that was read
     * @param cause      the failure that ended the read
     * @return the refusal, its reason opening with the format identifier
     */
    static CoverageParseResult.Refused of(CoverageFormat format, Path reportFile, IOException cause) {
        if (!(cause instanceof FileSystemException failure)) {
            return of(format, cause);
        }
        Path fileName = reportFile.getFileName();
        String name = fileName == null ? "the report file" : fileName.toString();
        return refused(format, cut(name) + ": " + kind(failure));
    }

    /**
     * Builds a refusal whose reason is the format prefix and the given detail, already bounded by {@link #cut(String)}.
     * The detail echoes report content (a type name, a counter value, a file name), so the cut bounds what an untrusted
     * report can write into a reason; a cut never splits a surrogate pair. A file-system failure's name is cut before
     * the failure kind is appended, so a name near the bound never loses the kind.
     *
     * @param format the format that was refused
     * @param detail the bounded detail of the refusal
     * @return the refusal, its reason opening with the format identifier
     */
    private static CoverageParseResult.Refused refused(CoverageFormat format, String detail) {
        return new CoverageParseResult.Refused(format, format.id() + ": " + detail);
    }

    private static String cut(String detail) {
        if (detail.length() <= MAX_DETAIL_LENGTH) {
            return detail;
        }
        int end = Character.isHighSurrogate(detail.charAt(MAX_DETAIL_LENGTH - 1))
                ? MAX_DETAIL_LENGTH - 1 : MAX_DETAIL_LENGTH;
        return detail.substring(0, end);
    }

    private static String kind(FileSystemException failure) {
        if (failure instanceof NoSuchFileException) {
            return "no such file";
        }
        if (failure instanceof AccessDeniedException) {
            return "access denied";
        }
        String reason = failure.getReason();
        return reason == null ? failure.getClass().getSimpleName() : reason;
    }
}
