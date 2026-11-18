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
import java.util.Optional;
import java.util.regex.Pattern;

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
import org.jspecify.annotations.Nullable;

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

    /** The form of an enum value a unit's frontmatter accepts, so a converted value is one a unit can declare. */
    private static final Pattern ENUM_VALUE = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

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
     * The closed input type that expresses a configuration domain.
     *
     * @param domain the domain of a configuration key
     * @return the input type, or empty when no closed input type expresses the domain
     */
    static Optional<InputType> inputType(ConfigValueDomain domain) {
        return switch (domain) {
            case ConfigValueDomain.Enumeration(var values) -> enumeration(values);
            case ConfigValueDomain.IntegerRange(var min, var max) -> range(min, max);
            case ConfigValueDomain.ListOf(var element) -> listOf(element);
            case ConfigValueDomain.Bool _ -> Optional.empty();
            case ConfigValueDomain.Text _ -> Optional.empty();
            case ConfigValueDomain.Object _ -> Optional.empty();
            case ConfigValueDomain.Union _ -> Optional.empty();
        };
    }

    private static Optional<InputType> enumeration(List<String> values) {
        return validEnumValues(values)
                ? Optional.of(new InputType.Enumeration(values))
                : Optional.empty();
    }

    private static Optional<InputType> range(@Nullable Integer min, @Nullable Integer max) {
        if (min == null && max == null || min != null && max != null) {
            return Optional.of(new InputType.IntRange(min, max));
        }
        return Optional.empty();
    }

    private static Optional<InputType> listOf(ConfigValueDomain element) {
        return element instanceof ConfigValueDomain.Enumeration(var values) && validEnumValues(values)
                ? Optional.of(new InputType.EnumList(values))
                : Optional.empty();
    }

    private static boolean validEnumValues(List<String> values) {
        return values.stream().allMatch(value -> ENUM_VALUE.matcher(value).matches());
    }

    private static ConfigKeyRegistry.ConfigKey configKey(EffectiveConfigKey key) {
        return new ConfigKeyRegistry.ConfigKey(key.key(), inputType(key.domain()).orElse(null),
                key.hasCompiledDefault());
    }
}
