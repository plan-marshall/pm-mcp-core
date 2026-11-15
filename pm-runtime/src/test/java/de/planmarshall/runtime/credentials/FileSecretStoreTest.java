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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The file-backed credential store ({@code <PM_MCP_BASE>/credentials/}).
 * <p>
 * The secret values and the project ids are generated; the PM-MCP file format, the {@code 0600}/{@code 0700} modes, the
 * error codes and the fixture account names are the contracts.
 */
@EnableGeneratorController
@DisplayName("FileSecretStore: <PM_MCP_BASE>/credentials/")
class FileSecretStoreTest {

    @TempDir
    Path base;

    private Path directory;
    private FileSecretStore store;
    private String projectId;
    private String otherProject;
    private String secretOne;
    private String secretTwo;
    private String globalValue;
    private String projectValue;
    private String storedValue;

    @BeforeEach
    void create() {
        directory = base.resolve("credentials");
        store = new FileSecretStore(directory);
        projectId = RuntimeGenerators.projectIds().next();
        otherProject = projectId + "-other";
        secretOne = Generators.letterStrings(1, 20).next();
        secretTwo = Generators.letterStrings(1, 20).next();
        globalValue = Generators.letterStrings(1, 20).next();
        projectValue = Generators.letterStrings(1, 20).next();
        storedValue = Generators.letterStrings(1, 20).next();
    }

    private static String mode(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("writes 0600 files in 0700 directories in the PM-MCP format")
        void putGetDelete() throws Exception {
            store.put(CredentialAccount.global("github"), "s\"cr€ t");

            var file = directory.resolve("github.json");
            assertEquals("rwx------", mode(directory), "store directory is private to the owner");
            assertEquals("rw-------", mode(file), "credential file is private to the owner");
            assertEquals("{\"format_version\":1,\"value\":\"s\\\"cr€ t\"}", Files.readString(file),
                    "file body is the versioned PM-MCP format");
            assertEquals(Optional.of("s\"cr€ t"), store.get(CredentialAccount.global("github")),
                    "get returns the secret that was written");
            assertTrue(store.delete(CredentialAccount.global("github")), "delete removes the existing global entry");
            assertFalse(store.delete(CredentialAccount.global("github")),
                    "delete of an absent entry returns false");
            assertEquals(Optional.empty(), store.get(CredentialAccount.global("github")),
                    "get of a deleted entry is empty");
            assertEquals(SecretStore.FILE, store.name(), "store name is the file backend");
        }

        @Test
        @DisplayName("keeps project entries in <project>/ and resolves them first")
        void project() throws Exception {
            store.put(CredentialAccount.global("sonar"), globalValue);
            store.put(CredentialAccount.project(projectId, "sonar"), projectValue);

            assertEquals("rwx------", mode(directory.resolve(projectId)),
                    "project directory is private to the owner");
            assertEquals(Optional.of(projectValue), store.resolve(projectId, "sonar"),
                    "project entry wins over the global one");
            assertEquals(Optional.of(globalValue), store.resolve(otherProject, "sonar"),
                    "another project falls back to the global entry");
        }

        @Test
        @DisplayName("replaces an existing file atomically without leftovers")
        void replace() throws Exception {
            store.put(CredentialAccount.global("github"), secretOne);
            store.put(CredentialAccount.global("github"), secretTwo);

            assertEquals(Optional.of(secretTwo), store.get(CredentialAccount.global("github")),
                    "second put replaces the stored secret");
            try (var files = Files.list(directory)) {
                assertEquals(1, files.count(), "replace leaves exactly one file behind");
            }
        }
    }

    @Nested
    @DisplayName("entries")
    class Entries {

        @Test
        @DisplayName("an absent directory is an empty store")
        void absent() {
            assertEquals(List.of(), store.entries(), "absent directory is an empty store");
        }

        @Test
        @DisplayName("lists global and project entries in account order, skipping temporary files")
        void globalAndProject() throws Exception {
            store.put(CredentialAccount.project(projectId, "sonar"), projectValue);
            store.put(CredentialAccount.global("sonar"), globalValue);
            store.put(CredentialAccount.global("github"), globalValue);
            Files.createFile(directory.resolve(FileSecretStore.TEMP_PREFIX + "123.json"),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.createFile(directory.resolve(projectId).resolve(FileSecretStore.TEMP_PREFIX + "456.json"),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));

            var entries = store.entries();

            assertEquals(List.of(CredentialAccount.global("github"), CredentialAccount.project(projectId, "sonar"),
                            CredentialAccount.global("sonar")), entries,
                    "entries list global and project accounts in order");
        }

        @Test
        @DisplayName("lists and reads an entry and a project named .tmp-ci, and skips a temporary file a crash left")
        void legalNamesLikeTemporaryFiles() throws Exception {
            var key = CredentialAccount.global(".tmp-ci");
            var project = CredentialAccount.project(".tmp-ci", "sonar");
            store.put(key, globalValue);
            store.put(project, projectValue);
            Files.writeString(directory.resolve(FileSecretStore.TEMP_PREFIX + "789.json"), "{\"format_version\":1,",
                    StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(directory.resolve(FileSecretStore.TEMP_PREFIX + "789.json"),
                    PosixFilePermissions.fromString("rw-------"));

            var entries = store.entries();

            assertAll(
                    () -> assertEquals(List.of(key, project), entries,
                            "entries list the two legal names in order"),
                    () -> assertEquals(List.of(Optional.of(globalValue), Optional.of(projectValue)),
                            entries.stream().map(store::get).toList(), "each entry reads back its own value"));
        }

        @Test
        @DisplayName("a temporary file of an atomic write carries a name no account can have")
        void temporaryNameNoAccount() {
            assertThrows(InvalidCredentialNameException.class,
                    () -> CredentialAccount.global(FileSecretStore.TEMP_PREFIX + "123"),
                    "temporary file names are no valid accounts");
        }

        @Test
        @DisplayName("an empty directory is an empty store")
        void empty() {
            store.put(CredentialAccount.global("github"), storedValue);
            store.delete(CredentialAccount.global("github"));

            assertEquals(List.of(), store.entries(), "an emptied directory is an empty store");
        }

        @ParameterizedTest(name = "entry {0}")
        @ValueSource(strings = {"bad name.json", "README", "..json"})
        @DisplayName("refuses an entry whose name is no account as invalid")
        void invalidName(String name) throws Exception {
            store.put(CredentialAccount.global("github"), storedValue);
            Files.writeString(directory.resolve(name), "{}");

            var e = assertThrows(SecretStoreException.class, store::entries,
                    "an invalid entry name fails the listing");
            assertEquals(SecretStoreException.Reason.INVALID, e.reason(),
                    "invalid entry name reports the invalid reason");
            assertEquals("credentials_file_invalid", e.reason().code(),
                    "invalid entry name reports its error code");
        }

        @Test
        @DisplayName("refuses a directory inside a project directory as invalid")
        void nestedDirectory() throws Exception {
            store.put(CredentialAccount.project(projectId, "sonar"), storedValue);
            Files.createDirectory(directory.resolve(projectId).resolve("deeper"),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));

            var e = assertThrows(SecretStoreException.class, store::entries,
                    "a nested directory fails the listing");
            assertEquals(SecretStoreException.Reason.INVALID, e.reason(),
                    "nested directory reports the invalid reason");
        }

        @Test
        @DisplayName("refuses a project directory that fails the private-mode check as insecure")
        void insecureProjectDirectory() throws Exception {
            store.put(CredentialAccount.project(projectId, "sonar"), storedValue);
            Files.setPosixFilePermissions(directory.resolve(projectId), PosixFilePermissions.fromString("rwxr-xr-x"));

            var e = assertThrows(SecretStoreException.class, store::entries,
                    "an insecure project directory fails the listing");
            assertEquals(SecretStoreException.Reason.INSECURE, e.reason(),
                    "insecure project directory reports insecure reason");
            assertEquals("credentials_file_insecure", e.reason().code(),
                    "insecure project directory reports its error code");
        }

        @Test
        @DisplayName("refuses a store directory that fails the private-mode check as insecure")
        void insecureDirectory() throws Exception {
            store.put(CredentialAccount.global("github"), storedValue);
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-x---"));

            var e = assertThrows(SecretStoreException.class, store::entries,
                    "an insecure store directory fails the listing");
            assertEquals(SecretStoreException.Reason.INSECURE, e.reason(),
                    "insecure store directory reports insecure reason");
        }

        @Test
        @DisplayName("refuses a symbolic link without following it as insecure")
        void symlink() throws Exception {
            store.put(CredentialAccount.global("other"), storedValue);
            Files.createSymbolicLink(directory.resolve("github.json"), directory.resolve("other.json"));

            var e = assertThrows(SecretStoreException.class, store::entries,
                    "a symbolic link fails the listing");
            assertEquals(SecretStoreException.Reason.INSECURE, e.reason(),
                    "symbolic link reports the insecure reason");
        }
    }

    @Nested
    @DisplayName("refuses")
    class Refuses {

        @ParameterizedTest(name = "file mode {0}")
        @ValueSource(strings = {"rw-r-----", "rw----r--", "r--------"})
        @DisplayName("a file with another mode as insecure")
        void insecureFile(String mode) throws Exception {
            store.put(CredentialAccount.global("github"), storedValue);
            Files.setPosixFilePermissions(directory.resolve("github.json"), PosixFilePermissions.fromString(mode));

            var account = CredentialAccount.global("github");
            var e = assertThrows(SecretStoreException.class, () -> store.get(account),
                    "an insecure file mode fails the read");
            assertEquals(SecretStoreException.Reason.INSECURE, e.reason(),
                    "insecure file mode reports the insecure reason");
            assertEquals("credentials_file_insecure", e.reason().code(),
                    "insecure file mode reports its error code");
        }

        @Test
        @DisplayName("a directory readable by others as insecure")
        void insecureDirectory() throws Exception {
            store.put(CredentialAccount.global("github"), storedValue);
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"));

            var account = CredentialAccount.global("github");
            var e = assertThrows(SecretStoreException.class, () -> store.get(account),
                    "a world-readable directory fails the read");
            assertEquals(SecretStoreException.Reason.INSECURE, e.reason(),
                    "world-readable directory reports insecure reason");
            assertThrows(SecretStoreException.class, () -> store.put(account, storedValue),
                    "put into an insecure directory fails");
        }

        @Test
        @DisplayName("a symbolic link in place of the file")
        void symlink() throws Exception {
            store.put(CredentialAccount.global("other"), storedValue);
            Files.createSymbolicLink(directory.resolve("github.json"), directory.resolve("other.json"));

            var account = CredentialAccount.global("github");
            var e = assertThrows(SecretStoreException.class, () -> store.get(account),
                    "a symlink in place of the file fails the read");
            assertEquals(SecretStoreException.Reason.INSECURE, e.reason(),
                    "symlink entry reports the insecure reason");
        }

        @ParameterizedTest(name = "content {0}")
        @ValueSource(strings = {
                "{\"format_version\":2,\"value\":\"x\"}",
                "{\"value\":\"x\"}",
                "{\"format_version\":\"1\",\"value\":\"x\"}",
                "{\"format_version\":1}",
                "{\"format_version\":1,\"value\":7}",
                "{\"format_version\":1,\"value\":\"x\",\"url\":\"https://x\"}",
                "[1]",
                "{\"format_version\":1,"})
        @DisplayName("a file in another format as invalid")
        void invalid(String content) throws Exception {
            store.put(CredentialAccount.global("github"), storedValue);
            Files.writeString(directory.resolve("github.json"), content);

            var account = CredentialAccount.global("github");
            var e = assertThrows(SecretStoreException.class, () -> store.get(account),
                    "an invalid file format fails the read");
            assertEquals(SecretStoreException.Reason.INVALID, e.reason(),
                    "invalid file format reports the invalid reason");
            assertTrue(e.getMessage().contains("github.json"), e.getMessage());
        }

        @Test
        @DisplayName("an unwritable directory as failed")
        void unwritable() throws Exception {
            Files.writeString(base.resolve("credentials"), "a file, not a directory");

            var account = CredentialAccount.global("github");
            var e = assertThrows(SecretStoreException.class, () -> store.put(account, storedValue),
                    "an unwritable base fails the put");
            assertEquals(SecretStoreException.Reason.FAILED, e.reason(),
                    "an unwritable base reports the failed reason");
        }
    }
}
