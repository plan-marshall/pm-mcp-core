/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.spi.validation.ConventionalCommitValidator;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.provider.git.GitOperations.Identity;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Outcome;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Params;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Reason;
import de.planmarshall.provider.git.testing.generators.GitGenerators;

/**
 * {@code git.stage-and-commit}: the trailer, the author and the committer read back from the history, the message
 * refusals and a missing commit identity.
 */
@EnableGeneratorController
@DisplayName("GitStageAndCommitPrimitive: message and identities")
class GitStageAndCommitPrimitiveMessageTest extends GitStageAndCommitPrimitiveFixtures {

    @Test
    @DisplayName("the commit carries exactly one trailer and the operator and agent identities")
    void trailerAuthorCommitter() throws Exception {
        var dirty = file();
        write(dirty);
        var trailer = GitGenerators.trailers().next();
        expectScope(ScopeType.PLAN, false, List.of(dirty));
        expect(site.commitIdentity(context)).andReturn(Optional.of(operator)).anyTimes();
        expect(site.agentIdentity(context)).andReturn(Optional.of(agent)).anyTimes();
        expect(site.localTrailer(context))
                .andReturn(Optional.of(new CommitTrailer.Stored(trailer.name(), trailer.email()))).anyTimes();
        expect(site.machineTrailer(context)).andReturn(Optional.empty()).anyTimes();
        var message = messages.next();

        var info = head(committed(run(new GitStageAndCommitPrimitive(git, site), new Params(message, null))));

        var expected = ((CommitMessages.Appended) CommitMessages.withTrailer(message, trailer)).message();
        assertAll("read back from the history",
                () -> assertEquals(expected, info.message(), "the message carries the resolved trailer"),
                () -> assertEquals(1, trailerLines(info.message()), "the message carries exactly one trailer"),
                () -> assertEquals(operator, info.author(), "the author is the operator's identity"),
                () -> assertEquals(agent, info.committer(), "the committer is the agent identity"));
    }

    @Test
    @DisplayName("without an agent identity the compiled committer and default trailer are used")
    void compiledCommitter() throws Exception {
        var dirty = file();
        write(dirty);
        stub(ScopeType.PLAN, false, List.of(dirty), Optional.empty());

        var info = head(committed(run(new GitStageAndCommitPrimitive(git, site),
                new Params(messages.next(), null))));

        var compiled = CommitTrailer.compiled();
        assertAll("compiled values",
                // the compiled committer of the specification
                () -> assertEquals(new Identity("plan-marshall-mcp", "noreply@cuioss.de"), info.committer(),
                        "the committer is the compiled identity"),
                () -> assertTrue(info.message().endsWith("Co-Authored-By: " + compiled.name() + " <"
                        + compiled.email() + ">\n"), "the trailer is the compiled one"));
    }

    @Test
    @DisplayName("a message with a trailer line is refused naming the rule before any git call")
    void messageWithTrailer() throws Exception {
        var dirty = file();
        write(dirty);
        stub(ScopeType.PLAN, false, List.of(dirty), Optional.of(agent));
        var trailer = GitGenerators.trailers().next();
        var message = messages.next() + "\n\nCo-Authored-By: " + trailer.name() + " <" + trailer.email() + ">";

        var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site), new Params(message, null));

        assertAll("message refused",
                () -> assertEquals(Outcome.FAILED, result.outcome(), "the refused message fails the commit"),
                () -> assertEquals(Reason.MESSAGE_REFUSED, result.reason(), "the reason is message_refused"),
                () -> assertEquals(ConventionalCommitValidator.RULE_CO_AUTHORED_BY,
                        result.updatedFacts().get(GitStageAndCommitPrimitive.RULE_FACT),
                        "the facts name the trailer rule"));
    }

    @Test
    @DisplayName("a message without a type is refused naming the header rule before any git call")
    void messageWithoutType() throws Exception {
        var dirty = file();
        write(dirty);
        stub(ScopeType.PLAN, false, List.of(dirty), Optional.of(agent));

        var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site),
                new Params(": " + names.next(), null));

        assertAll("header refused",
                () -> assertEquals(Reason.MESSAGE_REFUSED, result.reason(), "the reason is message_refused"),
                () -> assertEquals(1, result.updatedFacts().get(GitStageAndCommitPrimitive.LINE_FACT),
                        "the facts name the header line"));
    }

    @Test
    @DisplayName("without a commit identity the commit fails without a git call")
    void noCommitIdentity() throws Exception {
        var dirty = file();
        write(dirty);
        expectScope(ScopeType.PLAN, false, List.of(dirty));
        expect(site.commitIdentity(context)).andReturn(Optional.empty()).anyTimes();
        expectTrailers();

        var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site), new Params(messages.next(), null));

        assertAll("no commit identity",
                () -> assertEquals(Outcome.FAILED, result.outcome(), "a missing identity fails the commit"),
                () -> assertEquals(Reason.COMMIT_FAILED, result.reason(), "the reason is commit_failed"));
    }
}
