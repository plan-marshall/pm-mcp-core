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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Builder;

import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.role.corpus.JsonValue.JsonObject;

/**
 * What a consumer registers for a fact kind (Store Schemas § Derived Facts Store, the kind table; PM-EXT-6): the value
 * schema, the {@code source.class} values the kind admits, whether the key may carry a qualifier, and the description
 * of the derivation inputs.
 * <p>
 * The schema is the {@link JsonValue} tree of the kind's value, which {@link SchemaSubset} validates; it must be a JSON
 * object, because every value schema of the specification is one. A kind admits at least one source class: a value
 * obtained from the repository or the machine is {@code local}, one obtained from a provider is {@code network}, and
 * the store refuses an entry whose class the kind does not admit.
 *
 * @param schema           the value schema of the kind, a JSON object
 * @param classes          the {@code source.class} values the kind admits; non-empty
 * @param qualifierAllowed whether a key of the kind may carry a qualifier
 * @param inputs           the description of the derivation inputs, as the kind table of the specification states it
 * @since 0.1
 */
@Builder(toBuilder = true)
public record DerivedFactKindSpec(
JsonValue schema,
Set<DerivedFact.DerivationClass> classes,
boolean qualifierAllowed,
String inputs) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the schema is not a JSON object, the class set is empty, or the inputs are
     *                                  blank
     */
    public DerivedFactKindSpec {
        Objects.requireNonNull(schema, "schema");
        if (!(schema instanceof JsonObject)) {
            throw new IllegalArgumentException("The value schema of a fact kind must be a JSON object");
        }
        Objects.requireNonNull(classes, "classes");
        classes = Collections.unmodifiableSet(new LinkedHashSet<>(classes));
        if (classes.isEmpty()) {
            throw new IllegalArgumentException("A fact kind admits at least one source class");
        }
        Objects.requireNonNull(inputs, "inputs");
        if (inputs.isBlank()) {
            throw new IllegalArgumentException("The derivation inputs of a fact kind are described");
        }
    }
}
