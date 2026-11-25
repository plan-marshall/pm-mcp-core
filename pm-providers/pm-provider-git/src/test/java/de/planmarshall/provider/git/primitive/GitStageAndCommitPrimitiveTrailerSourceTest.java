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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.provider.git.primitive.CommitTrailer.TrailerSource;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Outcome;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Params;
import de.planmarshall.provider.git.testing.generators.GitGenerators;

/**
 * {@code git.stage-and-commit}: the provenance of the resolved trailer on {@code committed} and on no other outcome.
 */
@EnableGeneratorController
@DisplayName("GitStageAndCommitPrimitive: trailer source")
class GitStageAndCommitPrimitiveTrailerSourceTest extends GitStageAndCommitPrimitiveFixtures {

    @Test
    @DisplayName("a local trailer is reported configured for both halves")
    void localTrailerIsConfigured() throws Exception {
        var trailer = GitGenerators.trailers().next();

        var result = commitWithTrailers(Optional.of(new CommitTrailer.Stored(trailer.name(), trailer.email())),
                Optional.empty());

        assertEquals(sourceFact(TrailerSource.CONFIGURED, TrailerSource.CONFIGURED), trailerSource(result),
                "both halves come from the stored local value");
    }

    @Test
    @DisplayName("a machine trailer is reported configured when the project stores none")
    void machineTrailerIsConfigured() throws Exception {
        var trailer = GitGenerators.trailers().next();

        var result = commitWithTrailers(Optional.empty(),
                Optional.of(new CommitTrailer.Stored(trailer.name(), trailer.email())));

        assertEquals(sourceFact(TrailerSource.CONFIGURED, TrailerSource.CONFIGURED), trailerSource(result),
                "both halves come from the stored machine value");
    }

    @Test
    @DisplayName("no stored trailer is reported default for both halves")
    void noStoredTrailerIsDefault() throws Exception {
        var result = commitWithTrailers(Optional.empty(), Optional.empty());

        assertEquals(sourceFact(TrailerSource.DEFAULT, TrailerSource.DEFAULT), trailerSource(result),
                "both halves come from the compiled default");
    }

    @Test
    @DisplayName("a refused stored name is invalid_ignored beside a configured email")
    void refusedNameIsInvalidIgnored() throws Exception {
        var trailer = GitGenerators.trailers().next();
        // an angle bracket breaks the constraints of commit_trailer.name
        var refusedName = trailer.name() + "<";

        var result = commitWithTrailers(Optional.of(new CommitTrailer.Stored(refusedName, trailer.email())),
                Optional.empty());

        assertEquals(sourceFact(TrailerSource.INVALID_IGNORED, TrailerSource.CONFIGURED), trailerSource(result),
                "the refused name half is invalid_ignored");
    }

    @Test
    @DisplayName("the trailer source names only sources, never a value of the trailer")
    void sourceCarriesNoTrailerValue() throws Exception {
        var trailer = GitGenerators.trailers().next();

        var result = commitWithTrailers(Optional.of(new CommitTrailer.Stored(trailer.name(), trailer.email())),
                Optional.empty());

        var source = trailerSource(result);
        assertAll("not rendered",
                () -> assertFalse(source.containsValue(trailer.name()), "the trailer name is not in the fact"),
                () -> assertFalse(source.containsValue(trailer.email()), "the trailer email is not in the fact"),
                () -> assertEquals(Set.of(GitStageAndCommitPrimitive.COMMIT_FACT,
                                GitStageAndCommitPrimitive.TRAILER_SOURCE_FACT), result.updatedFacts().keySet(),
                        "committed carries the commit and the source"));
    }

    @Test
    @DisplayName("an outcome other than committed carries no trailer source")
    void absentWithoutCommit() throws Exception {
        var dirty = file();
        write(dirty);
        stub(ScopeType.PLAN, false, List.of(dirty), Optional.of(agent));

        var nothing = run(new GitStageAndCommitPrimitive(untouchedGit(), site),
                new Params(messages.next(), List.of(file())));
        var refused = new GitStageAndCommitPrimitive(untouchedGit(), site).execute(context,
                new Params(": " + names.next(), null));

        var key = GitStageAndCommitPrimitive.TRAILER_SOURCE_FACT;
        assertAll("absent trailer source",
                () -> assertEquals(Outcome.NOTHING_TO_COMMIT, nothing.outcome(), "the first run commits nothing"),
                () -> assertFalse(nothing.updatedFacts().containsKey(key),
                        "nothing_to_commit carries no trailer source"),
                () -> assertEquals(GitStageAndCommitPrimitive.Reason.MESSAGE_REFUSED, refused.reason(),
                        "the second run refuses the message"),
                () -> assertFalse(refused.updatedFacts().containsKey(key),
                        "message_refused carries no trailer source"));
    }

    private PrimitiveResult<Outcome> commitWithTrailers(Optional<CommitTrailer.Stored> local,
            Optional<CommitTrailer.Stored> machine) throws IOException {
        var dirty = file();
        write(dirty);
        expectScope(ScopeType.PLAN, false, List.of(dirty));
        expect(site.commitIdentity(context)).andReturn(Optional.of(operator)).anyTimes();
        expect(site.agentIdentity(context)).andReturn(Optional.of(agent)).anyTimes();
        expect(site.localTrailer(context)).andReturn(local).anyTimes();
        expect(site.machineTrailer(context)).andReturn(machine).anyTimes();
        var result = run(new GitStageAndCommitPrimitive(git, site), new Params(messages.next(), null));
        committed(result);
        return result;
    }

    private static Map<?, ?> trailerSource(PrimitiveResult<Outcome> result) {
        return assertInstanceOf(Map.class, result.updatedFacts().get(GitStageAndCommitPrimitive.TRAILER_SOURCE_FACT),
                "committed carries the trailer source map");
    }

    private static Map<String, String> sourceFact(TrailerSource name, TrailerSource email) {
        return Map.of(CommitTrailer.Resolution.NAME_HALF, name.wireName(), CommitTrailer.Resolution.EMAIL_HALF,
                email.wireName());
    }
}
