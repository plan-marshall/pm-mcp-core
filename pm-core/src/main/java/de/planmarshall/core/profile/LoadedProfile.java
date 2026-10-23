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

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A profile the loader read and validated, with the state its verification record gives it (PM-CLIENT-5, Profile Sets).
 * <p>
 * A shipped profile is always {@link State#VERIFIED}: the release compiled its document. A machine or project profile
 * is {@code VERIFIED} when its record and sidecar match the document and the enrolled harness build;
 * {@code VERIFIED_ON_OTHER_BUILD} when the record was made on the harness build the profile supports but not on the
 * build enrolled now, in which case launches go on; and it is not loaded at all when it does not count, in which case
 * the profile of the next set stays in force.
 *
 * @param clientId the client id of the profile
 * @param kind     the set it was read from
 * @param document the validated tree
 * @param bytes    the document, UTF-8 encoded, as its digest and sidecar are computed over it
 * @param state    the state its verification record gives it
 * @param verificationRecord the verification record the profile was bound to; {@code null} for a shipped profile
 * @since 0.1
 */
public record LoadedProfile(String clientId, ProfileSetKind kind, JsonNode document, byte[] bytes, State state,
VerificationRecord verificationRecord) {

    @Override
    public boolean equals(Object other) {
        return other instanceof LoadedProfile(String otherClientId, ProfileSetKind otherKind, JsonNode otherDocument,
                byte[] otherBytes, State otherState, VerificationRecord otherRecord)
                && Objects.equals(clientId, otherClientId) && kind == otherKind
                && Objects.equals(document, otherDocument) && Arrays.equals(bytes, otherBytes) && state == otherState
                && Objects.equals(verificationRecord, otherRecord);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, kind, document, Arrays.hashCode(bytes), state, verificationRecord);
    }

    @Override
    public String toString() {
        return "LoadedProfile[clientId=" + clientId + ", kind=" + kind + ", document=" + document + ", bytes=["
                + bytes.length + " bytes], state=" + state + ", verificationRecord=" + verificationRecord + "]";
    }

    /** The state a verification record gives a profile. The set is closed. */
    public enum State {
        /** The record and its sidecar match the document and the enrolled harness build. */
        VERIFIED("verified"),
        /** The record was made on another build of the enrolled harness, inside the supported range. */
        VERIFIED_ON_OTHER_BUILD("verified_on_other_build");

        private final String wireName;

        State(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the state in a representation */
        public String wireName() {
            return wireName;
        }
    }

    /** Copies the document bytes. */
    public LoadedProfile {
        bytes = bytes.clone();
    }

    /** @return the canonical-JSON digest of the profile, {@code sha256:} and 64 lower-case hexadecimal digits */
    public String digest() {
        return CanonicalJson.digest(document);
    }

    /**
     * @param headlessPath a path below {@code /headless}, for example {@code /classes/writer}
     * @return the value at that path; empty if the profile has no headless block
     */
    public Optional<JsonNode> headless(String headlessPath) {
        return document.member("headless")
                .filter(headless -> !headless.isNull())
                .map(headless -> JsonPointer.resolve(headless, headlessPath, clientId));
    }

    /**
     * @param name a role class, {@code writer}, {@code read_only} or {@code zero_tool}
     * @return whether the profile declares that class runnable
     */
    public boolean classRunnable(String name) {
        return headless("/classes").flatMap(classes -> classes.member(name))
                .map(value -> !value.isNull())
                .orElse(false);
    }
}
