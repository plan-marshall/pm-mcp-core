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
 *
 * @since 0.1
 */
@UtilityClass
class CoverageRefusals {

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
        return new CoverageParseResult.Refused(format, format.id() + ": " + detail);
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
        return new CoverageParseResult.Refused(format, format.id() + ": " + name + ": " + kind(failure));
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
