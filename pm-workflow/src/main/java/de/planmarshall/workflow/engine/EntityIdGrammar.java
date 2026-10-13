/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The grammar of an addressed entity identifier and of the forms it takes (PM-ARCH-4, PM-WF-1). This is the only place
 * of these checks: the port that reads a pending entry record receives an {@code entry_id} already checked here.
 * <ul>
 * <li>entity identifier: {@code ^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$}, 1 to 64 characters;</li>
 * <li>{@code entry_id}, the entity of a plan or epic bootstrap under the workspace scope:
 * {@code ^ent-[0-9a-f]{8,32}$}; an entity that begins with {@code ent-} is an {@code entry_id} and must match it;</li>
 * <li>{@code enrich-<module_id>}, the entity of an architecture enrichment task, with {@code module_id}
 * {@code ^[A-Za-z0-9][A-Za-z0-9_.-]{0,56}$}, bounded so the entity stays within 64 characters; an entity that begins
 * with {@code enrich-} must carry such a {@code module_id};</li>
 * <li>{@code queue_task_id}, the entity of a task whose state names no scope entity: {@code ^qt-[0-9a-f]{16}$}.</li>
 * </ul>
 * Identifiers are compared byte-exactly; no case is folded.
 */
final class EntityIdGrammar {

    /** The prefix of an {@code entry_id}. */
    static final String ENTRY_PREFIX = "ent-";

    /** The prefix of the entity of an architecture enrichment task. */
    static final String ENRICH_PREFIX = "enrich-";

    private static final String ENTITY_REGEX = "^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$";
    private static final String ENTRY_ID_REGEX = "^ent-[0-9a-f]{8,32}$";
    private static final String MODULE_ID_REGEX = "^[A-Za-z0-9][A-Za-z0-9_.-]{0,56}$";
    private static final String QUEUE_TASK_ID_REGEX = "^qt-[0-9a-f]{16}$";

    private static final Pattern ENTITY = Pattern.compile(ENTITY_REGEX);
    private static final Pattern ENTRY_ID = Pattern.compile(ENTRY_ID_REGEX);
    private static final Pattern MODULE_ID = Pattern.compile(MODULE_ID_REGEX);
    private static final Pattern QUEUE_TASK_ID = Pattern.compile(QUEUE_TASK_ID_REGEX);

    /**
     * The form of a valid entity identifier.
     */
    enum Form {
        /** An {@code entry_id}: a pending plan or epic bootstrap. */
        ENTRY,
        /** {@code enrich-<module_id>}: an architecture enrichment task. */
        ENRICH,
        /** A {@code queue_task_id}: a task whose state names no scope entity. */
        QUEUE_TASK,
        /** Any other entity of a task. */
        OTHER
    }

    private EntityIdGrammar() {
    }

    /**
     * @param entity the supplied entity identifier
     * @return why the identifier violates its grammar; empty when it is valid
     */
    static Optional<String> violation(String entity) {
        if (entity.startsWith(ENTRY_PREFIX) && !ENTRY_ID.matcher(entity).matches()) {
            return Optional.of("entry_id does not match %s: %s".formatted(ENTRY_ID_REGEX, entity));
        }
        if (entity.startsWith(ENRICH_PREFIX)
                && !MODULE_ID.matcher(entity.substring(ENRICH_PREFIX.length())).matches()) {
            return Optional.of("module_id of %s does not match %s".formatted(entity, MODULE_ID_REGEX));
        }
        if (!ENTITY.matcher(entity).matches()) {
            return Optional.of("entity does not match %s: %s".formatted(ENTITY_REGEX, entity));
        }
        return Optional.empty();
    }

    /**
     * @param entity an entity identifier for which {@link #violation(String)} is empty
     * @return the form of the identifier
     */
    static Form formOf(String entity) {
        if (entity.startsWith(ENTRY_PREFIX)) {
            return Form.ENTRY;
        }
        if (entity.startsWith(ENRICH_PREFIX)) {
            return Form.ENRICH;
        }
        return QUEUE_TASK_ID.matcher(entity).matches() ? Form.QUEUE_TASK : Form.OTHER;
    }
}
