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

/**
 * Generates a valid Conventional Commit message without a trailer: a header {@code type(scope)!: description} whose
 * scope and {@code !} are optional, and an optional body after exactly one blank line. The message is at most a few
 * hundred characters, well inside the bound of the message type, and holds no colon outside its header, so no body
 * line can read as a trailer.
 *
 * @since 0.1
 */
public final class CommitMessageGenerator implements TypedGenerator<String> {

    private final TypedGenerator<String> types =
            Generators.fixedValues("feat", "fix", "perf", "refactor", "docs", "style", "test", "chore", "ci");

    private final TypedGenerator<String> scopes = Generators.strings("abcdefghijklmnopqrstuvwxyz-", 1, 20);

    private final TypedGenerator<String> words = Generators.letterStrings(1, 12);

    private final TypedGenerator<Integer> wordCounts = Generators.integers(1, 8);

    private final TypedGenerator<Integer> bodyLines = Generators.integers(0, 4);

    private final TypedGenerator<Boolean> flags = Generators.booleans();

    @Override
    public String next() {
        var header = new StringBuilder(types.next());
        if (Boolean.TRUE.equals(flags.next())) {
            header.append('(').append(scopes.next()).append(')');
        }
        if (Boolean.TRUE.equals(flags.next())) {
            header.append('!');
        }
        header.append(": ").append(sentence());
        var lines = bodyLines.next();
        if (lines == 0) {
            return header.toString();
        }
        var message = header.append("\n");
        for (var line = 0; line < lines; line++) {
            message.append('\n').append(sentence());
        }
        return message.toString();
    }

    private String sentence() {
        var sentence = new StringBuilder(words.next());
        var count = wordCounts.next();
        for (var word = 1; word < count; word++) {
            sentence.append(' ').append(words.next());
        }
        return sentence.toString();
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
