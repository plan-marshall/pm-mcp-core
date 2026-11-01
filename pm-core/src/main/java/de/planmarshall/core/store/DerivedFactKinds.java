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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.role.corpus.SchemaSubset;

/**
 * The registry through which a consumer binds a {@link DerivedFactKind} to its {@link DerivedFactKindSpec} (Store
 * Schemas § Derived Facts Store, the kind table; PM-EXT-6).
 * <p>
 * The registry is an instance and never a static: the composition that owns a store creates it, registers the kinds it
 * derives and hands it on. No concrete kind is registered by this plan, in production code or in a default registry:
 * {@code module_discovery} belongs to the module discovery, {@code ci_provider} and {@code base_branch_default} to the
 * CI contract, the onboarding facts to the onboarding plans and {@code cost_calibration} to the recalibration, each
 * registering its kind with the value schema of the specification when it lands (ADR {@code core-store/053}).
 * <p>
 * A kind is registered once: a second registration is refused. A kind that was never registered has no default: its
 * {@link #spec(DerivedFactKind)} is empty and {@link #validate(DerivedFactKind, JsonValue)} refuses it, so a value is
 * never validated against a schema nobody registered.
 *
 * @since 0.1
 */
public final class DerivedFactKinds {

    private final Map<DerivedFactKind, DerivedFactKindSpec> specs = new ConcurrentHashMap<>();

    /**
     * Registers the specification of a kind.
     *
     * @param kind the kind
     * @param spec the specification of the kind
     * @throws NullPointerException  if the kind or the specification is {@code null}
     * @throws IllegalStateException if the kind is already registered
     */
    public void register(DerivedFactKind kind, DerivedFactKindSpec spec) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(spec, "spec");
        if (specs.putIfAbsent(kind, spec) != null) {
            throw new IllegalStateException("The fact kind '%s' is already registered".formatted(kind.wireName()));
        }
    }

    /**
     * @param kind the kind
     * @return the specification of the kind; empty if no specification was registered for it
     */
    public Optional<DerivedFactKindSpec> spec(DerivedFactKind kind) {
        Objects.requireNonNull(kind, "kind");
        return Optional.ofNullable(specs.get(kind));
    }

    /**
     * Validates a value against the value schema registered for its kind.
     *
     * @param kind  the kind
     * @param value the value
     * @return the violations of {@link SchemaSubset#validate(JsonValue, JsonValue)} of the registered schema; empty if
     *         the value is valid
     * @throws NullPointerException  if the kind or the value is {@code null}
     * @throws IllegalStateException if the kind has no registered specification
     */
    public List<String> validate(DerivedFactKind kind, JsonValue value) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        var spec = specs.get(kind);
        if (spec == null) {
            throw new IllegalStateException(
                    "The fact kind '%s' has no registered value schema".formatted(kind.wireName()));
        }
        return SchemaSubset.validate(value, spec.schema());
    }

    /**
     * @param kind the kind
     * @return {@code true} if the kind is operator-only, whether it is registered or not
     */
    public boolean isOperatorOnly(DerivedFactKind kind) {
        Objects.requireNonNull(kind, "kind");
        return kind.operatorOnly();
    }
}
