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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The permission-guarded file store (PM-CRED-3), used when the operator recorded it, never as a
 * fallback: {@code <PM_MCP_BASE>/credentials/<key>.json} and
 * {@code <PM_MCP_BASE>/credentials/<project>/<key>.json}, directories {@code 0700}, files
 * {@code 0600}, written atomically. A file that is not owned by the runtime user or is accessible
 * to anyone else is refused ({@code credentials_file_insecure}); a file in another format, or an
 * entry of the directory whose name is no account, is refused ({@code credentials_file_invalid}).
 * <p>
 * The file format of this slice is {@code {"format_version":1,"value":"<secret>"}}: the secret of
 * the local API's credential resource; the full credential schema (url, auth type, expiry) of
 * PM-CRED-3 extends it with the operator verbs.
 */
public final class FileSecretStore implements SecretStore {

    /** The current file format version. */
    static final int FORMAT_VERSION = 1;

    private static final String SUFFIX = ".json";

    /**
     * The name prefix of the temporary file of an atomic write. It begins with {@code ~}, which is outside the alphabet
     * of {@link CredentialAccount}, so no credential file and no project directory ever carries it.
     */
    static final String TEMP_PREFIX = "~tmp-";

    private static final Set<PosixFilePermission> DIRECTORY_MODE = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_MODE = PosixFilePermissions.fromString("rw-------");
    private static final JsonFactory JSON = new JsonFactory();

    private final Path directory;

    /**
     * @param directory {@code <PM_MCP_BASE>/credentials}
     */
    public FileSecretStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return FILE;
    }

    @Override
    public synchronized void put(CredentialAccount account, String secret) {
        var target = path(account);
        try {
            createPrivateDirectory(directory);
            createPrivateDirectory(target.getParent());
            var temp = Files.createTempFile(target.getParent(), TEMP_PREFIX, SUFFIX,
                    PosixFilePermissions.asFileAttribute(FILE_MODE));
            try {
                Files.writeString(temp, json(secret), StandardCharsets.UTF_8);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            requirePrivate(target, FILE_MODE);
        } catch (IOException e) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                    "cannot write credential file " + target, e);
        }
    }

    @Override
    public synchronized Optional<String> get(CredentialAccount account) {
        var file = path(account);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            requirePrivate(directory, DIRECTORY_MODE);
            requirePrivate(file.getParent(), DIRECTORY_MODE);
            requirePrivate(file, FILE_MODE);
            return Optional.of(parse(file, Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED, "cannot read credential file " + file, e);
        }
    }

    @Override
    public synchronized boolean delete(CredentialAccount account) {
        var file = path(account);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                    "cannot delete credential file " + file, e);
        }
    }

    /**
     * Lists {@code <dir>/<key>.json} and {@code <dir>/<project>/<key>.json} without following a
     * link; the temporary files of an atomic write ({@code ~tmp-*}, a name no account can have, also
     * one a crash left behind) are skipped, so every legal account is listed, {@code .tmp-ci} among
     * them. An absent directory is an empty store.
     *
     * @throws SecretStoreException {@code credentials_file_insecure} for a directory that fails the
     *                              private-mode check or a symbolic link, {@code credentials_file_invalid}
     *                              for an entry whose name is no account
     */
    @Override
    public synchronized List<CredentialAccount> entries() {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        var accounts = new ArrayList<CredentialAccount>();
        try {
            requirePrivate(directory, DIRECTORY_MODE);
            for (var entry : list(directory)) {
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    requirePrivate(entry, DIRECTORY_MODE);
                    var project = entry.getFileName().toString();
                    for (var file : list(entry)) {
                        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                            throw invalid(file, "not a credential file");
                        }
                        accounts.add(account(file, project));
                    }
                } else {
                    accounts.add(account(entry, null));
                }
            }
        } catch (IOException e) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                    "cannot list credential directory " + directory, e);
        }
        return SecretStore.ordered(accounts);
    }

    /** The entries of one directory, temporary files skipped. */
    private static List<Path> list(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.filter(path -> !path.getFileName().toString().startsWith(TEMP_PREFIX)).toList();
        }
    }

    /** The account of one credential file, {@code project} null for a global entry. */
    private static CredentialAccount account(Path file, @Nullable String project) {
        if (Files.isSymbolicLink(file)) {
            throw new SecretStoreException(SecretStoreException.Reason.INSECURE,
                    file + " must not be a symbolic link");
        }
        var name = file.getFileName().toString();
        if (!name.endsWith(SUFFIX) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw invalid(file, "not a credential file");
        }
        var key = name.substring(0, name.length() - SUFFIX.length());
        try {
            return project == null ? CredentialAccount.global(key) : CredentialAccount.project(project, key);
        } catch (InvalidCredentialNameException _) {
            throw invalid(file, "not a credential entry name");
        }
    }

    /**
     * @param account the account
     * @return its file, canonicalized below the store directory
     */
    Path path(CredentialAccount account) {
        var file = directory.resolve(account.account() + SUFFIX).normalize();
        if (!file.startsWith(directory)) {
            throw new InvalidCredentialNameException(account.account());
        }
        return file;
    }

    private static void createPrivateDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(DIRECTORY_MODE));
            Files.setPosixFilePermissions(dir, DIRECTORY_MODE);
        }
        requirePrivate(dir, DIRECTORY_MODE);
    }

    private static void requirePrivate(Path path, Set<PosixFilePermission> mode) throws IOException {
        var owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS);
        var user = path.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        var permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
        if (Files.isSymbolicLink(path) || !owner.equals(user) || !permissions.equals(mode)) {
            throw new SecretStoreException(SecretStoreException.Reason.INSECURE,
                    path + " must be owned by " + user.getName() + " with mode "
                            + PosixFilePermissions.toString(mode) + ", is " + owner.getName() + " "
                            + PosixFilePermissions.toString(permissions));
        }
    }

    private static String json(String secret) throws IOException {
        var writer = new StringWriter();
        try (var generator = JSON.createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeNumberField("format_version", FORMAT_VERSION);
            generator.writeStringField("value", secret);
            generator.writeEndObject();
        }
        return writer.toString();
    }

    private static String parse(Path file, String content) throws IOException {
        @Nullable Integer version = null;
        @Nullable String value = null;
        try (JsonParser parser = JSON.createParser(content)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw invalid(file, "not a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = parser.currentName();
                var token = parser.nextToken();
                switch (field) {
                    case "format_version" -> version = token == JsonToken.VALUE_NUMBER_INT ? parser.getIntValue() : -1;
                    case "value" -> value = token == JsonToken.VALUE_STRING ? parser.getText() : null;
                    default -> throw invalid(file, "undeclared field '" + field + "'");
                }
            }
        } catch (JsonProcessingException _) {
            throw invalid(file, "malformed JSON");
        }
        if (version == null || version != FORMAT_VERSION) {
            throw invalid(file, "field 'format_version' must be " + FORMAT_VERSION);
        }
        if (value == null) {
            throw invalid(file, "field 'value' must be a string");
        }
        return value;
    }

    private static SecretStoreException invalid(Path file, String reason) {
        return new SecretStoreException(SecretStoreException.Reason.INVALID, file + ": " + reason);
    }
}
