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

import static de.planmarshall.runtime.job.JobEnrolmentFixture.randomContent;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.sha256;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.runtime.job.JobRefusal.Reason;
import de.planmarshall.runtime.job.ProbeCandidate.CheckoutRoot;
import de.planmarshall.runtime.job.ProbeCandidate.DigestedFile;
import de.planmarshall.runtime.job.ProbeCandidate.JobPrivate;

/**
 * Step 0 of a probe job (PM-EXEC-1, PM-EXEC-3): the verification against the candidate record refuses a changed
 * executable or interpreter, a working directory that is not the canonical one of the probe and a changed argument
 * vector, each against its matched control that passes, and the candidate record and the fixed budgets refuse what
 * they rule out.
 */
@EnableGeneratorController
@DisplayName("ProbeVerification")
class ProbeVerificationTest {

    private static final Scope ENTRY = new Scope(Scope.Type.ENTRY, "entry-one");
    private static final Scope MACHINE = new Scope(Scope.Type.MACHINE, null);

    @TempDir
    Path temporary;

    private Path executable;
    private Path interpreter;
    private Path checkout;
    private Path jobPrivate;
    private Path outside;
    private List<String> arguments;

    @BeforeEach
    void setUp() throws IOException {
        var root = temporary.toRealPath();
        executable = Files.write(root.resolve("probe"), randomContent());
        interpreter = Files.write(root.resolve("interpreter"), randomContent());
        checkout = Files.createDirectories(root.resolve("checkout"));
        Files.createDirectories(checkout.resolve(ProbeVerification.GIT));
        jobPrivate = Files.createDirectories(root.resolve("run/jobs/v-0123456789abcdef/cwd"));
        outside = Files.createDirectories(root.resolve("outside"));
        arguments = List.of("--" + Generators.letterStrings(3, 10).next());
    }

    private ProbeCandidate candidate(Scope scope, ProbeCandidate.WorkingDirectory workingDirectory) {
        return new ProbeCandidate(ProbeCandidate.Kind.VERSION, new DigestedFile(executable, sha256(executable)),
                Optional.of(new DigestedFile(interpreter, sha256(interpreter))), arguments, Set.of(0), scope,
                workingDirectory);
    }

    private ProbeCandidate inCheckout() {
        return candidate(ENTRY, new CheckoutRoot(checkout));
    }

    private JobRefusal refused(ProbeVerification.Result result) {
        return assertInstanceOf(ProbeVerification.Refused.class, result, "the probe should be refused").refusal();
    }

    @Nested
    @DisplayName("matched controls")
    class Controls {

        @Test
        @DisplayName("a probe of a checkout runs in the canonical checkout root with the declared vector")
        void checkout() {
            var result = ProbeVerification.verify(inCheckout(), arguments, checkout, jobPrivate);

            var verified = assertInstanceOf(ProbeVerification.Verified.class, result);
            var argv = new ArrayList<String>(List.of(interpreter.toString(), executable.toString()));
            argv.addAll(arguments);
            assertAll(
                    () -> assertEquals(checkout, verified.workingDirectory()),
                    () -> assertEquals(argv, verified.argv()));
        }

        @Test
        @DisplayName("a probe of the machine runs in its job-private directory")
        void jobPrivate() {
            var result = ProbeVerification.verify(candidate(MACHINE, new JobPrivate()), arguments, jobPrivate,
                    jobPrivate);

            assertEquals(jobPrivate, assertInstanceOf(ProbeVerification.Verified.class, result).workingDirectory());
        }
    }

    @Nested
    @DisplayName("refuses a changed program as executable_digest_mismatch")
    class Digests {

        @Test
        @DisplayName("an executable whose content changed after the scan")
        void executableChanged() throws IOException {
            var candidate = inCheckout();
            Files.write(executable, randomContent());

            var refusal = refused(ProbeVerification.verify(candidate, arguments, checkout, jobPrivate));

            assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH, refusal.reason());
        }

        @Test
        @DisplayName("an interpreter whose content changed after the scan")
        void interpreterChanged() throws IOException {
            var candidate = inCheckout();
            Files.write(interpreter, randomContent());

            var refusal = refused(ProbeVerification.verify(candidate, arguments, checkout, jobPrivate));

            assertAll(
                    () -> assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH, refusal.reason()),
                    () -> assertEquals("the interpreter does not match the digest of its candidate record",
                            refusal.detail()));
        }

        @Test
        @DisplayName("an executable that is gone, before the working directory is looked at")
        void executableMissingFirst() throws IOException {
            var candidate = inCheckout();
            Files.delete(executable);

            var refusal = refused(ProbeVerification.verify(candidate, arguments, outside, jobPrivate));

            assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH, refusal.reason());
        }
    }

    @Nested
    @DisplayName("refuses a working directory that is not the probe's as path_traversal_rejected")
    class WorkingDirectories {

        @Test
        @DisplayName("a directory outside the checkout")
        void outsideCheckout() {
            var refusal = refused(ProbeVerification.verify(inCheckout(), arguments, outside, jobPrivate));

            assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refusal.reason());
        }

        @Test
        @DisplayName("a symbolic link in the checkout that escapes it")
        void symlinkEscape() throws IOException {
            var link = Files.createSymbolicLink(checkout.resolve("escape"), outside);

            var refusal = refused(ProbeVerification.verify(inCheckout(), arguments, link, jobPrivate));

            assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refusal.reason());
        }

        @Test
        @DisplayName("a symbolic link that resolves to the checkout root itself")
        void symlinkToRoot() throws IOException {
            var link = Files.createSymbolicLink(outside.resolve("root"), checkout);

            var refusal = refused(ProbeVerification.verify(inCheckout(), arguments, link, jobPrivate));

            assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refusal.reason());
        }

        @Test
        @DisplayName("a checkout root that is no git top-level")
        void noGitTopLevel() throws IOException {
            Files.delete(checkout.resolve(ProbeVerification.GIT));

            var refusal = refused(ProbeVerification.verify(inCheckout(), arguments, checkout, jobPrivate));

            assertAll(
                    () -> assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refusal.reason()),
                    () -> assertEquals("the checkout root is no git top-level", refusal.detail()));
        }

        @Test
        @DisplayName("a probe of the machine asked to run anywhere but its job-private directory")
        void notJobPrivate() {
            var refusal = refused(ProbeVerification.verify(candidate(MACHINE, new JobPrivate()), arguments, checkout,
                    jobPrivate));

            assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refusal.reason());
        }

        @Test
        @DisplayName("a working directory that does not exist")
        void missing() {
            var refusal = refused(ProbeVerification.verify(inCheckout(), arguments, checkout.resolve("missing"),
                    jobPrivate));

            assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refusal.reason());
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"added", "changed", "removed"})
    @DisplayName("refuses a vector other than the declared one as argument_rejected")
    void changedVector(String change) {
        var asked = new ArrayList<>(arguments);
        switch (change) {
            case "added" -> asked.add(Generators.letterStrings(3, 10).next());
            case "changed" -> asked.set(0, asked.getFirst() + "x");
            default -> asked.clear();
        }

        var refusal = refused(ProbeVerification.verify(inCheckout(), asked, checkout, jobPrivate));

        assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason());
    }

    @Nested
    @DisplayName("the candidate record")
    class Candidate {

        @Test
        @DisplayName("belongs to the machine or an onboarding entry, and only an entry runs in a checkout")
        void scope() {
            var digested = new DigestedFile(executable, sha256(executable));
            var workspace = new Scope(Scope.Type.WORKSPACE, null);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new ProbeCandidate(
                            ProbeCandidate.Kind.VERSION, digested, Optional.empty(), List.of(), Set.of(0), workspace,
                            new JobPrivate())),
                    () -> assertThrows(IllegalArgumentException.class, () -> new ProbeCandidate(
                            ProbeCandidate.Kind.VERSION, digested, Optional.empty(), List.of(), Set.of(0), MACHINE,
                            new CheckoutRoot(checkout))),
                    () -> assertThrows(IllegalArgumentException.class, () -> new ProbeCandidate(
                            ProbeCandidate.Kind.VERSION, digested, Optional.empty(), List.of(), Set.of(), MACHINE,
                            new JobPrivate())));
        }

        @Test
        @DisplayName("names its files by absolute path and lower-case SHA-256")
        void digestedFile() {
            var digest = sha256(executable);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new DigestedFile(Path.of("probe"), digest)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new DigestedFile(executable, digest.toUpperCase(Locale.ROOT))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new CheckoutRoot(Path.of("checkout"))));
        }

        @Test
        @DisplayName("declares its vector without an interpreter for a program that runs by itself")
        void declaredArgv() {
            var candidate = new ProbeCandidate(ProbeCandidate.Kind.LOGIN, new DigestedFile(executable,
                    sha256(executable)), Optional.empty(), arguments, Set.of(0), MACHINE, new JobPrivate());

            assertEquals(executable.toString(), candidate.declaredArgv().getFirst());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ProbeCandidate.Kind.class)
        @DisplayName("every kind is found by its name")
        void kindNames(ProbeCandidate.Kind kind) {
            assertEquals(Optional.of(kind), ProbeCandidate.Kind.fromWireName(kind.wireName()));
        }
    }

    @ParameterizedTest(name = "{0}: {1} seconds")
    @CsvSource({"VERSION, probe:version, 15", "MODELS, probe:models, 30", "LOGIN, probe:login, 120",
            "SCRIPT, probe:script, 600"})
    @DisplayName("the fixed budget of every kind of probe is a compiled constant, never learned")
    void budgets(ProbeCandidate.Kind kind, String key, long seconds) {
        var budget = ProbeBudget.of(kind);

        assertAll(
                () -> assertEquals(key, budget.key()),
                () -> assertEquals(seconds, budget.seconds()),
                () -> assertEquals(BudgetDerivation.Kind.FLOOR_DEFAULT, budget.derivation().kind()));
    }
}
