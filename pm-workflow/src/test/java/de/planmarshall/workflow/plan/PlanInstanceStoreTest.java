/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static de.planmarshall.workflow.plan.PlanFixtures.COMPOSED_AT;
import static de.planmarshall.workflow.plan.PlanFixtures.composer;
import static de.planmarshall.workflow.plan.PlanFixtures.facts;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.DigestSidecar;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.dsl.WorkflowPrinter;
import de.planmarshall.workflow.verify.DeclarationBounds;
import de.planmarshall.workflow.verify.InvariantFixtures;

/**
 * The store of the plan instance (PM-IMPL-5, PM-WF-1): in a temporary machine store, every version file is written
 * once and never rewritten, every composition attempt appends exactly one line to the composition log, a refused
 * composition writes only its line, and the text of each bound sub-unit is written once per digest as
 * {@code mcp/units/<digest>.pmwf}, in the transaction of the first version naming it, covered by the digest sidecar.
 */
@EnableTestLogger
@DisplayName("PlanInstanceStore")
class PlanInstanceStoreTest {

    private static final String PROJECT = "pm-mcp-core";

    @TempDir
    Path base;

    private JournalScope scope;

    private PlanInstanceStore store;

    /** A lock transaction that holds every key: the test is the only writer of its scope. */
    private static final class HoldingEverything implements LockTransaction {

        @Override
        public void acquire(LockKey key) {
            // every key is held already
        }

        @Override
        public boolean holds(LockKey key) {
            return true;
        }

        @Override
        public void close() {
            // nothing to release
        }
    }

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, PROJECT, PlanFixtures.PLAN_ID);
        Files.createDirectories(scope.journalFile().getParent());
        store = new PlanInstanceStore(scope);
    }

    /** Records in one transaction of the plan and commits it, as one locked call of the runtime does. */
    private void committed(Consumer<ScopeTransaction> recording) {
        try (var lock = new HoldingEverything()) {
            var transaction = ScopeTransaction.open(lock, scope, event -> {
                // no audit record is owed by an instance write
            }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
            recording.accept(transaction);
            transaction.commit();
        }
    }

    private static List<SubUnitText> texts() {
        return PlanFixtures.subUnits().stream().map(subUnit -> new SubUnitText(subUnit.reference(),
                PlanFixtures.subUnitText(subUnit.reference().file().orElseThrow()))).toList();
    }

    private static PlanInstance version1() {
        var result = composer().compose(PlanFixtures.start(facts().build()));
        return assertInstanceOf(CompositionResult.Composed.class, result, result::toString).instance();
    }

    private static PlanInstance nextVersion(PlanInstance previous, List<CompositionRequest.SubUnit> subUnits) {
        var template = PlanFixtures.request(CompositionEvent.WAIVER, facts().build(), Optional.of(previous),
                List.of(), InstancePosition.at("finalize.push-barrier"));
        var request = new CompositionRequest(template.planId(), template.event(), template.template(),
                template.templates(), subUnits, template.facts(), template.configHash(),
                template.realizedFootprintDigest(), template.providersDigest(), template.waivers(),
                template.position(), template.previous(), template.verificationSteps(), template.finalizeSteps());
        var result = composer().compose(request);
        return assertInstanceOf(CompositionResult.Composed.class, result, result::toString).instance();
    }

    /** Composes the waiver version after the previous one, under the waivers in force. */
    private static PlanInstance waiverVersion(PlanInstance previous, List<Waiver> waivers) {
        var result = composer().compose(PlanFixtures.request(CompositionEvent.WAIVER, facts().build(),
                Optional.of(previous), waivers, InstancePosition.at("finalize.push-barrier")));
        return assertInstanceOf(CompositionResult.Composed.class, result, result::toString).instance();
    }

    /** Records version 1. */
    private void recordComposed(PlanInstance instance, List<SubUnitText> texts) {
        committed(transaction -> store.recordComposed(transaction, instance, texts,
                CompositionRecord.composed(instance, Optional.empty(), COMPOSED_AT)));
    }

    /** Records the version after the previous one. */
    private void recordComposed(PlanInstance previous, PlanInstance instance, List<SubUnitText> texts) {
        committed(transaction -> store.recordComposed(transaction, instance, texts,
                CompositionRecord.composed(instance, Optional.of(previous), COMPOSED_AT)));
    }

    private static CompositionRecord attempt(CompositionRecord.Result result, Optional<CompositionCause> cause) {
        return new CompositionRecord(CompositionEvent.CONFIG_ADOPTED, result, Optional.empty(),
                CompositionEvent.Trigger.OPERATOR, cause, new TreeMap<>(), Optional.empty(), COMPOSED_AT);
    }

    private List<String> logLines() throws IOException {
        return Files.readAllLines(scope.resolve(PlanInstanceStore.COMPOSITION_LOG), StandardCharsets.UTF_8);
    }

    private Path subUnitFile(String digest) {
        return scope.resolve(PlanInstanceStore.subUnitPath(digest));
    }

    @Nested
    @DisplayName("Versions")
    class Versions {

        @Test
        @DisplayName("a composed version is written with its text and logged")
        void written() {
            var instance = version1();

            recordComposed(instance, texts());

            assertAll(
                    () -> assertEquals(Optional.of(instance.text()), store.readVersion(1)),
                    () -> assertEquals(Optional.empty(), store.readVersion(2)),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            "Plan instance version 1 of plan 'plan-fixture' recorded for the event 'start'"));
        }

        @Test
        @DisplayName("a second write of the same version is refused and the file stays as it was")
        void writtenOnce() {
            var instance = version1();
            recordComposed(instance, texts());

            try (var lock = new HoldingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                var compositionRecord = CompositionRecord.composed(instance, Optional.empty(), COMPOSED_AT);
                var texts = texts();
                assertThrows(IllegalStateException.class, () -> store.recordComposed(transaction, instance, texts,
                        compositionRecord));
            }
            assertEquals(Optional.of(instance.text()), store.readVersion(1));
        }

        /**
         * The interleaved case: two transactions of one lock transaction both pass the early check of the same
         * version, because neither has committed when the other records; the commit of the second is refused.
         */
        @Test
        @DisplayName("two transactions of one lock that record the same version: the second commit is refused")
        void interleavedTransactions() {
            var first = version1();
            var otherFacts = facts().configuration("derived.push_remote", "upstream").build();
            var otherResult = composer().compose(PlanFixtures.start(otherFacts));
            var second = assertInstanceOf(CompositionResult.Composed.class, otherResult, otherResult::toString)
                    .instance();
            assertAll("the two compositions are version 1 with different texts",
                    () -> assertEquals(1, second.version()),
                    () -> assertNotEquals(first.text(), second.text()));
            var texts = texts();

            try (var lock = new HoldingEverything()) {
                var firstTransaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                var secondTransaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                store.recordComposed(firstTransaction, first, texts, CompositionRecord.composed(first, Optional.empty(), COMPOSED_AT));
                store.recordComposed(secondTransaction, second, texts,
                        CompositionRecord.composed(second, Optional.empty(), COMPOSED_AT));

                firstTransaction.commit();

                assertThrows(IllegalStateException.class, secondTransaction::commit);
            }
            assertAll(
                    () -> assertEquals(Optional.of(first.text()), store.readVersion(1),
                            "the text of the first transaction is still the version"),
                    () -> assertEquals(1, logLines().size(), "the refused commit wrote no log line"));
        }

        @Test
        @DisplayName("a record that does not record the version, or a composed attempt without its text, is refused")
        void mismatchedRecord() {
            var instance = version1();
            var refusedRecord = attempt(CompositionRecord.Result.REFUSED, Optional.empty());
            var composedRecord = CompositionRecord.composed(instance, Optional.empty(), COMPOSED_AT);
            var texts = texts();

            try (var lock = new HoldingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                assertAll(
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> store.recordComposed(transaction, instance, texts, refusedRecord)),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> store.recordAttempt(transaction, composedRecord)));
            }
        }
    }

    @Nested
    @DisplayName("Records of another version")
    class ForeignRecords {

        private CompositionRecord composedWith(CompositionEvent event, CompositionEvent.Trigger trigger,
                SortedMap<String, String> digests) {
            return new CompositionRecord(event, CompositionRecord.Result.COMPOSED, Optional.of(1), trigger,
                    Optional.empty(), digests, Optional.empty(), COMPOSED_AT);
        }

        private void assertRefused(CompositionRecord compositionRecord) {
            var instance = version1();
            var texts = texts();

            try (var lock = new HoldingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                assertAll(
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> store.recordComposed(transaction, instance, texts, compositionRecord)),
                        () -> assertFalse(Files.exists(scope.resolve("instance"))));
            }
        }

        @Test
        @DisplayName("a composed record of another event is refused")
        void otherEvent() {
            var instance = version1();

            assertRefused(composedWith(CompositionEvent.OUTLINE, instance.header().event().trigger(),
                    CompositionRecord.inputDigests(instance.header().inputs())));
        }

        @Test
        @DisplayName("a composed record of another trigger is refused")
        void otherTrigger() {
            var instance = version1();

            assertRefused(composedWith(instance.header().event(), CompositionEvent.Trigger.OPERATOR,
                    CompositionRecord.inputDigests(instance.header().inputs())));
        }

        @Test
        @DisplayName("a composed record of other input digests is refused")
        void otherDigests() {
            var instance = version1();
            var digests = new TreeMap<>(CompositionRecord.inputDigests(instance.header().inputs()));
            digests.put("config_hash", "0".repeat(64));

            assertRefused(composedWith(instance.header().event(), instance.header().event().trigger(), digests));
        }
    }

    @Nested
    @DisplayName("Composition log")
    class CompositionLog {

        @Test
        @DisplayName("every attempt appends one line, a refused one with a null version and its refusal")
        void lineperAttempt() throws Exception {
            recordComposed(version1(), texts());
            committed(transaction -> store.recordAttempt(transaction, attempt(CompositionRecord.Result.REFUSED,
                    Optional.of(CompositionCause.refused(CompositionResult.Refusal.POSITION_NOT_CONTAINED)))));
            committed(transaction -> store.recordAttempt(transaction, attempt(CompositionRecord.Result.KEPT,
                    Optional.empty())));

            var lines = logLines();
            assertAll(
                    () -> assertEquals(3, lines.size()),
                    () -> assertTrue(lines.get(0).contains("\"result\":\"composed\",\"trigger\":\"server\","
                            + "\"version\":1"), lines.get(0)),
                    () -> assertTrue(lines.get(1).contains("\"refusal\":\"position_not_contained\""), lines.get(1)),
                    () -> assertTrue(lines.get(1).contains("\"version\":null"), lines.get(1)),
                    () -> assertTrue(lines.get(2).contains("\"result\":\"kept\""), lines.get(2)),
                    () -> assertFalse(lines.get(2).contains("\"cause\""), lines.get(2)));
        }

        @Test
        @DisplayName("a refused composition writes its line and no version or sub-unit file, and is logged")
        void refusedWritesOnlyItsLine() {
            committed(transaction -> store.recordAttempt(transaction, attempt(CompositionRecord.Result.REFUSED,
                    Optional.of(CompositionCause.refused(CompositionResult.Refusal.INVARIANT_FAILED)))));

            assertAll(
                    () -> assertEquals(1, logLines().size()),
                    () -> assertEquals(Optional.empty(), store.readVersion(1)),
                    () -> assertFalse(Files.exists(scope.resolve("mcp/units"))),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            "Composition 'config_adopted' of plan 'plan-fixture' refused with 'invariant_failed'"));
        }

        @Test
        @DisplayName("the digests of a composed version name every input of its header")
        void inputDigests() {
            var instance = version1();
            var inputs = instance.header().inputs();

            var digests = CompositionRecord.composed(instance, Optional.empty(), COMPOSED_AT).inputDigests();

            assertAll(
                    () -> assertEquals(inputs.configHash(), digests.get("config_hash")),
                    () -> assertEquals(inputs.compositionRulesDigest(), digests.get("composition_rules")),
                    () -> assertEquals(inputs.planFactsDigest(), digests.get("plan_facts")),
                    () -> assertEquals(inputs.templates().getFirst().digest(), digests.get("template:plan")),
                    () -> assertTrue(digests.containsKey("sub_unit:git.commit_and_push")),
                    () -> assertFalse(digests.containsKey("providers")));
        }

        @Test
        @DisplayName("a composed attempt needs its version and no other attempt has one")
        @SuppressWarnings("java:S5778") // each lambda is one constructor call; its arguments are the case table
        void versionFitsResult() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new CompositionRecord(
                            CompositionEvent.PLAN, CompositionRecord.Result.COMPOSED, Optional.empty(),
                            CompositionEvent.Trigger.SERVER, Optional.empty(), new TreeMap<>(), Optional.empty(),
                            COMPOSED_AT)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new CompositionRecord(
                            CompositionEvent.PLAN, CompositionRecord.Result.KEPT, Optional.of(2),
                            CompositionEvent.Trigger.SERVER, Optional.empty(), new TreeMap<>(), Optional.empty(),
                            COMPOSED_AT)));
        }

        @Test
        @DisplayName("a diff summary belongs to a composed version after the first, and to nothing else")
        @SuppressWarnings("java:S5778") // each lambda is one constructor call; its arguments are the case table
        void diffSummaryFitsVersion() {
            var summary = Optional.of(new DiffSummary(List.of(), List.of(), List.of(), List.of(), List.of()));

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new CompositionRecord(
                            CompositionEvent.PLAN, CompositionRecord.Result.COMPOSED, Optional.of(2),
                            CompositionEvent.Trigger.SERVER, Optional.empty(), new TreeMap<>(), Optional.empty(),
                            COMPOSED_AT)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new CompositionRecord(
                            CompositionEvent.START, CompositionRecord.Result.COMPOSED, Optional.of(1),
                            CompositionEvent.Trigger.SERVER, Optional.empty(), new TreeMap<>(), summary,
                            COMPOSED_AT)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new CompositionRecord(
                            CompositionEvent.PLAN, CompositionRecord.Result.REFUSED, Optional.empty(),
                            CompositionEvent.Trigger.SERVER, Optional.empty(), new TreeMap<>(), summary,
                            COMPOSED_AT)));
        }

        @Test
        @DisplayName("the line of version 1 carries diff_summary null, that of version 2 the summary against "
                + "version 1, and an attempt without a version none")
        void diffSummaryLines() throws Exception {
            var first = version1();
            var second = waiverVersion(first, List.of(Waiver.raise("W-1", "fix_budget:qgate-findings", 4)));
            recordComposed(first, texts());
            recordComposed(first, second, texts());
            committed(transaction -> store.recordAttempt(transaction, attempt(CompositionRecord.Result.KEPT,
                    Optional.empty())));

            var lines = logLines();
            assertAll(
                    () -> assertEquals(3, lines.size()),
                    () -> assertTrue(lines.get(0).contains("\"diff_summary\":null"), lines.get(0)),
                    () -> assertTrue(lines.get(1).contains("\"diff_summary\":{\"ceilings_changed\":[{\"argument\":"
                                    + "\"fix_budget\",\"call_site\":\"finalize.remediate\",\"previous\":3,\"value\":4}],"
                                    + "\"gates_unwaived\":[],\"gates_waived\":[],\"pruned_added\":[],\"pruned_removed\":[]}"),
                            lines.get(1)),
                    () -> assertFalse(lines.get(2).contains("diff_summary"), lines.get(2)));
        }

        @Test
        @DisplayName("a version 2 whose summary is not the one against the recorded version 1 is refused")
        void diffSummaryAgainstRecordedVersion() {
            var first = version1();
            recordComposed(first, texts());
            var raised = waiverVersion(first, List.of(Waiver.raise("W-1", "fix_budget:qgate-findings", 4)));
            var plain = waiverVersion(first, List.of());
            var foreign = CompositionRecord.composed(raised, Optional.of(first), COMPOSED_AT);
            var mismatched = new CompositionRecord(plain.header().event(), CompositionRecord.Result.COMPOSED,
                    Optional.of(2), plain.header().event().trigger(), Optional.empty(),
                    CompositionRecord.inputDigests(plain.header().inputs()), foreign.diffSummary(), COMPOSED_AT);
            var texts = texts();

            try (var lock = new HoldingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                assertAll(
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> store.recordComposed(transaction, plain, texts, mismatched)),
                        () -> assertEquals(Optional.empty(), store.readVersion(2)));
            }
        }

        @Test
        @DisplayName("a version 2 is refused while version 1 is not recorded, and a record is refused whose previous "
                + "version is not the one before")
        void previousVersionRequired() {
            var first = version1();
            var second = waiverVersion(first, List.of());
            var previous = Optional.of(first);
            var compositionRecord = CompositionRecord.composed(second, previous, COMPOSED_AT);
            var texts = texts();
            Optional<PlanInstance> noPrevious = Optional.empty();

            try (var lock = new HoldingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                var refused = assertThrows(IllegalArgumentException.class,
                        () -> store.recordComposed(transaction, second, texts, compositionRecord));
                assertAll(
                        () -> assertTrue(refused.getMessage().contains("version 1"), refused.getMessage()),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> CompositionRecord.composed(second, noPrevious, COMPOSED_AT)),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> CompositionRecord.composed(first, previous, COMPOSED_AT)));
            }
        }

        @Test
        @DisplayName("a version 2 is refused with the cause attached when the recorded version 1 carries a header "
                + "that fails the schema, and nothing is recorded")
        void unreadablePreviousHeader() throws Exception {
            var first = version1();
            recordComposed(first, texts());
            var second = waiverVersion(first, List.of());
            var compositionRecord = CompositionRecord.composed(second, Optional.of(first), COMPOSED_AT);
            var texts = texts();
            var corrupted = first.text().replace("\"format_version\":1,", "\"format_version\":2,");
            Files.writeString(scope.resolve(PlanInstanceStore.versionPath(1)), corrupted, StandardCharsets.UTF_8);

            try (var lock = new HoldingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                var refused = assertThrows(IllegalStateException.class,
                        () -> store.recordComposed(transaction, second, texts, compositionRecord));
                assertAll(
                        () -> assertEquals(Optional.of(corrupted), store.readVersion(1),
                                "the refused record left the recorded text as it was"),
                        () -> assertTrue(refused.getMessage().contains("version 1"), refused.getMessage()),
                        () -> assertInstanceOf(IllegalArgumentException.class, refused.getCause()),
                        () -> assertEquals(Optional.empty(), store.readVersion(2)),
                        () -> assertEquals(1, logLines().size(), "no log line was written"));
            }
        }

        @Test
        @DisplayName("a version 2 is refused with the parser diagnostic when the recorded version 1 is refused by the "
                + "parser, and nothing is recorded")
        void parserRefusedPreviousHeader() throws Exception {
            var first = version1();
            recordComposed(first, texts());
            var second = waiverVersion(first, List.of());
            var compositionRecord = CompositionRecord.composed(second, Optional.of(first), COMPOSED_AT);
            var texts = texts();
            var corrupted = first.text().replace("\nscope plan\n", "\nscope team\n");
            Files.writeString(scope.resolve(PlanInstanceStore.versionPath(1)), corrupted, StandardCharsets.UTF_8);

            try (var lock = new HoldingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                var refused = assertThrows(IllegalStateException.class,
                        () -> store.recordComposed(transaction, second, texts, compositionRecord));
                assertAll(
                        () -> assertEquals(Optional.of(corrupted), store.readVersion(1),
                                "the refused record left the recorded text as it was"),
                        () -> assertTrue(refused.getMessage().contains("refused by the parser"), refused.getMessage()),
                        () -> assertTrue(refused.getMessage().contains("expected a scope"), refused.getMessage()),
                        () -> assertNull(refused.getCause(), "a parser refusal carries no cause"),
                        () -> assertEquals(Optional.empty(), store.readVersion(2)),
                        () -> assertEquals(1, logLines().size(), "no log line was written"));
            }
        }

        @Test
        @DisplayName("a version 2 is refused when the recorded version 1 carries no instance header, and nothing "
                + "is recorded")
        void headerlessPreviousVersion() throws Exception {
            var first = version1();
            recordComposed(first, texts());
            var second = waiverVersion(first, List.of());
            var compositionRecord = CompositionRecord.composed(second, Optional.of(first), COMPOSED_AT);
            var texts = texts();
            var headerless = first.text().lines()
                    .filter(line -> !line.startsWith("instance "))
                    .collect(Collectors.joining("\n", "", "\n"));
            Files.writeString(scope.resolve(PlanInstanceStore.versionPath(1)), headerless, StandardCharsets.UTF_8);

            try (var lock = new HoldingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                var refused = assertThrows(IllegalStateException.class,
                        () -> store.recordComposed(transaction, second, texts, compositionRecord));
                assertAll(
                        () -> assertEquals(Optional.of(headerless), store.readVersion(1),
                                "the refused record left the recorded text as it was"),
                        () -> assertTrue(refused.getMessage().contains("no readable header"), refused.getMessage()),
                        () -> assertNull(refused.getCause(), "a headerless version carries no cause"),
                        () -> assertEquals(Optional.empty(), store.readVersion(2)),
                        () -> assertEquals(1, logLines().size(), "no log line was written"));
            }
        }
    }

    @Nested
    @DisplayName("Sub-units")
    class SubUnits {

        @Test
        @DisplayName("the first version writes the text of every bound sub-unit, covered by the digest sidecar, and "
                + "none of an asking variant it does not bind")
        void firstWrite() throws Exception {
            var instance = version1();
            var named = instance.header().inputs().subUnits();
            recordComposed(instance, texts());

            var sidecar = Files.readString(scope.sidecarFile(), StandardCharsets.UTF_8);
            for (var text : texts()) {
                var digest = text.reference().digest();
                if (named.contains(text.reference())) {
                    assertAll(
                            () -> assertEquals(Optional.of(text.text()), store.readSubUnit(digest)),
                            () -> assertTrue(sidecar.contains(PlanInstanceStore.subUnitPath(digest)), sidecar));
                } else {
                    assertEquals(Optional.empty(), store.readSubUnit(digest), text.reference()::unit);
                }
            }
        }

        @Test
        @DisplayName("a version that names the adjudication unit stores its verified text content-addressed like "
                + "every bound sub-unit")
        void adjudicationUnitStored() throws Exception {
            var instance = withAdjudication();
            var adjudication = instance.header().inputs().subUnits().getLast();

            recordComposed(instance, textsWithAdjudication());

            var sidecar = Files.readString(scope.sidecarFile(), StandardCharsets.UTF_8);
            assertAll(
                    () -> assertEquals(DeclarationBounds.ADJUDICATION_UNIT, adjudication.unit()),
                    () -> assertEquals(Optional.of(InvariantFixtures.read(InvariantFixtures.ADJUDICATION_FILE)),
                            store.readSubUnit(adjudication.digest())),
                    () -> assertTrue(Files.isRegularFile(subUnitFile(adjudication.digest()))),
                    () -> assertTrue(sidecar.contains(PlanInstanceStore.subUnitPath(adjudication.digest())),
                            sidecar));
        }

        @Test
        @DisplayName("a version that names the adjudication unit is refused when its texts lack it, before any "
                + "write")
        void adjudicationUnitTextMissing() {
            var instance = withAdjudication();
            var compositionRecord = CompositionRecord.composed(instance, Optional.empty(), COMPOSED_AT);
            var texts = texts();

            try (var lock = new HoldingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                var refused = assertThrows(IllegalArgumentException.class,
                        () -> store.recordComposed(transaction, instance, texts, compositionRecord));
                assertAll(
                        () -> assertTrue(refused.getMessage().contains(DeclarationBounds.ADJUDICATION_UNIT),
                                refused.getMessage()),
                        () -> assertFalse(Files.exists(scope.resolve("instance"))));
            }
        }

        private static PlanInstance withAdjudication() {
            var result = composer().compose(PlanFixtures.withAdjudication(PlanFixtures.start(facts().build())));
            return assertInstanceOf(CompositionResult.Composed.class, result, result::toString).instance();
        }

        private static List<SubUnitText> textsWithAdjudication() {
            var request = PlanFixtures.withAdjudication(PlanFixtures.start(facts().build()));
            return request.subUnits().stream().map(subUnit -> new SubUnitText(subUnit.reference(),
                    PlanFixtures.subUnitText(subUnit.reference().file().orElseThrow()))).toList();
        }

        @Test
        @DisplayName("a later version naming the same digest leaves its file untouched")
        void writtenOncePerDigest() throws Exception {
            var first = version1();
            recordComposed(first, texts());
            var digest = texts().getFirst().reference().digest();
            var untouched = FileTime.fromMillis(0);
            Files.setLastModifiedTime(subUnitFile(digest), untouched);

            recordComposed(first, nextVersion(first, PlanFixtures.subUnits()), texts());

            assertAll(
                    () -> assertEquals(untouched, Files.getLastModifiedTime(subUnitFile(digest))),
                    () -> assertTrue(store.readVersion(2).isPresent()));
        }

        @Test
        @DisplayName("a new digest adds its own file beside the old one")
        void newDigest() {
            var first = version1();
            recordComposed(first, texts());
            var changedText = InvariantFixtures.read(InvariantFixtures.GIT_FILE) + "# changed for the test\n";
            var git = PlanFixtures.subUnits().getFirst();
            var changedReference = new UnitReference(git.reference().unit(), git.reference().file(),
                    PlanFacts.sha256(changedText), UnitOrigin.DEFAULT);
            var subUnits = Stream.concat(Stream.of(new CompositionRequest.SubUnit(changedReference,
                    git.definition())), PlanFixtures.subUnits().stream().skip(1)).toList();
            var texts = Stream.concat(Stream.of(new SubUnitText(changedReference, changedText)),
                    texts().stream().skip(1)).toList();

            recordComposed(first, nextVersion(first, subUnits), texts);

            assertAll(
                    () -> assertEquals(Optional.of(changedText), store.readSubUnit(changedReference.digest())),
                    () -> assertTrue(store.readSubUnit(git.reference().digest()).isPresent()));
        }

        @Test
        @DisplayName("a text that does not match its digest, or a missing text, is refused before any write")
        void refusedBeforeWrite() {
            var instance = version1();
            var compositionRecord = CompositionRecord.composed(instance, Optional.empty(), COMPOSED_AT);
            var reference = texts().getFirst().reference();
            var otherText = InvariantFixtures.read(InvariantFixtures.GIT_FILE)
                    .replace("workflow git.commit_and_push version", "workflow other version");
            var wrong = List.of(new SubUnitText(reference, otherText), texts().get(1));
            var missing = List.of(texts().get(1));

            try (var lock = new HoldingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                assertAll(
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> store.recordComposed(transaction, instance, wrong, compositionRecord)),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> store.recordComposed(transaction, instance, missing, compositionRecord)),
                        () -> assertFalse(Files.exists(scope.resolve("instance"))),
                        () -> assertThrows(IllegalStateException.class, transaction::commit));
            }
        }
    }

    /**
     * The version as one set of files: the plan unit's file and one file per composed unit, all written in the
     * transaction that composes the version, and the set refused when it disagrees with its header.
     */
    @Nested
    @DisplayName("Composed set")
    class ComposedSet {

        private static final InstancePosition AT_INIT = InstancePosition.at("init.ready");

        private static PlanInstance merged() {
            var result = PlanFixtures.mergedComposer().compose(PlanFixtures.mergedRequest(CompositionEvent.START,
                    facts().build(), Optional.empty(), AT_INIT, List.of()));
            return assertInstanceOf(CompositionResult.Composed.class, result, result::toString).instance();
        }

        private Path instanceFile(ComposedUnit unit) {
            return scope.resolve(unit.file());
        }

        @Test
        @DisplayName("a composed version writes the plan file and one file per composed unit in the one transaction, "
                + "covered by the digest sidecar")
        void writtenAsOneSet() throws Exception {
            var instance = merged();
            var texts = textsOf(instance);

            recordComposed(instance, texts);

            assertAll(
                    () -> assertTrue(instance.units().size() > 2, instance.units()::toString),
                    () -> instance.units().forEach(unit -> assertEquals(Optional.of(unit.text()),
                            store.readUnit(instance.version(), unit.unit()))),
                    () -> instance.units().forEach(unit -> assertEquals(Optional.of(unit.text()),
                            read(instanceFile(unit)))),
                    () -> assertEquals(Optional.of(instance.text()), store.readVersion(instance.version())),
                    () -> assertTrue(instance.units().stream().allMatch(unit -> Files.exists(
                            instanceFile(unit)))));
            var sidecar = DigestSidecar.load(scope.sidecarFile());
            assertAll(
                    () -> assertTrue(sidecar.digest(PlanInstanceStore.versionPath(instance.version())).isPresent()),
                    () -> instance.units().forEach(unit -> assertTrue(sidecar.digest(unit.file()).isPresent(),
                            unit.file())));
        }

        /** @return the text of every sub-unit the version binds by digest, as the request composed it */
        private static List<SubUnitText> textsOf(PlanInstance instance) {
            var units = InvariantFixtures.passingUnits(UnitOrigin.PROJECT);
            return instance.header().inputs().subUnits().stream().map(reference -> new SubUnitText(reference,
                    WorkflowPrinter.print(units.get(reference.unit())))).toList();
        }

        @Test
        @DisplayName("a header that names a composed file the version does not write is refused before any write")
        void headerNamesNoFile() {
            var instance = merged();
            var withoutUnits = new PlanInstance(instance.version(), instance.header(), instance.unit(),
                    instance.text());

            try (var lock = new HoldingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> store.recordComposed(transaction,
                                withoutUnits, textsOf(withoutUnits), CompositionRecord.composed(withoutUnits,
                                        Optional.empty(), COMPOSED_AT))),
                        () -> assertFalse(Files.exists(scope.resolve("instance"))),
                        () -> assertThrows(IllegalStateException.class, transaction::commit));
            }
        }

        @Test
        @DisplayName("a composed file the header does not name is refused before any write")
        void fileNotNamed() {
            var header = deepOutline();
            var unitsOfAnother = lightOutline();
            var mixed = new PlanInstance(header.version(), header.header(), header.unit(), header.text(),
                    unitsOfAnother.units());

            try (var lock = new HoldingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(COMPOSED_AT, ZoneOffset.UTC));
                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> store.recordComposed(transaction,
                                mixed, textsOf(mixed), CompositionRecord.composed(mixed, Optional.empty(),
                                        COMPOSED_AT))),
                        () -> assertFalse(Files.exists(scope.resolve("instance"))),
                        () -> assertThrows(IllegalStateException.class, transaction::commit));
            }
        }

        /** @return a deep version at the outline event, which composes no light phase file */
        private static PlanInstance deepOutline() {
            return outline(PlanInstanceHeader.Lane.DEEP, "outline.deep");
        }

        /** @return a light version at the outline event, which composes the light phase files */
        private static PlanInstance lightOutline() {
            return outline(PlanInstanceHeader.Lane.LIGHT, "outline.light");
        }

        private static PlanInstance outline(PlanInstanceHeader.Lane lane, String position) {
            var start = PlanFixtures.perLaneComposer().compose(PlanFixtures.perLaneRequest(CompositionEvent.START,
                    facts().lane(lane).build(), Optional.empty(), AT_INIT));
            var first = assertInstanceOf(CompositionResult.Composed.class, start, start::toString).instance();
            var outline = PlanFixtures.perLaneComposer().compose(PlanFixtures.perLaneRequest(
                    CompositionEvent.OUTLINE, facts().lane(lane).build(), Optional.of(first),
                    InstancePosition.at(position)));
            return assertInstanceOf(CompositionResult.Composed.class, outline, outline::toString).instance();
        }

        private static Optional<String> read(Path file) {
            try {
                return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
    }

    @Test
    @DisplayName("lives only in a plan scope")
    void planScopeOnly() {
        var epic = JournalScope.epic(base, PROJECT, "epic-fixture");

        assertThrows(IllegalArgumentException.class, () -> new PlanInstanceStore(epic));
    }
}
