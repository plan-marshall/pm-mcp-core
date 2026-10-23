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

import java.util.List;
import java.util.Objects;

import de.planmarshall.core.role.RoleResolution;
import de.planmarshall.core.role.RoleResolver;

/**
 * The composition check of {@code model-work/01-roles-and-configuration.adoc#role-composition} (PM-WORK-1): when the
 * runtime composes a plan instance, an epic workflow or the project setup workflow, it resolves every role the unit names
 * in the project's effective set, with the plan's overrides applied.
 * <p>
 * A role that is absent or invalid refuses the composition with {@code role_undefined}, naming the role, the state and the
 * validation reason where there is one; a role whose class does not fit its use, and a consulted {@code zero_tool} role,
 * refuse it with {@code class_mismatch}, naming the role, its class, the state and the required class. The scope is not
 * created, and no state is reached that names the role.
 *
 * @since 0.1
 */
public final class RoleComposition {

    private final RoleResolver resolver;

    /**
     * @param resolver the resolver of the plan's effective role set
     */
    public RoleComposition(RoleResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * @param uses every role the composed unit names
     * @throws CompositionRefusal at the first role the composition cannot use
     */
    public void check(List<RoleUse> uses) {
        for (var use : uses) {
            var resolution = resolver.resolve(use.role());
            if (resolution instanceof RoleResolution.Undefined undefined) {
                throw CompositionRefusal.roleUndefined(use.role(), use.stateId(), undefined.reason(),
                        undefined.detail());
            }
            var role = ((RoleResolution.Defined) resolution).role();
            if (!use.acceptedClasses().contains(role.roleClass())) {
                throw CompositionRefusal.classMismatch(use.role(), use.stateId(), role.roleClass(),
                        use.acceptedClasses());
            }
        }
    }
}
