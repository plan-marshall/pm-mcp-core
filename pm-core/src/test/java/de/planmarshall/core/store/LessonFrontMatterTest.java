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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.LessonFrontMatter.Category;
import de.planmarshall.core.store.LessonFrontMatter.Claim;
import de.planmarshall.core.store.LessonFrontMatter.MergedFrom;
import de.planmarshall.core.store.LessonFrontMatter.Remedy;
import de.planmarshall.core.store.LessonFrontMatter.Retired;
import de.planmarshall.core.store.LessonFrontMatter.Revision;
import de.planmarshall.core.store.LessonFrontMatter.Source;
import de.planmarshall.core.store.LessonFrontMatter.Status;

/**
 * The rules the specification states beside the {@code LessonFrontMatter} schema (Store Schemas § Lesson Record;
 * PM-IMPL-2). Every rule has a refusing case and a passing control; the hazard of PM-WATCH-STORE-17 (a lesson edit
 * that orphans its derivation) is the rule that the current revision is the last entry of the history, and the hazard
 * of PM-WATCH-STORE-20 (the supersede and prune semantics of the port) is the retirement carried by the record.
 */
@EnableGeneratorController
@DisplayName("Lesson front matter")
class LessonFrontMatterTest {

    private static Retired retired() {
        return Retired.builder()
                .at(LessonFixtures.instant())
                .by(LessonFixtures.text())
                .verdict(Retired.Verdict.APPLIED)
                .into(null)
                .reason(LessonFixtures.text())
                .coveringClause(null)
                .coveringInput(null)
                .lessonState(Retired.LessonState.FOUND)
                .build();
    }

    private static LessonFrontMatter.LessonFrontMatterBuilder archConstraint() {
        return LessonFixtures.frontMatterBuilder()
                .category(Category.ARCH_CONSTRAINT)
                .rule(LessonFixtures.text())
                .recurrenceCount(2)
                .lastSeen(LessonFixtures.date());
    }

    @Test
    @DisplayName("a wire_check carries its check and no other kind does")
    void remedyCheckIsSetExactlyForWireCheck() {
        var statement = LessonFixtures.statement();
        var text = LessonFixtures.text();
        var refusal = assertThrows(IllegalArgumentException.class,
                () -> new Remedy(Remedy.Kind.WIRE_CHECK, statement, null),
                "A wire_check without a check is refused");
        var withCheck = new Remedy(Remedy.Kind.WIRE_CHECK, statement, text);
        var withoutCheck = new Remedy(Remedy.Kind.CHECK, statement, null);

        assertAll(
                () -> assertTrue(refusal.getMessage().contains(LessonFrontMatter.REMEDY_CHECK_MISMATCH),
                        "The refusal names the detail remedy_check_mismatch"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Remedy(Remedy.Kind.CHECK, statement, text),
                        "A check on the kind check is refused"),
                () -> assertNotNull(withCheck.check(), "A wire_check with its check is taken"),
                () -> assertNull(withoutCheck.check(), "A check without a check is taken"));
    }

    @Test
    @DisplayName("a retirement is set exactly when the status is retired")
    void retirementIsSetExactlyForRetiredStatus() {
        var retiredWithoutRetirement = LessonFixtures.frontMatterBuilder().status(Status.RETIRED).retired(null);
        var retirementWithoutRetired = LessonFixtures.frontMatterBuilder().status(Status.ACTIVE).retired(retired());
        var retiredWithRetirement = LessonFixtures.frontMatterBuilder().status(Status.RETIRED).retired(retired());

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, retiredWithoutRetirement::build,
                        "A retired lesson without its retirement is refused"),
                () -> assertThrows(IllegalArgumentException.class, retirementWithoutRetired::build,
                        "An active lesson with a retirement is refused"),
                () -> assertEquals(Status.RETIRED, retiredWithRetirement.build().status(),
                        "A retired lesson with its retirement is taken"));
    }

    @Test
    @DisplayName("a rule is set exactly for the category arch-constraint")
    void ruleIsSetExactlyForArchConstraint() {
        var archConstraintWithoutRule = LessonFixtures.frontMatterBuilder()
                .category(Category.ARCH_CONSTRAINT).rule(null);
        var bugWithRule = LessonFixtures.frontMatterBuilder().category(Category.BUG).rule(LessonFixtures.text());
        var archConstraint = archConstraint().build();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, archConstraintWithoutRule::build,
                        "An arch-constraint without a rule is refused"),
                () -> assertThrows(IllegalArgumentException.class, bugWithRule::build,
                        "A rule on another category is refused"),
                () -> assertNotNull(archConstraint.rule(), "An arch-constraint with rule is taken"));
    }

    @Test
    @DisplayName("the recurrence count and the last-seen date are set only for arch-constraint")
    void recurrenceIsSetOnlyForArchConstraint() {
        var bugWithCount = LessonFixtures.frontMatterBuilder().category(Category.BUG).recurrenceCount(2);
        var bugWithLastSeen = LessonFixtures.frontMatterBuilder().category(Category.BUG)
                .lastSeen(LessonFixtures.date());

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, bugWithCount::build,
                        "A recurrence count on a bug is refused"),
                () -> assertThrows(IllegalArgumentException.class, bugWithLastSeen::build,
                        "A last-seen date on a bug is refused"),
                () -> assertEquals(2, archConstraint().build().recurrenceCount(),
                        "An arch-constraint carries its recurrence"));
    }

    @Test
    @DisplayName("a lesson and a revision carry at least one source")
    void sourcesAreNeverEmpty() {
        var withoutSources = LessonFixtures.frontMatterBuilder().sources(List.of());
        var instant = LessonFixtures.instant();
        var digest = LessonFixtures.digest();
        var frontMatter = LessonFixtures.frontMatter();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, withoutSources::build,
                        "A lesson without sources is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Revision(1, instant, List.of(), digest),
                        "A revision without sources is refused"),
                () -> assertFalse(frontMatter.sources().isEmpty(), "A lesson with a source is taken"));
    }

    @Test
    @DisplayName("a lesson carries at least one revision")
    void revisionsAreNeverEmpty() {
        var withoutRevisions = LessonFixtures.frontMatterBuilder().revisions(List.of());

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, withoutRevisions::build,
                        "A lesson without revisions is refused"),
                () -> assertEquals(1, LessonFixtures.frontMatter().revisions().size(),
                        "A lesson with one revision is taken"));
    }

    @Test
    @DisplayName("the revision counter is the n of the last revisions entry")
    void revisionIsTheLastRevisionEntry() {
        var history = List.of(LessonFixtures.revision(1), LessonFixtures.revision(2));
        var behindTheHistory = LessonFixtures.frontMatterBuilder().revision(1).revisions(history);
        var atTheHistory = LessonFixtures.frontMatterBuilder().revision(2).revisions(history);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, behindTheHistory::build,
                        "A revision behind the last entry is refused"),
                () -> assertEquals(2, atTheHistory.build().revision(), "A revision at the last entry is taken"));
    }

    @Test
    @DisplayName("derived_from_untrusted is a primitive the record only carries")
    void derivedFromUntrustedIsOnlyCarried() {
        var untrusted = LessonFixtures.frontMatterBuilder().derivedFromUntrusted(true).build();
        var trusted = LessonFixtures.frontMatterBuilder().derivedFromUntrusted(false).build();

        assertAll(
                () -> assertTrue(untrusted.derivedFromUntrusted(), "The mark is carried as it was set"),
                () -> assertFalse(trusted.derivedFromUntrusted(), "The absence of the mark is carried too"));
    }

    @Test
    @DisplayName("the members of the schema outside the named rules are bounded")
    void membersAreBounded() {
        var emptyTitle = LessonFixtures.frontMatterBuilder().title("");
        var emptyComponent = LessonFixtures.frontMatterBuilder().component("");
        var emptyToolVersion = LessonFixtures.frontMatterBuilder().toolVersion("");
        var zeroRevision = LessonFixtures.frontMatterBuilder().revision(0);
        var zeroRecurrence = archConstraint().recurrenceCount(0);
        var wrongVersion = LessonFixtures.frontMatterBuilder()
                .formatVersion(LessonFrontMatter.FORMAT_VERSION + 1);
        var shortStatement = "x".repeat(LessonFrontMatter.MIN_REMEDY_STATEMENT - 1);
        var longStatement = "x".repeat(LessonFrontMatter.MAX_REMEDY_STATEMENT + 1);
        var statement = LessonFixtures.statement();
        var longCheck = "x".repeat(LessonFrontMatter.MAX_REMEDY_CHECK + 1);
        var instant = LessonFixtures.instant();
        var text = LessonFixtures.text();
        var source = LessonFixtures.source();
        var sources = List.of(source);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, emptyTitle::build, "An empty title is refused"),
                () -> assertThrows(IllegalArgumentException.class, emptyComponent::build,
                        "An empty component is refused"),
                () -> assertThrows(IllegalArgumentException.class, emptyToolVersion::build,
                        "An empty tool version is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Remedy(Remedy.Kind.CHECK, shortStatement, null),
                        "A statement below its bound is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Remedy(Remedy.Kind.CHECK, longStatement, null),
                        "A statement above its bound is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Remedy(Remedy.Kind.WIRE_CHECK, statement, longCheck),
                        "A check above its bound is refused"),
                () -> assertThrows(IllegalArgumentException.class, zeroRevision::build,
                        "A revision counter below its minimum is refused"),
                () -> assertThrows(IllegalArgumentException.class, zeroRecurrence::build,
                        "A recurrence count below its minimum is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Source(Source.Kind.PLAN, text, "not-a-digest"),
                        "A source digest that is no SHA-256 is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Revision(1, instant, sources, "not-a-digest"),
                        "A body digest that is no SHA-256 is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Claim("Not A Plan", instant),
                        "A plan id outside its pattern is refused"),
                () -> assertThrows(IllegalArgumentException.class, wrongVersion::build,
                        "A format version above one is refused"));
    }

    @Test
    @DisplayName("a claim and a merge origin are carried when the schema admits them")
    void optionalRecordsAreCarried() {
        var claimed = LessonFixtures.frontMatterBuilder()
                .claim(new Claim("v2-11-lesson-record-and-codec", LessonFixtures.instant())).build();
        var merged = LessonFixtures.frontMatterBuilder()
                .mergedFrom(List.of(new MergedFrom(LessonFixtures.lessonId(), LessonFixtures.instant(),
                        Generators.letterStrings(1, 20).next())))
                .build();

        assertAll(
                () -> assertNotNull(claimed.claim(), "A claimed lesson carries its claim"),
                () -> assertEquals(1, merged.mergedFrom().size(), "A merged lesson carries its origin"));
    }
}
