/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportResult;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.role.qualification.GradedConfiguration;
import de.planmarshall.core.role.qualification.GradingConsent;
import de.planmarshall.core.role.qualification.GradingHarness;
import de.planmarshall.core.role.qualification.GradingPlan;
import de.planmarshall.core.role.qualification.GradingProfile;
import de.planmarshall.core.role.qualification.ItemCheckout;
import de.planmarshall.core.role.qualification.JudgeCheck;
import de.planmarshall.core.role.qualification.JudgeChecks;
import de.planmarshall.core.role.qualification.JudgeConfiguration;
import de.planmarshall.core.role.qualification.JudgeScorer;
import de.planmarshall.core.role.qualification.QualificationRecord;
import de.planmarshall.core.role.qualification.QualificationRecordStore;
import de.planmarshall.core.role.qualification.TokenTotal;
import de.planmarshall.core.role.qualification.Topic;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.LockKey;

/**
 * The fixtures of the grading tests (test helper): corpus items, an import result of any verdict, a scripted harness
 * and judge that record what they are offered, a judge check store in memory, and a qualification record store in a
 * temporary directory.
 */
final class GraderFixtures {

    static final String ROLE = "review-code";
    static final String GENERIC_ROLE = "worker";
    static final String HARNESS = "acme-cli";
    static final String MODEL = "acme-standard";
    static final String JUDGE_MODEL = "acme-judge";
    static final String VERSION = "1.4";
    static final String CORPUS_REVISION = "corpus-7";
    static final URI PROJECT_REPOSITORY = URI.create("https://example.org/plan-marshall/pm-mcp-core");
    static final URI DATASET = URI.create("https://example.org/datasets/reviews");
    static final String COMMIT = "b".repeat(40);
    static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private GraderFixtures() {
    }

    /** @return a lock manager that takes no operating-system lock */
    static LockManager locks() {
        return () -> new LockTransaction() {
            @Override
            public void acquire(LockKey key) {
                // nothing to take
            }

            @Override
            public boolean holds(LockKey key) {
                return true;
            }

            @Override
            public void close() {
                // nothing to release
            }
        };
    }

    /**
     * @param directory the qualification directory
     * @return the project set's record store under that directory
     */
    static QualificationRecordStore store(Path directory) {
        return new QualificationRecordStore(directory, LockKey.workspace(directory.getParent(), "pm-mcp-core"),
                locks());
    }

    /** @return an item the fixture harness answers with its reference */
    static Item item(String id, String role, String answer) {
        return new Item(id, role, answer);
    }

    /** The configurable corpus item of the tests. */
    static final class Item {

        private final String id;
        private String role;
        private String answer;
        private ImportedItem.GradingMethod method = ImportedItem.GradingMethod.EXACT_CHOICE;
        private ImportedItem.ReviewStatus status = ImportedItem.ReviewStatus.CONFIRMED;
        private URI repository = DATASET;
        private String commit = COMMIT;

        private Item(String id, String role, String answer) {
            this.id = id;
            this.role = role;
            this.answer = answer;
        }

        Item role(String role) {
            this.role = role;
            return this;
        }

        Item method(ImportedItem.GradingMethod method) {
            this.method = method;
            return this;
        }

        Item status(ImportedItem.ReviewStatus status) {
            this.status = status;
            return this;
        }

        Item origin(URI repository, String commit) {
            this.repository = repository;
            this.commit = commit;
            return this;
        }

        String id() {
            return id;
        }

        String answer() {
            return answer;
        }

        ImportedItem build() {
            var topic = id.substring(0, id.lastIndexOf('/'));
            var closed = method.isClosed();
            var offered = List.of(answer == null ? "clean" : answer, "clean".equals(answer) ? "defective" : "clean");
            var input = new ImportedItem.Input("Is the work right?", "", closed ? offered : null,
                    closed ? null : JsonValue.parse("{\"type\":\"string\"}"
                            .getBytes(StandardCharsets.UTF_8)));
            var target = new ImportedItem.Target(closed ? answer : null, "The reference says so.", List.of(),
                    closed ? List.of() : List.of(new ImportedItem.RubricItem("R1", "The defect is found.")), false);
            return new ImportedItem(id, topic, role, "A title", List.of(), null, input, target,
                    new ImportedItem.Grading(method, null, null),
                    new ImportedItem.Provenance(LocalDate.of(2026, 1, 1),
                            new ImportedItem.Origin(repository, commit, "data/rows.csv", 2), List.of("created")),
                    new ImportedItem.Review(status, "operator", LocalDate.of(2026, 1, 2), null), null, "canary");
        }
    }

    /** @return the import that covered one source without missing any */
    static ImportResult complete() {
        return new ImportResult(List.of(new ImportResult.CoveredSource("reviews", 2)), List.of());
    }

    /** @return the import that missed one source because its pin is absent */
    static ImportResult partial() {
        return new ImportResult(List.of(new ImportResult.CoveredSource("reviews", 2)),
                List.of(new ImportResult.MissedSource("papers", ImportResult.Reason.PIN_MISSING, "data/rows.csv",
                        "the pin is absent")));
    }

    /** @return the import a hash mismatch failed */
    static ImportResult failed() {
        return new ImportResult(List.of(),
                List.of(new ImportResult.MissedSource("papers", ImportResult.Reason.HASH_MISMATCH, "data/rows.csv",
                        "the hash is not the pinned one")));
    }

    /** @return the judge items of a balanced check */
    static List<JudgeCheck.Item> judgeItems() {
        return List.of(
                new JudgeCheck.Item("judge/right-1", "Is the answer right?", "", "a right answer", "a right answer",
                        JudgeCheck.Label.RIGHT),
                new JudgeCheck.Item("judge/wrong-1", "Is the answer right?", "", "a wrong answer",
                        "another wrong answer", JudgeCheck.Label.WRONG));
    }

    /** @return the judge configuration of the fixture judge */
    static JudgeConfiguration judgeConfiguration(EffectiveRole judge) {
        return new JudgeConfiguration(judge, Set.of("acme-standard-sibling"), judgeItems());
    }

    /**
     * @param role      the graded role
     * @param items     the corpus items
     * @param judge     the judge configuration
     * @param importResult the import verdict
     * @return a plan over the fixture corpus
     */
    static GradingPlan plan(EffectiveRole role, List<ImportedItem> items, JudgeConfiguration judge,
            ImportResult importResult) {
        return plan(role, items, judge, importResult, GradingProfile.single(), Optional.empty(), ItemCheckout.none());
    }

    /**
     * @param role         the graded role
     * @param items        the corpus items
     * @param judge        the judge configuration
     * @param importResult the import verdict
     * @param profile      the grading profile
     * @param previous     the record of an earlier grading
     * @param checkouts    whether a commit is present
     * @return the plan
     */
    static GradingPlan plan(EffectiveRole role, List<ImportedItem> items, JudgeConfiguration judge,
            ImportResult importResult, GradingProfile profile, Optional<QualificationRecord> previous,
            ItemCheckout checkouts) {
        return new GradingPlan(role, items, importResult, CORPUS_REVISION, PROJECT_REPOSITORY, profile, judge,
                checkouts, previous);
    }

    /** @return the read-only fixture role */
    static EffectiveRole role() {
        return ModelFixtures.role(ROLE, RoleClass.READ_ONLY, RoleForm.FRESH);
    }

    /** @return the judge's effective role, on another model */
    static EffectiveRole judge() {
        return ModelFixtures.roleIn("judge", RoleClass.ZERO_TOOL, RoleForm.FRESH, HARNESS, JUDGE_MODEL);
    }

    /**
     * @param agreements the agreement of each topic
     * @return a record of the fixture role that needs a judge check
     */
    static QualificationRecord qualification(Map<Topic, BigDecimal> agreements) {
        var topics = new LinkedHashMap<Topic,
                QualificationRecord.TopicResult>();
        agreements.forEach((topic, agreement) -> topics.put(topic,
                new QualificationRecord.TopicResult(agreement, BigDecimal.ZERO, 10)));
        var form = new QualificationRecord.FormResult(topics, 10, TokenTotal.measured(1_000), 100);
        return new QualificationRecord(ROLE, role().qualificationDigest(), configuration(), CORPUS_REVISION,
                List.of(), Map.of(RoleForm.FRESH, form), null, 0, NOW, List.of());
    }

    /** @return the graded members of the fixture role */
    static GradedConfiguration configuration() {
        return GradedConfiguration.of(role());
    }

    /** @return the consent that starts a grading */
    static GradingConsent granted() {
        return GradingConsent.GRANTED;
    }

    /** The scripted harness: it answers every item with the reference unless a test scripts another answer. */
    static final class Harness implements GradingHarness {

        private final List<Offered> offered = new ArrayList<>();
        private final Map<String, String> answers = new LinkedHashMap<>();
        private final Map<String, String> formAnswers = new LinkedHashMap<>();
        private final Set<String> lost = new HashSet<>();
        private final Set<String> lostInForm = new HashSet<>();
        private long tokens = 1_000;

        Harness answers(String itemId, String answer) {
            answers.put(itemId, answer);
            return this;
        }

        Harness answers(RoleForm form, String itemId, String answer) {
            formAnswers.put(form.wireName() + "/" + itemId, answer);
            return this;
        }

        Harness loses(String itemId) {
            lost.add(itemId);
            return this;
        }

        Harness loses(RoleForm form, String itemId) {
            lostInForm.add(form.wireName() + "/" + itemId);
            return this;
        }

        Harness tokens(long tokens) {
            this.tokens = tokens;
            return this;
        }

        List<Offered> offered() {
            return offered;
        }

        @Override
        public Optional<GradedAnswer> answer(Offered request) {
            offered.add(request);
            var key = request.form().wireName() + "/" + request.item().id();
            if (lost.contains(request.item().id()) || lostInForm.contains(key)) {
                return Optional.empty();
            }
            var answer = formAnswers.getOrDefault(key,
                    answers.getOrDefault(request.item().id(), request.item().target().answer() == null
                            ? "the defect is found"
                            : request.item().target().answer()));
            return Optional.of(new GradedAnswer(answer, TokenTotal.measured(tokens), 100));
        }
    }

    /** The scripted judge, which scores against the reference like a good instrument. */
    static final class Judge implements JudgeScorer {

        private final List<JudgePrompt> prompts = new ArrayList<>();
        private Function<JudgePrompt, Optional<BigDecimal>> score =
                prompt -> Optional.of(prompt.reference() == null ? BigDecimal.ZERO
                        : prompt.reference().equals(prompt.answer()) ? BigDecimal.ONE : BigDecimal.ZERO);

        Judge scores(Function<JudgePrompt, Optional<BigDecimal>> score) {
            this.score = score;
            return this;
        }

        List<JudgePrompt> prompts() {
            return prompts;
        }

        @Override
        public Optional<JudgeScore> score(JudgePrompt prompt) {
            prompts.add(prompt);
            return score.apply(prompt).map(value -> new JudgeScore(value, TokenTotal.measured(50), 20));
        }
    }

    /** The judge check store of the tests, in memory. */
    static final class JudgeCheckMemory implements JudgeChecks {

        private JudgeCheck.Record stored;
        private int writes;

        JudgeCheckMemory holds(JudgeCheck.Record entry) {
            this.stored = entry;
            return this;
        }

        int writes() {
            return writes;
        }

        @Override
        public Optional<JudgeCheck.Record> lookup() {
            return Optional.ofNullable(stored);
        }

        @Override
        public void write(JudgeCheck.Record entry) {
            this.stored = entry;
            writes++;
        }
    }

    /** @return a score value */
    static BigDecimal score(String value) {
        return new BigDecimal(value);
    }
}
