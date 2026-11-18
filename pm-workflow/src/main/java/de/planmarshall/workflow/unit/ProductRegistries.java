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

import java.util.List;

import org.jspecify.annotations.Nullable;

import de.planmarshall.core.config.ConfigValueDomain;
import de.planmarshall.core.config.EffectiveConfigKey;
import de.planmarshall.core.config.EffectiveConfigKeys;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.store.DerivedFactKind;
import de.planmarshall.workflow.ast.InputType;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.verify.ConfigKeyRegistry;
import de.planmarshall.workflow.verify.EntityKindRegistry;
import de.planmarshall.workflow.verify.FactKindRegistry;
import de.planmarshall.workflow.verify.QuestionClassRegistry;
import de.planmarshall.workflow.verify.SchemaRegistry;
import lombok.experimental.UtilityClass;

/**
 * The production registries of the static verifier (PM-IMPL-5, PM-WF-16): the closed configuration keys of Store Schemas
 * 07 and 08 and the derived fact kinds a unit may read, assembled with the compiled defaults into the one
 * {@link VerificationContext} the three verifier call sites read.
 * <p>
 * This class is the ONE place that builds a production context (ADR {@code workflow-unit}: one factory, no hidden
 * default). The empty registries stay the default of the existing test constructors, so a unit that declares a read in a
 * context built without this factory fails closed.
 * <p>
 * The closed configuration lives in {@code pm-core} ({@code de.planmarshall.core.config}) and the fact kinds in
 * {@code de.planmarshall.core.store}; this class converts both into the registries of {@code workflow.verify} and names
 * neither of them back, so nothing in {@code pm-core} knows {@code pm-workflow}.
 *
 * @since 0.1
 */
@UtilityClass
public final class ProductRegistries {

    /**
     * @return the keys of the closed configuration, each with the value domain its schema states and whether a compiled
     *         default exists
     */
    public static ConfigKeyRegistry configKeys() {
        return ConfigKeyRegistry.of(EffectiveConfigKeys.all().stream().map(ProductRegistries::configKey).toList());
    }

    /**
     * @return the fact kinds of the closed enumeration, by their wire names
     */
    public static FactKindRegistry factKinds() {
        return FactKindRegistry.of(List.of(DerivedFactKind.values()).stream().map(DerivedFactKind::wireName).toList());
    }

    /**
     * The production verification context: the default entity kinds, question classes and schemas plus the two registries
     * of this class, over the primitives and the compiled units the assembly supplies.
     *
     * @param registry      the primitives a unit may name
     * @param compiledUnits the units compiled into the release that no project owns
     * @return the context the three verifier call sites read
     */
    public static VerificationContext context(PrimitiveRegistry registry, List<WorkflowDefinition> compiledUnits) {
        return new VerificationContext(registry, EntityKindRegistry.defaults(), QuestionClassRegistry.defaults(),
                SchemaRegistry.defaults(), configKeys(), factKinds(), compiledUnits);
    }

    /**
     * The closed input type that expresses a configuration domain, or none.
     *
     * @param domain the domain of a configuration key
     * @return the input type, or {@code null} when no closed input type expresses the domain
     */
    static @Nullable InputType inputType(ConfigValueDomain domain) {
        return switch (domain) {
            case ConfigValueDomain.Enumeration(var values) -> new InputType.Enumeration(values);
            case ConfigValueDomain.IntegerRange(var min, var max) when min != null && max != null ->
                new InputType.IntRange(min, max);
            case ConfigValueDomain.IntegerRange(var min, var max) when min == null && max == null ->
                new InputType.IntRange(null, null);
            case ConfigValueDomain.ListOf(ConfigValueDomain.Enumeration(var values)) -> new InputType.EnumList(values);
            default -> null;
        };
    }

    private static ConfigKeyRegistry.ConfigKey configKey(EffectiveConfigKey key) {
        return new ConfigKeyRegistry.ConfigKey(key.key(), inputType(key.domain()), key.hasCompiledDefault());
    }
}
