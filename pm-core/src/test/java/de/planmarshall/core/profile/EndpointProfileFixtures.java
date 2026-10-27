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

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A valid endpoint profile and its variants, for the tests of the endpoint profile schema (Client Profiles § Endpoint
 * Profile Schema).
 */
public final class EndpointProfileFixtures {

    private EndpointProfileFixtures() {
    }

    /** The valid document, at the shape the specification's schema block states. */
    public static final String VALID = """
            {
              "profile_kind": "endpoint",
              "schema_version": 1,
              "client_id": "local-runner",
              "display_name": "Local Runner",
              "min_daemon_version": "0.1.0",
              "icon": null,
              "executable": "ollama",
              "identity": {"version_probe": ["--version"], "version_pattern": "version ([0-9.]+)"},
              "update_evidence": {"method": "none", "signer": null},
              "endpoint": {
                "protocol": "openai_chat",
                "locality": "loopback",
                "base_url": "http://127.0.0.1:11434",
                "credential_key": null,
                "readiness_path": "/api/tags",
                "models_path": "/api/tags",
                "models_pointer": "/models",
                "result_schema": "enforced",
                "context_tokens": 8192,
                "answer_reserve_tokens": 1024,
                "request_timeout_seconds": 60
              },
              "model_tiers": {"frontier": "big", "standard": "mid", "fast": "small"},
              "weights_probe": {"source": "models_listing", "pointer": "/digest", "argv": null, "pattern": null}
            }
            """;

    /**
     * @param document a profile document
     * @return the validated tree
     */
    public static JsonNode validate(String document) {
        return ProfileValidator.validate(document.getBytes(UTF_8), "profiles/local-runner.json", ProfileRules.none());
    }

    /** @return the validated endpoint profile */
    public static JsonNode valid() {
        return validate(VALID);
    }

    /** @return the loaded endpoint profile */
    public static LoadedProfile loaded() {
        var bytes = VALID.getBytes(UTF_8);
        return new LoadedProfile("local-runner", ProfileSetKind.MACHINE,
                ProfileValidator.validate(bytes, "profiles/local-runner.json", ProfileRules.none()), bytes,
                LoadedProfile.State.VERIFIED, null);
    }

    /**
     * @param from the text of the valid document to replace
     * @param to   the replacement
     * @return the variant document
     */
    public static String variant(String from, String to) {
        return VALID.replace(from, to);
    }
}
