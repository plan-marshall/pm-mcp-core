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
import java.util.Optional;

/**
 * The check of the OS store before it is recorded (PM-CRED-2): one test entry under the fixed global account
 * {@value #ACCOUNT} is written, read back and compared, and deleted, each step reported as one check line.
 * <p>
 * A failed step ends the check: what was written is deleted, and the check records nothing. The consent
 * {@code check_credential_store} and the states of the store step stay with the onboarding; this class only runs the
 * three steps on the store it is given. The test value is random, never logged and never part of a check line.
 *
 * @since 0.1
 */
public final class CredentialStoreCheck {

    /** The global account of the test entry. */
    public static final String ACCOUNT = "credential-store-check";

    /** The prefix of every message key of a check line. */
    static final String MESSAGE_KEY_PREFIX = "credential_store_check.";

    private static final int VALUE_BYTES = 32;

    /** The last part of the message key of a step the store failed with its own answer. */
    private static final String WHAT_FAILED = "failed";

    private final SecureRandom random;

    /** The step of the check. */
    public enum Step {
        /** The test entry is written. */
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
     * @param answer     the store's own answer where there is one: the reason code and message of its failure
     */
    public record CheckLine(Step step, Outcome outcome, String messageKey, List<String> arguments,
    Optional<String> answer) {

        /** Keeps the arguments unmodifiable. */
        public CheckLine {
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(messageKey, "messageKey");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(answer, "answer");
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
     * Writes, reads back and deletes the test entry in the given store.
     *
     * @param store the OS store to check
     * @return the result
     */
    Result check(SecretStore store) {
        var account = CredentialAccount.global(ACCOUNT);
        var value = testValue();
        var lines = new ArrayList<CheckLine>();
        try {
            store.put(account, value);
        } catch (SecretStoreException e) {
            lines.add(failed(Step.WRITE, WHAT_FAILED, store.name(), e));
            cleanUp(store, account);
            return new Result(lines);
        }
        lines.add(ok(Step.WRITE, store.name()));
        try {
            var read = store.get(account);
            if (read.isEmpty() || !read.get().equals(value)) {
                lines.add(new CheckLine(Step.READ_BACK, Outcome.FAILED,
                        key(Step.READ_BACK, read.isEmpty() ? "missing" : "differs"), arguments(store.name()),
                        Optional.empty()));
                cleanUp(store, account);
                return new Result(lines);
            }
        } catch (SecretStoreException e) {
            lines.add(failed(Step.READ_BACK, WHAT_FAILED, store.name(), e));
            cleanUp(store, account);
            return new Result(lines);
        }
        lines.add(ok(Step.READ_BACK, store.name()));
        try {
            if (!store.delete(account)) {
                lines.add(new CheckLine(Step.DELETE, Outcome.FAILED, key(Step.DELETE, "missing"),
                        arguments(store.name()), Optional.empty()));
                return new Result(lines);
            }
        } catch (SecretStoreException e) {
            lines.add(failed(Step.DELETE, WHAT_FAILED, store.name(), e));
            cleanUp(store, account);
            return new Result(lines);
        }
        lines.add(ok(Step.DELETE, store.name()));
        return new Result(lines);
    }

    /** Deletes the test entry after a failed step; a store that fails here too has its failure line already. */
    private static void cleanUp(SecretStore store, CredentialAccount account) {
        try {
            store.delete(account);
        } catch (SecretStoreException _) {
            // the failed step is reported; the entry the store refuses to delete is the store's own state
        }
    }

    private String testValue() {
        var bytes = new byte[VALUE_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static CheckLine ok(Step step, String store) {
        return new CheckLine(step, Outcome.OK, key(step, "ok"), arguments(store), Optional.empty());
    }

    private static CheckLine failed(Step step, String what, String store, SecretStoreException e) {
        return new CheckLine(step, Outcome.FAILED, key(step, what), arguments(store),
                Optional.of(e.reason().code() + ": " + e.getMessage()));
    }

    private static String key(Step step, String what) {
        return MESSAGE_KEY_PREFIX + step.checkName() + "." + what;
    }

    private static List<String> arguments(String store) {
        return List.of(store, ACCOUNT);
    }
}
