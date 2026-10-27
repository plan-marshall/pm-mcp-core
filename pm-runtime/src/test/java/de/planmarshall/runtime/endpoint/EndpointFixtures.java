/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.launch.HarnessVerifier;

/**
 * An endpoint profile and its harness enrolment, for the tests of the endpoint harness.
 */
final class EndpointFixtures {

    static final String BASE_URL = "http://127.0.0.1:11434";

    static final String FOREIGN_EXECUTABLE = "/usr/local/bin/other";

    private EndpointFixtures() {
    }

    /**
     * @param baseUrl the origin of the endpoint
     * @return the endpoint profile
     */
    static EndpointProfile profile(String baseUrl) {
        return profile(baseUrl, "enforced");
    }

    /**
     * @param baseUrl      the origin of the endpoint
     * @param resultSchema what the endpoint does with a result schema
     * @return the endpoint profile
     */
    static EndpointProfile profile(String baseUrl, String resultSchema) {
        return new EndpointProfile("local-runner", "ollama",
                new EndpointProfile.Identity(List.of("--version"), "version ([0-9.]+)"),
                new EndpointProfile.UpdateEvidence("none", null),
                new EndpointProfile.Endpoint("openai_chat", "loopback", baseUrl, "/ready", "/models", "/models",
                        resultSchema, 8192, 1024, 30),
                Map.of("frontier", "big", "standard", "mid", "fast", "small"),
                new EndpointProfile.WeightsProbe("models_listing", "/digest", null, null));
    }

    /** @return the endpoint profile of the fixture origin */
    static EndpointProfile profile() {
        return profile(BASE_URL);
    }

    /** @return the absolute path of the program the test runs in, which the owner check can find and digest */
    static String executablePath() {
        return ProcessHandle.current().info().command().orElseThrow();
    }

    /**
     * @param executablePath the absolute path of the serving program
     * @return the enrolment of the harness, with a digest the fixture does not compute
     */
    static HarnessEnrolment enrolment(String executablePath) {
        return new HarnessEnrolment("local-runner", Path.of(executablePath), "a".repeat(64), "1.0.0",
                Instant.EPOCH, Instant.EPOCH,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of()), Map.of(), Map.of());
    }

    /**
     * @param executablePath the absolute path of the program the owner check found
     * @return the enrolment that accepts that program's path and digest
     */
    static HarnessEnrolment enrolled(String executablePath) {
        var digest = HarnessVerifier.digestExecutable(Path.of(executablePath)).orElseThrow();
        return new HarnessEnrolment("local-runner", Path.of(executablePath), digest, "1.0.0", Instant.EPOCH,
                Instant.EPOCH,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of()), Map.of(), Map.of());
    }

    /** @return the enrolment that accepts the test process's own executable */
    static HarnessEnrolment enrolment() {
        return enrolled(executablePath());
    }
}
