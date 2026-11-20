/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The check of the OS store before it is recorded (PM-CRED-2): the fixed account {@value #ACCOUNT} is read first, then one
 * test entry is written, read back and compared, and deleted, each step reported as one check line.
 * <p>
 * The test entry is check-owned: its value starts with {@value #MARKER}, which only this check writes. An account that
 * holds an entry with the marker is a leftover of an earlier check that stopped between the write and the delete; it is
 * deleted and the check runs. An account that holds any other value is foreign and fails the write step with
 * {@code credential_store_check.write.occupied}, and nothing is written or deleted: the check never overwrites a foreign
 * entry, and restoring an original value could lose it on a crash between the write and the restore. A failed step ends
 * the check: the entry it wrote is deleted, and only while it still holds the value this run wrote, and the check
 * records nothing.
 * <p>
 * The store offers no conditional write, so the read before the write is not atomic with it. The runs of this runtime
 * are serialized by one lock, and the runtime lock excludes a second runtime on the same base; a writer outside both
 * can still put an entry between the read and the write. The three deletes are guarded differently (runtime-credentials
 * ADR 014): the clean-up after a failed step deletes only an entry that still holds the value this run wrote; the
 * delete of a leftover deletes an entry whose value starts with {@value #MARKER}, whichever check wrote it; and the
 * delete step after a matching read-back deletes the entry unconditionally, so an entry a writer outside both locks put
 * between the read-back and the delete is deleted with it. The consent {@code check_credential_store} and
 * the states of the store step stay with the onboarding; this class only runs the three steps on the store it is
 * given. The test value is random, never logged and never part of a check line.
 *
 * @since 0.1
 */
public final class CredentialStoreCheck {

    /** The global account of the test entry. */
    public static final String ACCOUNT = "credential-store-check";

    /** The start of every value the check writes: what makes an entry under {@link #ACCOUNT} the check's own. */
    public static final String MARKER = "pm-mcp-credential-store-check:";

    /** The prefix of every message key of a check line. */
    static final String MESSAGE_KEY_PREFIX = "credential_store_check.";

    private static final int VALUE_BYTES = 32;

    /** Serializes the runs of this runtime, so no two of its checks interleave between a read and a write. */
    private static final ReentrantLock RUNS = new ReentrantLock();

    /** The last part of the message key of a step the store failed with its own answer. */
    private static final String WHAT_FAILED = "failed";

    private final SecureRandom random;

    /** The step of the check. */
    public enum Step {
        /** The account is read, then the test entry is written. */
        WRITE("write"),
        /** The test entry is read back and compared with what was written. */
        READ_BACK("read_back"),
        /** The test entry is deleted. */
        DELETE("delete");

        private final String checkName;

        Step(String checkName) {
            this.checkName = checkName;
        }

        /** @return the name of the check line */
        public String checkName() {
            return checkName;
        }
    }

    /** The outcome of one step. */
    public enum Outcome {
        /** The step succeeded. */
        OK("ok"),
        /** The step failed and ended the check. */
        FAILED(WHAT_FAILED);

        private final String code;

        Outcome(String code) {
            this.code = code;
        }

        /** @return the outcome in a check line */
        public String code() {
            return code;
        }
    }

    /**
     * One check line.
     *
     * @param step       the step
     * @param outcome    {@code ok} or {@code failed}
     * @param messageKey the message key of the line, {@code credential_store_check.<step>.<what>}
     * @param arguments  the arguments of the message: the store and the account
     * @param answer     the store's own answer where there is one: the reason code and message of its failure;
     *                   {@code null} when none
     */
    @Builder
    public record CheckLine(Step step, Outcome outcome, String messageKey, List<String> arguments,
    @Nullable String answer) {

        /** Keeps the arguments unmodifiable. */
        public CheckLine {
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(messageKey, "messageKey");
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * The result of a check.
     *
     * @param lines the check lines, one per step run, in order; the last one failed if the check failed
     */
    public record Result(List<CheckLine> lines) {

        /** Keeps the lines unmodifiable. */
        public Result {
            lines = List.copyOf(lines);
        }

        /** @return whether all three steps succeeded */
        public boolean passed() {
            return lines.size() == Step.values().length
                    && lines.stream().allMatch(line -> line.outcome() == Outcome.OK);
        }
    }

    /**
     * @param random the source of the test value
     */
    public CredentialStoreCheck(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Checks the OS store of the platform the choice names; an unavailable OS store fails the write step.
     *
     * @param choice the credential store choice of the machine
     * @return the result
     */
    public Result check(CredentialStoreChoice choice) {
        SecretStore store;
        try {
            store = choice.requireOsStore();
        } catch (SecretStoreException e) {
            return new Result(List.of(failed(Step.WRITE, "unavailable", choice.osStoreName(), e)));
        }
        return check(store);
    }

    /**
     * Reads the account, writes, reads back and deletes the test entry in the given store.
     *
     * @param store the OS store to check
     * @return the result
     */
    Result check(SecretStore store) {
        RUNS.lock();
        try {
            return run(store);
        } finally {
            RUNS.unlock();
        }
    }

    private Result run(SecretStore store) {
        var account = CredentialAccount.global(ACCOUNT);
        var value = testValue();
        var lines = new ArrayList<CheckLine>();
        try {
            var existing = store.get(account);
            if (existing.isPresent() && !existing.get().startsWith(MARKER)) {
                lines.add(CheckLine.builder()
                        .step(Step.WRITE)
                        .outcome(Outcome.FAILED)
                        .messageKey(key(Step.WRITE, "occupied"))
                        .arguments(arguments(store.name()))
                        .answer(null)
                        .build());
                return new Result(lines);
            }
            if (existing.isPresent()) {
                // a leftover of an earlier check: the marker says the check wrote it, so the check may delete it
                store.delete(account);
            }
        } catch (SecretStoreException e) {
            lines.add(failed(Step.WRITE, WHAT_FAILED, store.name(), e));
            return new Result(lines);
        }
        try {
            store.put(account, value);
        } catch (SecretStoreException e) {
            lines.add(failed(Step.WRITE, WHAT_FAILED, store.name(), e));
            cleanUp(store, account, value);
            return new Result(lines);
        }
        lines.add(ok(Step.WRITE, store.name()));
        try {
            var read = store.get(account);
            if (read.isEmpty() || !read.get().equals(value)) {
                lines.add(CheckLine.builder()
                        .step(Step.READ_BACK)
                        .outcome(Outcome.FAILED)
                        .messageKey(key(Step.READ_BACK, read.isEmpty() ? "missing" : "differs"))
                        .arguments(arguments(store.name()))
                        .answer(null)
                        .build());
                cleanUp(store, account, value);
                return new Result(lines);
            }
        } catch (SecretStoreException e) {
            lines.add(failed(Step.READ_BACK, WHAT_FAILED, store.name(), e));
            cleanUp(store, account, value);
            return new Result(lines);
        }
        lines.add(ok(Step.READ_BACK, store.name()));
        try {
            if (!store.delete(account)) {
                lines.add(CheckLine.builder()
                        .step(Step.DELETE)
                        .outcome(Outcome.FAILED)
                        .messageKey(key(Step.DELETE, "missing"))
                        .arguments(arguments(store.name()))
                        .answer(null)
                        .build());
                return new Result(lines);
            }
        } catch (SecretStoreException e) {
            lines.add(failed(Step.DELETE, WHAT_FAILED, store.name(), e));
            cleanUp(store, account, value);
            return new Result(lines);
        }
        lines.add(ok(Step.DELETE, store.name()));
        return new Result(lines);
    }

    /**
     * Deletes the test entry after a failed step, only while it still holds the value this run wrote. An entry another
     * writer put under the account before this read is kept. A writer that replaces the entry between this read and the
     * delete is not excluded: the store offers no conditional delete the runtime uses. A store that fails here too has
     * its failure line already.
     */
    private static void cleanUp(SecretStore store, CredentialAccount account, String value) {
        try {
            if (store.get(account).filter(value::equals).isPresent()) {
                store.delete(account);
            }
        } catch (SecretStoreException _) {
            // the failed step is reported; the entry the store refuses to read or delete is the store's own state
        }
    }

    private String testValue() {
        var bytes = new byte[VALUE_BYTES];
        random.nextBytes(bytes);
        return MARKER + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static CheckLine ok(Step step, String store) {
        return CheckLine.builder()
                .step(step)
                .outcome(Outcome.OK)
                .messageKey(key(step, "ok"))
                .arguments(arguments(store))
                .answer(null)
                .build();
    }

    private static CheckLine failed(Step step, String what, String store, SecretStoreException e) {
        return CheckLine.builder()
                .step(step)
                .outcome(Outcome.FAILED)
                .messageKey(key(step, what))
                .arguments(arguments(store))
                .answer(e.reason().code() + ": " + e.getMessage())
                .build();
    }

    private static String key(Step step, String what) {
        return MESSAGE_KEY_PREFIX + step.checkName() + "." + what;
    }

    private static List<String> arguments(String store) {
        return List.of(store, ACCOUNT);
    }
}
