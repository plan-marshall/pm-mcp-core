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

import de.cuioss.tools.logging.CuiLogger;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import de.planmarshall.core.config.Consent;
import de.planmarshall.core.config.ConsentGate;
import de.planmarshall.core.config.ConsentKind;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.credentials.CredentialStoreCheck.CheckLine;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Refusal;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The engine half of the credential-store aspect (PM-CRED-2, State Catalogue § Credential Store): the states
 * {@code store.detect}, {@code store.choose}, {@code store.consent}, {@code store.check},
 * {@code store.confirm-move}, {@code store.move} and {@code store.record}, each as one call that answers the next state.
 * The states themselves are units of the machine's onboarding, which lives in {@code plan-marshall-mcp}; this class is
 * what those units run, and it is provable over fixtures without a workflow.
 * <p>
 * <b>The consent comes first.</b> The OS store is checked only through the consent state {@code store.consent}, whose
 * kind {@link ConsentKind#CHECK_CREDENTIAL_STORE} is asked with {@link #consent}: an answer that does not allow the check
 * leaves the scope in {@code store.consent} and writes nothing. The file store is checked without a consent, because it
 * lies in the machine's own directory, so {@code store.choose} reaches {@code store.check} for it directly.
 * <p>
 * <b>A failed move keeps the old store.</b> {@link #move} runs the delivered {@link CredentialStoreMove}, which switches
 * {@code credentials.store} last and leaves the old store recorded and untouched on any failure before the switch; the
 * aspect reports that state as {@code store.unavailable} with {@code credential_store_failed}, the old store kept.
 * <p>
 * No secret reaches a log record, an exception message or a transition: the check's lines carry the store's own answer
 * and the encrypted entry never leaves the store.
 *
 * @since 0.1
 */
public final class CredentialStoreAspect {

    /** The step of the machine's onboarding that records the store, whose states this class runs. */
    public static final String STEP = "store";

    /** The reason of a store that failed, of {@code store.unavailable}. */
    public static final String CREDENTIAL_STORE_FAILED = "credential_store_failed";

    /** The reason of an OS store that is not available now, of {@code store.choose}. */
    public static final String OS_UNAVAILABLE = "os_unavailable";

    private static final CuiLogger LOGGER = new CuiLogger(CredentialStoreAspect.class);

    /** The states of the aspect, each with the id the catalogue gives it. */
    public enum State {
        /** {@code store.choose}: the offered stores, with what protects a secret in each. */
        CHOOSE("store.choose"),
        /** {@code store.consent}: the consent {@code check_credential_store}, before the OS store is checked. */
        CONSENT("store.consent"),
        /** {@code store.check}: write, read back and delete one test entry. */
        CHECK("store.check"),
        /** {@code store.confirm-move}: the count of the entries a change of the store would move. */
        CONFIRM_MOVE("store.confirm-move"),
        /** {@code store.move}: every entry into the other store, the switch last. */
        MOVE("store.move"),
        /** {@code store.record}: {@code credentials.store}, last. */
        RECORD("store.record"),
        /** {@code store.done}: the aspect's terminal state. */
        DONE("store.done"),
        /** {@code store.unavailable}: why the store cannot be used, with the fix. */
        UNAVAILABLE("store.unavailable");

        private final String stateId;

        State(String stateId) {
            this.stateId = stateId;
        }

        /** @return the id of the state in the state catalogue */
        public String stateId() {
            return stateId;
        }
    }

    /**
     * What {@code store.detect} found.
     *
     * @param offered          the stores the machine offers now, in the order of the choice
     * @param moveInterrupted  whether a move an earlier run began is not complete
     * @since 0.1
     */
    public record Detection(List<Store> offered, boolean moveInterrupted) {

        /** Keeps the offered stores unmodifiable. */
        public Detection {
            offered = List.copyOf(offered);
        }
    }

    /**
     * What one state answered: the next state and what the state produced.
     *
     * @param next        the next state
     * @param reason      the reason code of an unavailable store, or empty
     * @param detail      why the store is unavailable, or empty
     * @param toRecord    the consent to record through {@code ConsentStore}, for a {@code yes_remember}
     * @param lines       the check lines of {@code store.check}, empty for the other states
     * @param moveEntries the count of entries a move would carry, for {@code store.confirm-move}
     * @since 0.1
     */
    public record Transition(State next, Optional<String> reason, Optional<String> detail,
    Optional<Consent> toRecord, List<CheckLine> lines, OptionalInt moveEntries) {

        /** Keeps the lists unmodifiable and rejects {@code null} components. */
        public Transition {
            Objects.requireNonNull(next, "next");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
            Objects.requireNonNull(toRecord, "toRecord");
            lines = List.copyOf(lines);
            Objects.requireNonNull(moveEntries, "moveEntries");
        }

        static Transition to(State next) {
            return new Transition(next, Optional.empty(), Optional.empty(), Optional.empty(), List.of(),
                    OptionalInt.empty());
        }

        static Transition unavailable(String reason, String detail) {
            return new Transition(State.UNAVAILABLE, Optional.of(reason), Optional.of(detail), Optional.empty(),
                    List.of(), OptionalInt.empty());
        }

        /** @return whether the store is usable after this state */
        public boolean usable() {
            return next != State.UNAVAILABLE;
        }
    }

    private final Path base;
    private final CredentialStoreChoice choice;
    private final LockManager locks;
    private final String user;
    private final CredentialStoreCheck check;
    private final CredentialStoreMove move;

    /**
     * @param base      the base directory of the runtime
     * @param choice    the credential store as the operator's recorded choice
     * @param locks     the lock manager of the runtime
     * @param auditSink where the completed move is recorded
     * @param user      the name of the running user, the owner of the files a move and its verification read
     * @param clock     the clock of a granted consent and of a move
     * @param random    the source of a check's test value
     */
    public CredentialStoreAspect(Path base, CredentialStoreChoice choice, LockManager locks, AuditSink auditSink,
            String user, Clock clock, SecureRandom random) {
        this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.choice = Objects.requireNonNull(choice, "choice");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.user = Objects.requireNonNull(user, "user");
        this.check = new CredentialStoreCheck(random);
        this.move = new CredentialStoreMove(this.base, choice, this.locks, auditSink, this.user, clock);
    }

    /**
     * {@code store.detect}: which stores the machine offers now, and whether a move an earlier run began is not
     * complete.
     *
     * @return what the state found
     */
    public Detection detect() {
        var offered = new ArrayList<Store>();
        if (choice.osStore().isPresent()) {
            offered.add(Store.OS);
        }
        offered.add(Store.FILE);
        return new Detection(offered, Files.exists(CredentialStoreMoveRecord.file(base)));
    }

    /**
     * {@code store.choose}: the store the operator chose. An OS store that is not available now is not offered, so the
     * choice answers {@code os_unavailable}; a file store is checked without a consent.
     *
     * @param store the store the operator chose
     * @return the next state: {@code store.consent} for an OS store, {@code store.check} for the file store
     */
    public Transition choose(Store store) {
        Objects.requireNonNull(store, STEP);
        if (store == Store.OS && choice.osStore().isEmpty()) {
            return Transition.unavailable(OS_UNAVAILABLE,
                    "the OS credential store '%s' is unavailable now".formatted(choice.osStoreName()));
        }
        return Transition.to(store == Store.OS ? State.CONSENT : State.CHECK);
    }

    /**
     * {@code store.consent}: the answer to the consent {@code check_credential_store}. Only a {@code yes} or a
     * {@code yes_remember} reaches {@code store.check}; a {@code no} records nothing and leaves the scope in the consent
     * state.
     *
     * @param gate     the gate read from the consent store before the question
     * @param answer   the answer the operator gave
     * @param channel  the channel it came through
     * @param deviceId the paired device, for an answer through the web app
     * @return the next state, with the consent to record for a {@code yes_remember}
     * @throws IllegalArgumentException if the answer cannot belong to the kind, its scope or its channel
     */
    public Transition consent(ConsentGate gate, ConsentGate.Answer answer, Consent.Channel channel,
            Optional<String> deviceId) {
        var decision = Objects.requireNonNull(gate, "gate").answer(
                ConsentGate.Request.machine(ConsentKind.CHECK_CREDENTIAL_STORE), answer, channel, deviceId);
        if (!decision.runs()) {
            return Transition.to(State.CONSENT);
        }
        return new Transition(State.CHECK, Optional.empty(), Optional.empty(), decision.toRecord(), List.of(),
                OptionalInt.empty());
    }

    /**
     * {@code store.check}: writes, reads back and deletes one test entry in the chosen store, and decides from the
     * result and the recorded store: a passed check with no store recorded, or with the chosen store recorded, reaches
     * {@code store.record}; a passed check with another store recorded reaches {@code store.confirm-move}; a failed
     * check reaches {@code store.unavailable}.
     *
     * @param store the store to check
     * @return the next state, with the check's lines
     */
    public Transition check(Store store) {
        Objects.requireNonNull(store, STEP);
        var result = check.check(choice.backendOf(store));
        if (!result.passed()) {
            var last = result.lines().getLast();
            return new Transition(State.UNAVAILABLE, Optional.of(last.messageKey()),
                    Optional.of(String.join(" ", last.arguments())), Optional.empty(), result.lines(),
                    OptionalInt.empty());
        }
        LOGGER.info(INFO.CREDENTIAL_STORE_CHECK_PASSED, store.value());
        var recorded = choice.recorded();
        if (recorded.isPresent() && recorded.get() != store) {
            return new Transition(State.CONFIRM_MOVE, Optional.empty(), Optional.empty(), Optional.empty(),
                    result.lines(), OptionalInt.empty());
        }
        return new Transition(State.RECORD, Optional.empty(), Optional.empty(), Optional.empty(), result.lines(),
                OptionalInt.empty());
    }

    /**
     * {@code store.confirm-move}: the count of the entries the move would carry from the recorded store to the new one.
     *
     * @return the next state {@code store.move} with the count, or {@code store.unavailable} if the recorded store
     *         cannot be read
     */
    public Transition confirmMove() {
        try {
            return new Transition(State.MOVE, Optional.empty(), Optional.empty(), Optional.empty(), List.of(),
                    OptionalInt.of(move.recordedEntries()));
        } catch (Refusal | SecretStoreException | StoreRefusal | UncheckedIOException e) {
            return Transition.unavailable(CREDENTIAL_STORE_FAILED, e.getMessage());
        }
    }

    /**
     * {@code store.move}: moves every entry into {@code to} and switches {@code credentials.store} last. A move that
     * fails before the switch keeps the old store recorded and untouched, and is reported as
     * {@code credential_store_failed} with the old store kept; a machine configuration that cannot be read, a record
     * that cannot be written and a first recording that cannot carry the key fail the same way.
     *
     * @param to the store to move to
     * @return the next state {@code store.record}, or {@code store.unavailable} with the failure
     */
    public Transition move(Store to) {
        Objects.requireNonNull(to, "to");
        try {
            move.move(to);
            return Transition.to(State.RECORD);
        } catch (SecretStoreException | StoreRefusal | UncheckedIOException e) {
            LOGGER.warn(WARN.CREDENTIAL_STORE_ASPECT_MOVE_FAILED, e.getMessage());
            return Transition.unavailable(CREDENTIAL_STORE_FAILED, e.getMessage());
        } catch (Refusal e) {
            return Transition.unavailable(e.reason().code(), e.getMessage());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return Transition.unavailable(CREDENTIAL_STORE_FAILED, e.getMessage());
        }
    }

    /**
     * {@code store.record}: records {@code credentials.store} last, after the store passed its check or the move ended.
     *
     * @param store the store the aspect records
     * @return the terminal state {@code store.done}, or {@code store.unavailable} with the refusal
     */
    public Transition recordStore(Store store) {
        Objects.requireNonNull(store, STEP);
        try {
            choice.record(locks, user, store);
            return Transition.to(State.DONE);
        } catch (Refusal e) {
            return Transition.unavailable(e.reason().code(), e.getMessage());
        } catch (StoreRefusal | SecretStoreException | UncheckedIOException e) {
            return Transition.unavailable(CREDENTIAL_STORE_FAILED, e.getMessage());
        }
    }

    /**
     * Resumes a store operation a stop cut, which {@code store.detect} reported as {@code move_interrupted}: the next
     * start finishes the first recording or the move it finds.
     *
     * @return the operation that was resumed, or empty when none was open
     */
    public Optional<CredentialStoreMoveRecord> resume() {
        return move.resume();
    }
}
