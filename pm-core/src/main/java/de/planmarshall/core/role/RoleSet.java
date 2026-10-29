/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The effective role set of one project (PM-WORK-1, Role Sets &amp; Precedence): one artifact per role name, each the
 * artifact of the nearest set that defines it, never merged with another, beside the artifacts that were refused.
 * <p>
 * The set is data: the loader decides once, at each load, and a caller reads the effective artifact, the set it came from
 * and the refusal. A role that a higher set defines with an invalid artifact is <em>absent</em> from the effective set: an
 * invalid role counts as undefined and does not fall back to the artifact of a lower set.
 *
 * @since 0.1
 */
public final class RoleSet {

    private final Map<String, RoleArtifact> effective;
    private final Map<String, RoleSetKind> kinds;
    private final List<RefusedRole> refused;

    RoleSet(Map<String, RoleArtifact> effective, Map<String, RoleSetKind> kinds, List<RefusedRole> refused) {
        this.effective = Collections.unmodifiableMap(new LinkedHashMap<>(effective));
        this.kinds = Collections.unmodifiableMap(new LinkedHashMap<>(kinds));
        this.refused = List.copyOf(refused);
    }

    /**
     * @param name a role name
     * @return the effective artifact of the role, from the nearest set that defines it
     */
    public Optional<RoleArtifact> effective(String name) {
        return Optional.ofNullable(effective.get(name));
    }

    /** @return the effective artifacts by role name; unmodifiable, in name order */
    public Map<String, RoleArtifact> effective() {
        return effective;
    }

    /**
     * The roles a role assignment names: every effective artifact except the machine's advisor. The steward is not among
     * the roles {@code roles.load} lists, as no assignment or override names it — it runs on the machine's default
     * harness and that harness's tier {@code standard} (Model Work § Steward Role).
     *
     * @return the assignable artifacts by role name; unmodifiable, in name order
     */
    public Map<String, RoleArtifact> assignable() {
        var assignable = new LinkedHashMap<>(effective);
        assignable.remove(RoleCatalog.STEWARD);
        return Collections.unmodifiableMap(assignable);
    }

    /**
     * @param name a role name
     * @return the set the effective artifact was read from; empty for a role the set does not define
     */
    public Optional<RoleSetKind> kind(String name) {
        return Optional.ofNullable(kinds.get(name));
    }

    /** @return the artifacts that are absent because an artifact was refused, with the refusal */
    public List<RefusedRole> refused() {
        return refused;
    }

    /**
     * @param name a role name
     * @return the refusal that left the role undefined; empty when the role is defined or absent without a refusal
     */
    public Optional<Refusal> refusal(String name) {
        for (var index = refused.size() - 1; index >= 0; index--) {
            var refusedRole = refused.get(index);
            if (refusedRole.name().equals(name)) {
                return Optional.of(new Refusal(refusedRole.kind(), refusedRole.refusal()));
            }
        }
        return Optional.empty();
    }

    /**
     * One artifact the loader refused.
     *
     * @param name    the role name the file names
     * @param kind    the set it was read from
     * @param refusal the refusal
     * @since 0.1
     */
    public record RefusedRole(String name, RoleSetKind kind, RoleRefusal refusal) {
    }

    /**
     * The refusal of one role name, with the set it was read from.
     *
     * @param kind    the set it was read from
     * @param refusal the refusal
     * @since 0.1
     */
    public record Refusal(RoleSetKind kind, RoleRefusal refusal) {
    }
}
