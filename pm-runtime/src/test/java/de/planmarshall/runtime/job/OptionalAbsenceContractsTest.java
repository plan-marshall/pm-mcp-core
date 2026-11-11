/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.config.MachineDefaultsValidation;
import de.planmarshall.runtime.config.ProviderDefault;
import de.planmarshall.runtime.credentials.CredentialAccount;
import de.planmarshall.runtime.enrolment.HarnessesSidecarCheck;
import de.planmarshall.runtime.worker.WorkerRecycling;

/**
 * The absent case of the public record components this conversion changed: a {@code null} input is the absence that
 * {@code Optional.empty()} carried before, and the accessor returns it unchanged.
 *
 * @since 0.1
 */
class OptionalAbsenceContractsTest {

    @Test
    @DisplayName("a survivor without an executable keeps null")
    void survivorWithoutCommand() {
        assertNull(new ProcessTreeTracker.Survivor(1L, null).command(),
                "a survivor without a command is absent");
    }

    @Test
    @DisplayName("a global credential account has no project")
    void globalCredentialAccount() {
        assertNull(new CredentialAccount(null, "github").project(),
                "a global account has no project");
    }

    @Test
    @DisplayName("a read from a later offset carries no launch report")
    void linesWithoutReport() {
        assertNull(new JobLog.Lines(List.of("line"), 5L, null).report(),
                "a later read carries no launch report");
    }

    @Test
    @DisplayName("a composer without a session bus keeps null")
    void composerWithoutSessionBus() {
        var composer = new JobConfinementComposer(Path.of("/base"), Path.of("/tmp"), Path.of("/dev"), null);

        assertNull(composer.sessionBusSocket(), "a platform without a session bus is absent");
    }

    @Test
    @DisplayName("a valid machine default has no reason")
    void validVerdictHasNoReason() {
        assertNull(new MachineDefaultsValidation.Verdict(true, null, "ok").reason(),
                "a valid verdict carries no reason");
    }

    @Test
    @DisplayName("a provider default without a variant or key keeps both null")
    void providerDefaultWithoutVariant() {
        var providerDefault = new ProviderDefault(false, null, null, null);

        assertAll(
                () -> assertNull(providerDefault.variant(), "a provider without a variant is absent"),
                () -> assertNull(providerDefault.credentialKey(), "a provider without a key is absent"));
    }

    @Test
    @DisplayName("a passed sidecar check names no file and no reason")
    void passedSidecarCheck() {
        var result = new HarnessesSidecarCheck.Result(HarnessesSidecarCheck.Outcome.VERIFIED, null, null);

        assertAll(
                () -> assertNull(result.file(), "a pass names no file"),
                () -> assertNull(result.reason(), "a pass names no reason"));
    }

    @Test
    @DisplayName("a whole-tree host tool build has no module")
    void hostToolBuildWithoutModule() {
        assertNull(new JobRequest.HostToolBuild("test", null).module(),
                "a whole-tree build has no module");
    }

    @Test
    @DisplayName("a wait-again result without a recycle cause keeps null")
    void waitAgainWithoutCause() {
        assertNull(new WorkerRecycling.WaitAgainResult(3L, null).recycleCause(),
                "a wait without recycling has no cause");
    }
}
