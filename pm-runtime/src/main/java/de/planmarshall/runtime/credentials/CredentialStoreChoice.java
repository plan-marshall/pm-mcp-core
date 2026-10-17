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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.cuioss.tools.logging.CuiLogger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreSnapshot;
import de.planmarshall.core.store.StrictJson;
import de.planmarshall.runtime.credentials.dbus.DbusConnection;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The credential store as the operator's recorded choice (PM-CRED-2, PM-CRED-4): {@code credentials.store} of
 * {@code <PM_MCP_BASE>/machine-config.json}, {@code os} or {@code file}, absent until the operator chose. The runtime
 * never chooses a store by itself and never serves from another store than the recorded one.
 * <p>
 * <b>The setting.</b> {@link #record(LockTransaction, Store, Writer)} writes the key under
 * {@code locks/machine-config.lock}: inside the lock it re-reads the document, keeps every other member, increments
 * {@code revision} and replaces the file in one atomic write, so two writers of the file never interleave and no
 * check-then-act window opens outside the lock. An absent document is created with {@code format_version} 1,
 * {@code revision} 1 and the other required keys at their schema defaults ({@code max_build_slots}
 * {@value #DEFAULT_MAX_BUILD_SLOTS}, {@code lsp_idle_timeout_seconds} {@value #DEFAULT_LSP_IDLE_TIMEOUT_SECONDS}). Only
 * the credential-store aspect of the machine's onboarding and the store move write the key; every other writer is
 * refused with {@code aspect_owned}.
 * <p>
 * <b>The active store.</b> {@link #activeStore()} gives the store of the recorded choice, read from the file at every
 * call, so a switch by the move takes effect for the next operation. Before a store is recorded it refuses with
 * {@code onboarding_incomplete}, naming the store step. With {@code os} it gives the OS store of the platform, the
 * macOS Keychain or the Linux Secret Service; when that store is unavailable, at start or later, a read fails with
 * {@code credential_store_unavailable} and a write with {@code credential_store_locked}, and nothing is written to the
 * file store in its place. With {@code file} it gives the file store below {@code <PM_MCP_BASE>/credentials}.
 * <p>
 * <b>The integrity key before the choice.</b> {@link #integrityKeyStore()} gives the store the enrolment integrity key
 * lives in: the active store once a store is recorded, and before that the store the runtime finds, the OS store where
 * one is available, else the file store, for which the key is the key file below {@code state/}.
 *
 * @since 0.1
 */
public final class CredentialStoreChoice {

    /** The name of the machine configuration file below the base. */
    public static final String FILE_NAME = "machine-config.json";

    /** The current format version of the machine configuration file. */
    static final int FORMAT_VERSION = 1;

    /** The schema default of {@code max_build_slots}, written when the document is created. */
    static final int DEFAULT_MAX_BUILD_SLOTS = 2;

    /** The schema default of {@code lsp_idle_timeout_seconds}, written when the document is created. */
    static final int DEFAULT_LSP_IDLE_TIMEOUT_SECONDS = 900;

    static final String REVISION = "revision";
    static final String MAX_BUILD_SLOTS = "max_build_slots";
    static final String LSP_IDLE_TIMEOUT_SECONDS = "lsp_idle_timeout_seconds";
    static final String CREDENTIALS = "credentials";
    static final String STORE = "store";

    /** The step of the machine's onboarding that records the store, named by {@code onboarding_incomplete}. */
    static final String STORE_STEP = "store";

    private static final CuiLogger LOGGER = new CuiLogger(CredentialStoreChoice.class);

    /** The store the operator can record. */
    public enum Store {
        /** The OS store of the platform: the macOS Keychain or the Linux Secret Service. */
        OS("os"),
        /** The permission-guarded file store below {@code <PM_MCP_BASE>/credentials}. */
        FILE("file");

        private final String value;

        Store(String value) {
            this.value = value;
        }

        /** @return the value of {@code credentials.store} */
        public String value() {
            return value;
        }

        static Optional<Store> of(String value) {
            return Arrays.stream(values()).filter(store -> store.value.equals(value)).findFirst();
        }
    }

    /** Who writes {@code machine-config.json}. The set is closed; only the owners of the key may write it. */
    public enum Writer {
        /** The credential-store aspect of the machine's onboarding, in its first or a later run. */
        CREDENTIAL_STORE_ASPECT(true),
        /** The move of every entry between the stores, which switches the key in one write. */
        STORE_MOVE(true),
        /** Every other writer of the machine configuration, the plain configuration endpoint among them. */
        OTHER_CONFIGURATION(false);

        private final boolean owner;

        Writer(boolean owner) {
            this.owner = owner;
        }

        /** @return whether this writer may write {@code credentials.store} */
        public boolean owner() {
            return owner;
        }
    }

    /**
     * A request about the credential store that is refused although no store failed: the store is not recorded yet,
     * or the writer does not own the key.
     */
    public static final class Refusal extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        /** Why the request was refused. */
        public enum Reason {
            /** No store is recorded yet: no credential can be entered before the store step of the onboarding. */
            ONBOARDING_INCOMPLETE("onboarding_incomplete"),
            /** The writer does not own {@code credentials.store}. */
            ASPECT_OWNED("aspect_owned");

            private final String code;

            Reason(String code) {
                this.code = code;
            }

            /** @return the error code of the API */
            public String code() {
                return code;
            }
        }

        private final Reason reason;

        /**
         * @param reason  the reason
         * @param message the description
         */
        Refusal(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        /** @return the reason */
        public Reason reason() {
            return reason;
        }
    }

    /** The OS keyrings, separated for tests. */
    interface Keyrings {

        /**
         * @param service the service name
         * @return the Keychain store
         * @throws SecretStoreException if Security.framework cannot be loaded
         */
        SecretStore keychain(String service);

        /**
         * @param socket  the session bus socket
         * @param service the service name
         * @return the Secret Service store, or empty if it is not usable now
         */
        Optional<SecretStore> secretService(Path socket, String service);
    }

    /** The real keyrings of this machine. */
    static final Keyrings SYSTEM = new Keyrings() {

        @Override
        public SecretStore keychain(String service) {
            return new KeychainSecretStore(service);
        }

        @Override
        public Optional<SecretStore> secretService(Path socket, String service) {
            var store = new SecretServiceStore(socket, Posix.getuid(), service, SecretServiceStore.BUS_TIMEOUT);
            return store.available() ? Optional.of(store) : Optional.empty();
        }
    };

    private final AtomicStoreFile file;
    private final String service;
    private final MachinePaths.Os os;
    private final Map<String, String> environment;
    private final Keyrings keyrings;
    private final FileSecretStore fileStore;

    /**
     * @param base        the base directory of the runtime
     * @param service     the service name of the instance ({@link ServiceName})
     * @param os          the operating system
     * @param environment the process environment, which names the session bus
     * @param keyrings    the OS keyrings
     */
    CredentialStoreChoice(Path base, String service, MachinePaths.Os os, Map<String, String> environment,
            Keyrings keyrings) {
        var root = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.file = new AtomicStoreFile(root.resolve(FILE_NAME), LockKey.machineConfiguration(root));
        this.service = Objects.requireNonNull(service, "service");
        this.os = Objects.requireNonNull(os, "os");
        this.environment = Map.copyOf(environment);
        this.keyrings = Objects.requireNonNull(keyrings, "keyrings");
        this.fileStore = new FileSecretStore(root.resolve("credentials"));
    }

    /**
     * @param paths the machine paths
     * @return the choice of the running process for the given machine root
     */
    public static CredentialStoreChoice of(MachinePaths paths) {
        return new CredentialStoreChoice(paths.base(),
                ServiceName.of(paths.base(), Path.of(System.getProperty("user.home"))), paths.os(), System.getenv(),
                SYSTEM);
    }

    /** @return the service name every keyring entry of the instance lives under */
    public String service() {
        return service;
    }

    /**
     * Reads the recorded store without a lock: the file is replaced by one atomic rename, so the read sees the
     * document before or after a write.
     *
     * @return the recorded store; empty if the file or the key is absent
     * @throws de.planmarshall.core.store.StoreRefusal if the document is in another format version, is unreadable, or
     *                                                 holds a value of {@code credentials.store} other than
     *                                                 {@code os} or {@code file}
     * @throws UncheckedIOException                    if the file exists and cannot be read
     */
    public Optional<Store> recorded() {
        return StoreSnapshot.read(file.store()).content().flatMap(document -> parse(document).store());
    }

    /**
     * Records the operator's store: under the lock of the machine configuration, which this call acquires, the
     * document is re-read, {@code credentials.store} set, {@code revision} incremented, and the file replaced in one
     * atomic write; every other member is kept.
     *
     * @param transaction the transaction of the write; it holds no lock that sorts after the machine configuration
     *                    lock
     * @param store       the store the operator chose
     * @param writer      who writes the key
     * @throws Refusal                                 with {@code aspect_owned} if the writer does not own the key;
     *                                                 nothing was acquired or written then
     * @throws de.planmarshall.core.store.StoreRefusal if the document on disk is in another format version or
     *                                                 unreadable; nothing was written then
     * @throws UncheckedIOException                    if the document cannot be read or written
     */
    public void record(LockTransaction transaction, Store store, Writer writer) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(store, "store");
        if (!Objects.requireNonNull(writer, "writer").owner()) {
            throw new Refusal(Refusal.Reason.ASPECT_OWNED,
                    "credentials.store of %s is written only by the credential-store aspect and the store move"
                            .formatted(file.store()));
        }
        transaction.acquire(file.lockKey());
        var current = file.read(transaction);
        var revision = current.map(document -> parse(document).revision()).orElse(0L) + 1;
        file.write(transaction, current.map(document -> rewrite(document, store, revision))
                .orElseGet(() -> create(store)));
        LOGGER.info(INFO.CREDENTIAL_STORE_RECORDED, store.value(), writer, revision);
    }

    /**
     * Gives the store of the recorded choice, as it is recorded now.
     *
     * @return the active store
     * @throws Refusal                                 with {@code onboarding_incomplete} if no store is recorded yet
     * @throws de.planmarshall.core.store.StoreRefusal if the machine configuration cannot be read as its format
     */
    public SecretStore activeStore() {
        var store = recorded().orElseThrow(() -> new Refusal(Refusal.Reason.ONBOARDING_INCOMPLETE,
                "no credential store is recorded yet; record one in the '%s' step of the machine's onboarding"
                        .formatted(STORE_STEP)));
        return store == Store.FILE ? fileStore : new RecordedOsStore();
    }

    /**
     * Gives the store the enrolment integrity key lives in: the active store once a store is recorded; before that
     * the OS store where one is available, else the file store, for which the key is the key file below
     * {@code state/}.
     *
     * @return the store of the integrity key
     * @throws de.planmarshall.core.store.StoreRefusal if the machine configuration cannot be read as its format
     */
    public SecretStore integrityKeyStore() {
        if (recorded().isPresent()) {
            return activeStore();
        }
        return osStore().orElse(fileStore);
    }

    /** @return the name of the OS store of this platform */
    String osStoreName() {
        return os == MachinePaths.Os.MACOS ? SecretStore.KEYCHAIN : SecretStore.SECRET_SERVICE;
    }

    /**
     * Probes the OS store of the platform.
     *
     * @return the OS store, or empty with the reason logged if it is unavailable now
     */
    Optional<SecretStore> osStore() {
        return osStoreOrReason().store();
    }

    /**
     * Probes the OS store of the platform for its check before it is recorded.
     *
     * @return the OS store
     * @throws SecretStoreException with {@code credential_store_unavailable} and the cause if it is unavailable now
     */
    SecretStore requireOsStore() {
        var probe = osStoreOrReason();
        return probe.store().orElseThrow(() -> new SecretStoreException(SecretStoreException.Reason.UNAVAILABLE,
                "the OS credential store '%s' is unavailable: %s".formatted(osStoreName(), probe.reason())));
    }

    private Probe osStoreOrReason() {
        if (os == MachinePaths.Os.MACOS) {
            try {
                return new Probe(Optional.of(keyrings.keychain(service)), "");
            } catch (SecretStoreException e) {
                return new Probe(Optional.empty(), "Keychain: " + e.getMessage());
            }
        }
        var socket = DbusConnection.sessionBusSocket(environment);
        if (socket.isEmpty()) {
            return new Probe(Optional.empty(), "Secret Service: no unix:path session bus address");
        }
        var store = keyrings.secretService(socket.get(), service);
        return new Probe(store, store.isPresent() ? ""
                : "Secret Service: not reachable, or no unlocked default collection on " + socket.get());
    }

    /** The outcome of a probe of the OS store. */
    private record Probe(Optional<SecretStore> store, String reason) {
    }

    /** What this class reads of the machine configuration. */
    private record Document(long revision, Optional<Store> store) {
    }

    private Document parse(byte[] document) {
        return StrictJson.read(file.store(), document, FORMAT_VERSION, parser -> {
            StrictJson.requireObject(parser, FILE_NAME);
            var seen = new HashSet<String>();
            long revision = 0;
            Optional<Store> store = Optional.empty();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = StrictJson.nextField(parser, seen);
                switch (field) {
                    case REVISION -> revision = revision(parser);
                    case CREDENTIALS -> store = credentialsStore(parser);
                    default -> parser.skipChildren();
                }
            }
            return new Document(revision, store);
        });
    }

    private static long revision(JsonParser parser) throws IOException {
        var revision = StrictJson.integer(parser, REVISION);
        if (revision < 0) {
            throw StrictJson.unreadable("'%s' must not be negative".formatted(REVISION));
        }
        return revision;
    }

    private static Optional<Store> credentialsStore(JsonParser parser) throws IOException {
        StrictJson.requireObject(parser, CREDENTIALS);
        var seen = new HashSet<String>();
        Optional<Store> store = Optional.empty();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            if (STORE.equals(StrictJson.nextField(parser, seen))) {
                var value = StrictJson.string(parser, CREDENTIALS + "." + STORE);
                store = Optional.of(Store.of(value).orElseThrow(() -> StrictJson.unreadable(
                        "'%s.%s' must be 'os' or 'file': %s".formatted(CREDENTIALS, STORE, value))));
            } else {
                parser.skipChildren();
            }
        }
        return store;
    }

    /** Copies the document with the new revision and store; every other member is copied as it is. */
    private byte[] rewrite(byte[] document, Store store, long revision) {
        return generate(generator -> {
            try (var parser = StrictJson.factory().createParser(document)) {
                parser.nextToken();
                generator.writeStartObject();
                var written = new HashSet<String>();
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    var field = parser.currentName();
                    parser.nextToken();
                    written.add(field);
                    switch (field) {
                        case REVISION -> generator.writeNumberField(REVISION, revision);
                        case CREDENTIALS -> writeCredentials(generator, parser, store);
                        default -> {
                            generator.writeFieldName(field);
                            generator.copyCurrentStructure(parser);
                        }
                    }
                }
                if (!written.contains(REVISION)) {
                    generator.writeNumberField(REVISION, revision);
                }
                if (!written.contains(CREDENTIALS)) {
                    writeCredentials(generator, null, store);
                }
                generator.writeEndObject();
            }
        });
    }

    private static void writeCredentials(JsonGenerator generator, JsonParser parser, Store store) throws IOException {
        generator.writeObjectFieldStart(CREDENTIALS);
        generator.writeStringField(STORE, store.value());
        if (parser != null) {
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = parser.currentName();
                parser.nextToken();
                if (STORE.equals(field)) {
                    parser.skipChildren();
                } else {
                    generator.writeFieldName(field);
                    generator.copyCurrentStructure(parser);
                }
            }
        }
        generator.writeEndObject();
    }

    private static byte[] create(Store store) {
        return generate(generator -> {
            generator.writeStartObject();
            generator.writeNumberField(StrictJson.FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeNumberField(REVISION, 1);
            generator.writeNumberField(MAX_BUILD_SLOTS, DEFAULT_MAX_BUILD_SLOTS);
            generator.writeNumberField(LSP_IDLE_TIMEOUT_SECONDS, DEFAULT_LSP_IDLE_TIMEOUT_SECONDS);
            writeCredentials(generator, null, store);
            generator.writeEndObject();
        });
    }

    /** Writes one document. */
    @FunctionalInterface
    private interface Content {

        void write(JsonGenerator generator) throws IOException;
    }

    private static byte[] generate(Content content) {
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out)) {
            content.write(generator);
        } catch (IOException e) {
            throw new UncheckedIOException("machine configuration cannot be written", e);
        }
        out.write('\n');
        return out.toByteArray();
    }

    /**
     * The recorded OS store: probed before every operation, so an OS store that becomes unavailable after the start
     * is reported as such. An unavailable store fails a read with {@code credential_store_unavailable} and a write
     * with {@code credential_store_locked}; a locked collection or item met by a read is unavailable as well.
     */
    private final class RecordedOsStore implements SecretStore {

        @Override
        public String name() {
            return osStoreName();
        }

        @Override
        public void put(CredentialAccount account, String secret) {
            forWrite().put(account, secret);
        }

        @Override
        public Optional<String> get(CredentialAccount account) {
            var store = forRead();
            try {
                return store.get(account);
            } catch (SecretStoreException e) {
                throw lockedAsUnavailable(e);
            }
        }

        @Override
        public boolean delete(CredentialAccount account) {
            return forWrite().delete(account);
        }

        @Override
        public List<CredentialAccount> entries() {
            var store = forRead();
            try {
                return store.entries();
            } catch (SecretStoreException e) {
                throw lockedAsUnavailable(e);
            }
        }

        private SecretStore forRead() {
            return available(SecretStoreException.Reason.UNAVAILABLE);
        }

        private SecretStore forWrite() {
            return available(SecretStoreException.Reason.LOCKED);
        }

        private SecretStore available(SecretStoreException.Reason reason) {
            var probe = osStoreOrReason();
            if (probe.store().isPresent()) {
                return probe.store().get();
            }
            LOGGER.warn(WARN.RECORDED_OS_STORE_UNAVAILABLE, osStoreName(), probe.reason());
            throw new SecretStoreException(reason,
                    "the recorded credential store '%s' is unavailable: %s".formatted(osStoreName(), probe.reason()));
        }

        private SecretStoreException lockedAsUnavailable(SecretStoreException e) {
            if (e.reason() != SecretStoreException.Reason.LOCKED) {
                return e;
            }
            LOGGER.warn(WARN.RECORDED_OS_STORE_UNAVAILABLE, osStoreName(), e.getMessage());
            return new SecretStoreException(SecretStoreException.Reason.UNAVAILABLE,
                    "the recorded credential store '%s' is unavailable: %s".formatted(osStoreName(), e.getMessage()),
                    e);
        }
    }
}
