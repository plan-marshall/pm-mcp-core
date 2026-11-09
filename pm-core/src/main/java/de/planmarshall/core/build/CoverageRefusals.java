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
}
