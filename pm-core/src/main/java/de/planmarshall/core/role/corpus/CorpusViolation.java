/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import java.util.Objects;

/**
 * One violation the {@link CorpusValidator} reports (PM-WORK-6): what it concerns, the rule it breaks, and what is
 * wrong.
 *
 * @param subject the id of the item, or the path of the file relative to the corpus root, it concerns; {@code items}
 *                for the generated items directory of the cache root
 * @param rule    the rule it breaks
 * @param detail  what is wrong
 * @since 0.1
 */
public record CorpusViolation(String subject, Rule rule, String detail) {

    /**
     * The rules of the corpus. The set is closed.
     *
     * @since 0.1
     */
    public enum Rule {
        /**
         * A file of the corpus cannot be read, the corpus root, its {@code sources} entry or the generated
         * {@code items} of the cache root is no directory, or a directory cannot be listed.
         */
        UNREADABLE("unreadable"),
        /** The item schema {@code schema/verification-item.schema.json} of the corpus root is absent or no JSON. */
        ITEM_SCHEMA("item_schema"),
        /** An item is no JSON document. */
        NOT_JSON("not_json"),
        /** An item breaks the item schema of the corpus. */
        SCHEMA("schema"),
        /** A generated item breaks {@code imported-item.schema.json}. */
        IMPORTED_ITEM("imported_item"),
        /** The id of an item differs from its path below the corpus without {@code .json}. */
        ID_PATH("id_path"),
        /** The topic of an item differs from its directory. */
        TOPIC_DIRECTORY("topic_directory"),
        /** Two items have one id. */
        DUPLICATE_ID("duplicate_id"),
        /** An item lacks the corpus canary. */
        CANARY("canary"),
        /** The answer of a closed item is not one of its choices. */
        CLOSED_ANSWER("closed_answer"),
        /** A rubric item set is absent from an item that is not a control. */
        RUBRIC_ITEMS("rubric_items"),
        /** A context file an item names does not exist. */
        CONTEXT_FILE("context_file"),
        /** The provenance, review, grading or tags of an item name a transient tool, stage or local path. */
        TRANSIENT_NAME("transient_name"),
        /** The manifest of a source is absent or breaks its format. */
        MANIFEST("manifest"),
        /** The overlay of a source is absent or breaks its format. */
        OVERLAY("overlay"),
        /** {@code ATTRIBUTION.md} lacks the one section of a source, or has it twice. */
        ATTRIBUTION("attribution"),
        /** Third-party text is stored below the corpus: a source file, or an imported item. */
        THIRD_PARTY_TEXT("third_party_text");

        private final String wireName;

        Rule(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the rule in a report */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * Requires every value.
     */
    public CorpusViolation {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(detail, "detail");
    }

    @Override
    public String toString() {
        return "%s: [%s] %s".formatted(subject, rule.wireName(), detail);
    }
}
