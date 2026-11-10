/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import de.cuioss.test.generator.Generators;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.HarnessProfile;
import de.planmarshall.core.role.RoleArtifactValidator;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleSkill;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.QualificationSettings;

/**
 * The fixtures of the qualification tests (test helper): corpus items of any topic, role and status, a record of any
 * measurement, a lock manager that takes no operating-system lock, and the two digests.
 */
final class QualificationFixtures {

    static final String ROLE = "review-code";
    static final String GENERIC_ROLE = "worker";
    static final String HARNESS = "c" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    static final String MODEL = "m" + Generators.letterStrings(3, 12).next();
    static final String JUDGE_MODEL = "j" + Generators.letterStrings(3, 12).next();
    static final String VERSION = "1.4";
    static final String DIGEST = "%064x".formatted(Generators.longs().next());
    static final String CORPUS_REVISION = "corpus-" + Generators.integers(1, 99).next();
    static final URI REPOSITORY = URI.create("https://example.org/"
            + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT));
    static final URI DATASET = URI.create("https://example.org/datasets/"
            + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT));
    static final Instant GRADED_AT = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());

    private static final String FRONTIER_MODEL = "x" + Generators.letterStrings(3, 12).next();
    private static final String FAST_MODEL = "y" + Generators.letterStrings(3, 12).next();
    private static final int CONTEXT_BUDGET = Generators.integers(200_000, 900_000).next();
    private static final int IDLE_WAKEUPS = Generators.integers(1, 10).next();
    private static final int MAX_HOLD_SECONDS = Generators.integers(301, 3_600).next();
    private static final int MAX_RESUME_SECONDS = Generators.integers(601, 604_800).next();
    private static final int ITEM_COUNT = Generators.integers(1, 50).next();
    private static final int TOTAL_TOKENS = Generators.integers(1_000, 10_000).next();
    private static final int DURATION_MILLIS = Generators.integers(1, 1_000).next();
    private static final String OWN_TOPIC = "t" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String GENERIC_TOPIC = "u" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String CORPUS_ANSWER = "a" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

    private QualificationFixtures() {
    }

    /** @return a lock manager that records nothing and takes no lock */
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
     * @return an item the fixture harness answers with the reference, so it is always right
     */
    static Item item(String id, String role, String answer) {
        return new Item(id, role, null, answer);
    }

    /** The configurable corpus item of the tests. */
    static final class Item {

        private final String id;
        private String role;
        private String topic;
        private String answer;
        private ImportedItem.GradingMethod method = ImportedItem.GradingMethod.EXACT_CHOICE;
        private ImportedItem.ReviewStatus status = ImportedItem.ReviewStatus.CONFIRMED;
        private URI repository = DATASET;
        private String commit = "%040x".formatted(Generators.longs().next());
        private List<String> choices;
        private String denominator = Generators.letterStrings(2, 5).next();

        private Item(String id, String role, String topic, String answer) {
            this.id = id;
            this.role = role;
            this.topic = topic != null ? topic : id.substring(0, id.lastIndexOf('/'));
            this.answer = answer;
        }

        Item role(String role) {
            this.role = role;
            return this;
        }

        Item topic(String topic) {
            this.topic = topic;
            return this;
        }

        Item answer(String answer) {
            this.answer = answer;
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

        Item choices(String... choices) {
            this.choices = List.of(choices);
            return this;
        }

        Item denominator(String denominator) {
            this.denominator = denominator;
            return this;
        }

        String id() {
            return id;
        }

        String answer() {
            return answer;
        }

        ImportedItem build() {
            var closed = method.isClosed();
            var offered = choices != null ? choices
                    : List.of(answer == null ? denominator : answer, denominator);
            var reference = closed ? answer : null;
            var rubric = closed ? List.<ImportedItem.RubricItem>of()
                    : List.of(new ImportedItem.RubricItem("r" + Generators.letterStrings(3, 8).next(),
                    Generators.letterStrings(5, 30).next()));
            var input = new ImportedItem.Input(Generators.letterStrings(5, 30).next(), "", closed ? offered : null,
                    closed ? null : JsonValue.parse("{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8)));
            var target = new ImportedItem.Target(reference, Generators.letterStrings(5, 30).next(), List.of(), rubric,
                    false);
            var grading = new ImportedItem.Grading(method, null, null);
            var provenance = new ImportedItem.Provenance(
                    LocalDate.ofEpochDay(Generators.longs(19_000, 21_000).next()),
                    new ImportedItem.Origin(repository, commit,
                            Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT) + "/rows.csv",
                            Generators.integers(1, 100).next()),
                    List.of(Generators.letterStrings(5, 30).next()));
            var review = new ImportedItem.Review(status, Generators.letterStrings(3, 10).next(),
                    LocalDate.ofEpochDay(Generators.longs(19_000, 21_000).next()), null);
            return new ImportedItem(id, topic, role, Generators.letterStrings(5, 30).next(),
                    List.of(Generators.letterStrings(2, 10).next()), null, input, target, grading,
                    provenance, review, null, "canary");
        }
    }

    /**
     * @param agreements the fresh agreement of each topic
     * @return a record measured on those topics, bound to the fixture digest and revision
     */
    static QualificationRecord qualification(Map<Topic, BigDecimal> agreements) {
        return qualification(agreements, Map.of(), BigDecimal.ZERO);
    }

    /**
     * @param freshAgreements the fresh agreement of each topic
     * @param warmAgreements  the warm agreement of each topic; empty for a fresh-only record
     * @param gap             the warm-fresh gap
     * @return the record
     */
    static QualificationRecord qualification(Map<Topic, BigDecimal> freshAgreements,
            Map<Topic, BigDecimal> warmAgreements, BigDecimal gap) {
        var forms = new LinkedHashMap<RoleForm, QualificationRecord.FormResult>();
        forms.put(RoleForm.FRESH, form(freshAgreements, ITEM_COUNT));
        if (!warmAgreements.isEmpty()) {
            forms.put(RoleForm.WARM, form(warmAgreements, ITEM_COUNT));
        }
        return new QualificationRecord(ROLE, DIGEST, configuration(), CORPUS_REVISION, List.of(), forms,
                warmAgreements.isEmpty() ? null : gap, 0, GRADED_AT, List.of());
    }

    /**
     * @param agreements the agreement of each topic
     * @param baseline   the constant baseline of every topic
     * @return the record with that baseline and no item results
     */
    static QualificationRecord recordWithBaseline(Map<Topic, BigDecimal> agreements, BigDecimal baseline) {
        var form = new QualificationRecord.FormResult(
                topics(agreements, baseline, ITEM_COUNT), ITEM_COUNT, TokenTotal.measured(TOTAL_TOKENS),
                DURATION_MILLIS);
        return new QualificationRecord(ROLE, DIGEST, configuration(), CORPUS_REVISION, List.of(),
                Map.of(RoleForm.FRESH, form), null, 0, GRADED_AT, List.of());
    }

    static QualificationRecord.FormResult form(Map<Topic, BigDecimal> agreements, int items) {
        return new QualificationRecord.FormResult(topics(agreements, BigDecimal.ZERO, items), items,
                TokenTotal.measured(TOTAL_TOKENS), DURATION_MILLIS);
    }

    private static Map<Topic, QualificationRecord.TopicResult> topics(Map<Topic, BigDecimal> agreements,
            BigDecimal baseline, int items) {
        var topics = new LinkedHashMap<Topic, QualificationRecord.TopicResult>();
        agreements.forEach((topic, agreement) ->
                topics.put(topic, new QualificationRecord.TopicResult(agreement, baseline, items)));
        return topics;
    }

    /** @return the graded members of the fixture role */
    static GradedConfiguration configuration() {
        return new GradedConfiguration(HARNESS, VERSION, MODEL, null,
                List.of("pm_wait", "pm_state", "pm_do", "pm_skill", "pm_skill_file"), RoleClass.READ_ONLY,
                Map.of("skill://pm/role/review-code/SKILL.md", DIGEST));
    }

    /** @return the topic set of an own-and-generic role */
    static RoleTopics bothTopics() {
        return new RoleTopics(EnumSet.of(Topic.OWN, Topic.GENERIC));
    }

    /** @return the topic set of a role with generic items alone */
    static RoleTopics genericOnly() {
        return new RoleTopics(EnumSet.of(Topic.GENERIC));
    }

    /** @return the items of a corpus with one own and one generic item */
    static List<ImportedItem> corpus() {
        return List.of(item(OWN_TOPIC + "/" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT), ROLE,
                        CORPUS_ANSWER).build(),
                item(GENERIC_TOPIC + "/" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT),
                        GENERIC_ROLE, CORPUS_ANSWER).build());
    }

    /**
     * @param name      the role name
     * @param roleClass the class
     * @param form      the form
     * @return an effective role with those values on the fixture harness
     */
    static EffectiveRole role(String name, RoleClass roleClass,
            RoleForm form) {
        var tools = roleClass == RoleClass.ZERO_TOOL
                ? "[]"
                : "[\"pm_wait\",\"pm_state\",\"pm_do\",\"pm_skill\",\"pm_skill_file\"]";
        var recycling = form == RoleForm.WARM
                ? ",\"recycling\":{\"idle_wakeups\":null,\"token_budget\":%d,\"tasks_per_generation\":null}".formatted(
                Generators.integers(10_000, 100_000).next())
                : "";
        var description = Generators.letterStrings(10, 60).next();
        var count = Generators.integers(1, 16).next();
        var budgetFloor = Generators.integers(60, 3_600).next();
        var json = """
                {"format_version":1,"name":"%s","description":"%s","class":"%s","tools":%s,
                 "harness":"%s","model":"%s","effort":null,"form":"%s","count":%d,
                 "skills":["skill://pm/role/%s/SKILL.md"],"budget_floor_seconds":%d%s}
                """.formatted(name, description, roleClass.wireName(), tools, HARNESS, MODEL, form.wireName(), count,
                name, budgetFloor, recycling);
        var artifact = RoleArtifactValidator.validate(
                json.getBytes(StandardCharsets.UTF_8), name);
        var classes = new EnumMap<RoleClass,
                HarnessProfile.ToolSet>(
                RoleClass.class);
        classes.put(RoleClass.WRITER,
                HarnessProfile.ToolSet.ENFORCED);
        classes.put(RoleClass.READ_ONLY,
                HarnessProfile.ToolSet.ADVISORY);
        classes.put(RoleClass.ZERO_TOOL,
                HarnessProfile.ToolSet.ENFORCED);
        var profile = new HarnessProfile(HARNESS, true, true, Set.of("*"),
                classes, Map.of(MODEL, List.of("medium", "high"), JUDGE_MODEL, List.of("medium", "high")),
                Map.of("frontier", FRONTIER_MODEL, "standard", MODEL, "fast", FAST_MODEL), CONTEXT_BUDGET,
                IDLE_WAKEUPS, new HarnessProfile.Resumability(true, MAX_HOLD_SECONDS, true, MAX_RESUME_SECONDS),
                "ask_user");
        return EffectiveRole.of(artifact, HARNESS, MODEL,
                new EffortValue(null),
                List.of(new RoleSkill("skill://pm/role/" + name + "/SKILL.md", DIGEST)),
                VERSION, null, profile);
    }

    /** @return the qualification settings at the compiled defaults */
    static QualificationSettings settings() {
        return QualificationSettings.defaults();
    }

    /**
     * @param level the agreement level
     * @return the settings with that level
     */
    static QualificationSettings settings(int level) {
        return new QualificationSettings(level,
                QualificationSettings.DEFAULT_ALLOWED_GAP, List.of(), false);
    }

    /**
     * @param results the item results
     * @return their answers, in order
     */
    static List<String> answers(List<QualificationRecord.ItemResult> results) {
        var answers = new ArrayList<String>();
        for (var result : results) {
            result.answer().text().ifPresent(answers::add);
        }
        return answers;
    }

    /** @return an empty previous record */
    static Optional<QualificationRecord> noPrevious() {
        return Optional.empty();
    }
}
