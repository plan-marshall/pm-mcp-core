/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.unit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.verify.EntityKindRegistry;
import de.planmarshall.workflow.verify.QuestionClassRegistry;
import de.planmarshall.workflow.verify.SchemaRegistry;
import de.planmarshall.workflow.verify.WorkflowCatalog;
import de.planmarshall.workflow.verify.WorkflowValidator;

/**
 * What the static verifier of the loaded units reads (PM-IMPL-5): the registries the assembly supplies and the units
 * no project can own, such as the entity units, which the catalog of every verification holds beside the loaded
 * units.
 *
 * @param registry        the primitives a unit may name
 * @param entityKinds     the registered entity kinds
 * @param questionClasses the registered question classes
 * @param schemas         the registered named parameter schemas
 * @param compiledUnits   the units compiled into the release that no project owns
 * @since 0.1
 */
public record VerificationContext(PrimitiveRegistry registry, EntityKindRegistry entityKinds,
QuestionClassRegistry questionClasses, SchemaRegistry schemas, List<WorkflowDefinition> compiledUnits) {

    /**
     * Refuses a missing component and copies the compiled units.
     *
     * @param registry        the primitives
     * @param entityKinds     the entity kinds
     * @param questionClasses the question classes
     * @param schemas         the schemas
     * @param compiledUnits   the units no project owns
     * @throws NullPointerException if a component or an element is {@code null}
     */
    public VerificationContext {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(entityKinds, "entityKinds");
        Objects.requireNonNull(questionClasses, "questionClasses");
        Objects.requireNonNull(schemas, "schemas");
        compiledUnits = List.copyOf(compiledUnits);
    }

    /**
     * @param loadedUnits    the units being loaded, which their calls name
     * @param releasedStates the state names of earlier releases; empty to leave Rule 5 out
     * @return the static verifier over the registries and a catalog of the compiled and the loaded units
     */
    WorkflowValidator validator(Collection<WorkflowDefinition> loadedUnits, Collection<String> releasedStates) {
        var catalog = new ArrayList<>(compiledUnits);
        catalog.addAll(loadedUnits);
        return new WorkflowValidator(registry, releasedStates, entityKinds, questionClasses, schemas,
                new WorkflowCatalog(catalog));
    }
}
