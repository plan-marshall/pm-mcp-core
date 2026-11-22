/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.testing.generators;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.domain.EmailGenerator;

import de.planmarshall.provider.git.primitive.CommitTrailer;

/**
 * Generates a {@link CommitTrailer} within the constraints of the stored key {@code commit_trailer}: a name of one or
 * two words of letters, at most 81 characters, and an email of the form {@code local@domain}.
 *
 * @since 0.1
 */
public final class CommitTrailerGenerator implements TypedGenerator<CommitTrailer> {

    private final TypedGenerator<String> words = Generators.letterStrings(1, 40);

    private final TypedGenerator<Boolean> twoWords = Generators.booleans();

    private final TypedGenerator<String> emails = new EmailGenerator();

    @Override
    public CommitTrailer next() {
        var name = Boolean.TRUE.equals(twoWords.next()) ? words.next() + " " + words.next() : words.next();
        return new CommitTrailer(name, emails.next());
    }

    @Override
    public Class<CommitTrailer> getType() {
        return CommitTrailer.class;
    }
}
