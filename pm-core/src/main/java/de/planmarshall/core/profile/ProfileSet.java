/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The effective profile set of one project (PM-CLIENT-5, Profile Sets): one profile per harness id, each the document of
 * the nearest set that counts, never merged with another, beside the profiles that were refused and the one whose
 * refusal left the next set in force.
 * <p>
 * The set is data: the loader decides once, at load, and a caller reads the effective profile or the state it found.
 * A refused profile is kept with its refusal so that both clients and {@code pm-operator profile show} can report it.
 *
 * @since 0.1
 */
public final class ProfileSet {

    private final Map<String, LoadedProfile> effective;
    private final List<RefusedProfile> refused;

    ProfileSet(Map<String, LoadedProfile> effective, List<RefusedProfile> refused) {
        this.effective = Collections.unmodifiableMap(new LinkedHashMap<>(effective));
        this.refused = List.copyOf(refused);
    }

    /**
     * @param harnessId a harness id
     * @return the effective profile of the client, from the nearest set that counts
     */
    public Optional<LoadedProfile> effective(String harnessId) {
        return Optional.ofNullable(effective.get(harnessId));
    }

    /** @return the effective profiles by harness id; unmodifiable, in client-id order */
    public Map<String, LoadedProfile> effective() {
        return effective;
    }

    /** @return the profiles that were refused, with the refusal that left the next set in force */
    public List<RefusedProfile> refused() {
        return refused;
    }

    /**
     * @param harnessId a harness id
     * @return the state of the effective profile; empty if the client has no profile that counts
     */
    public Optional<LoadedProfile.State> state(String harnessId) {
        return effective(harnessId).map(LoadedProfile::state);
    }

    /**
     * @param harnessId a harness id
     * @return whether the effective profile was verified on another build of the enrolled harness
     */
    public boolean verifiedOnOtherBuild(String harnessId) {
        return state(harnessId).filter(LoadedProfile.State.VERIFIED_ON_OTHER_BUILD::equals).isPresent();
    }

    /**
     * A profile the loader refused.
     *
     * @param harnessId the harness id the profile names
     * @param kind     the set it was read from
     * @param refusal  the refusal
     * @since 0.1
     */
    public record RefusedProfile(String harnessId, ProfileSetKind kind, ProfileRefusal refusal) {
    }
}
