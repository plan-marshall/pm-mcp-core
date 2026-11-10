/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.easymock.EasyMock.createMock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.profile.ProfileSetKind;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.testing.generators.CoreGenerators;
import de.planmarshall.runtime.launch.HarnessEnrolmentStore;
import de.planmarshall.runtime.launch.HarnessVerifier;
import de.planmarshall.runtime.launch.JobTokenStore;
import de.planmarshall.runtime.launch.LaunchComposer;
import de.planmarshall.runtime.launch.SkillSource;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builder of {@link SupervisorEnvironment} builds the value its canonical constructor builds, including
 * its null refusal for the project id. Every value is generated; the interfaces are mocks that the builder only stores,
 * the enrolment is drawn from the generator artifact of {@code pm-core}, and the three landed final collaborators are
 * constructed over a mock lock manager and a fixed key.
 */
@EnableGeneratorController
@DisplayName("SupervisorEnvironment builder")
class SupervisorEnvironmentRecordsTest {

    @Test
    @DisplayName("the builder builds the supervisor environment the constructor builds")
    void supervisorEnvironmentBuilderEqualsConstructor() {
        var projectId = RuntimeGenerators.projectIds().next();
        var enrolment = CoreGenerators.projectEnrolments().next();
        var base = Path.of("/tmp", Generators.letterStrings(1, 12).next());
        var home = Path.of("/tmp", Generators.letterStrings(1, 12).next());
        var runtimeHome = Path.of("/tmp", Generators.letterStrings(1, 12).next());
        var relaySocket = Path.of("/tmp", Generators.letterStrings(1, 12).next(), "pm.sock");
        var shimParagraph = Generators.letterStrings(1, 40).next();
        SkillSource skills = createMock(SkillSource.class);
        var bytes = "{}".getBytes(StandardCharsets.UTF_8);
        var profile = new LoadedProfile("claude", ProfileSetKind.SHIPPED, JsonDocument.parse(bytes, "profile"), bytes,
                LoadedProfile.State.VERIFIED, null);
        LockManager locks = createMock(LockManager.class);
        AuditSink audit = createMock(AuditSink.class);
        InstantSource clock = createMock(InstantSource.class);
        var key = new SecretKeySpec("0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var store = new HarnessEnrolmentStore(Path.of("/tmp", "harness"), () -> key, locks, "tester");
        var harnessVerifier = new HarnessVerifier(store, file -> Optional.empty(),
                (file, argv, pattern) -> Optional.empty(), (evidence, file) -> false, audit, clock);
        var composer = new LaunchComposer();
        SupervisorEnvironment.WorkerStarter starter = createMock(SupervisorEnvironment.WorkerStarter.class);
        SupervisorEnvironment.WorkerStateWriter stateWriter =
                createMock(SupervisorEnvironment.WorkerStateWriter.class);
        var jobTokens = new JobTokenStore();
        ModelSlots slots = createMock(ModelSlots.class);
        WorkerProtocol protocol = createMock(WorkerProtocol.class);
        WorkerTimers timers = createMock(WorkerTimers.class);
        SupervisorEnvironment.StartGuard startGuard = createMock(SupervisorEnvironment.StartGuard.class);
        SupervisorEnvironment.EligibilityGate eligibilityGate =
                createMock(SupervisorEnvironment.EligibilityGate.class);

        var constructed = new SupervisorEnvironment(projectId, enrolment, base, home, runtimeHome, relaySocket,
                shimParagraph, skills, profile, harnessVerifier, composer, starter, stateWriter, jobTokens, slots,
                protocol, audit, clock, timers, locks, startGuard, eligibilityGate);
        var built = SupervisorEnvironment.builder()
                .projectId(projectId)
                .enrolment(enrolment)
                .base(base)
                .home(home)
                .runtimeHome(runtimeHome)
                .relaySocket(relaySocket)
                .shimParagraph(shimParagraph)
                .skills(skills)
                .profile(profile)
                .harnessVerifier(harnessVerifier)
                .composer(composer)
                .starter(starter)
                .stateWriter(stateWriter)
                .jobTokens(jobTokens)
                .slots(slots)
                .protocol(protocol)
                .audit(audit)
                .clock(clock)
                .timers(timers)
                .locks(locks)
                .startGuard(startGuard)
                .eligibilityGate(eligibilityGate)
                .build();

        assertEquals(constructed, built, "the builder builds the same supervisor environment");
    }

    @Test
    @DisplayName("the builder refuses a missing project id like the constructor")
    void supervisorEnvironmentBuilderRefusesMissingProjectId() {
        var refusal = assertThrows(NullPointerException.class, () -> SupervisorEnvironment.builder().build());

        assertEquals("projectId", refusal.getMessage(), "the refusal names the project id");
    }
}
