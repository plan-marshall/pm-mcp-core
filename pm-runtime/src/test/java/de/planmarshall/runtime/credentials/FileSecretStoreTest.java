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

@DisplayName("FileSecretStore: <PM_MCP_BASE>/credentials/")
class FileSecretStoreTest {

    @TempDir
    Path base;

    private Path directory;
    private FileSecretStore store;

    @BeforeEach
    void create() {
        directory = base.resolve("credentials");
        store = new FileSecretStore(directory);
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
            assertEquals("rwx------", mode(directory));
            assertEquals("rw-------", mode(file));
            assertEquals("{\"format_version\":1,\"value\":\"s\\\"cr€ t\"}", Files.readString(file));
            assertEquals(Optional.of("s\"cr€ t"), store.get(CredentialAccount.global("github")));
            assertTrue(store.delete(CredentialAccount.global("github")));
            assertFalse(store.delete(CredentialAccount.global("github")));
            assertEquals(Optional.empty(), store.get(CredentialAccount.global("github")));
            assertEquals(SecretStore.FILE, store.name());
        }

        @Test
        @DisplayName("keeps project entries in <project>/ and resolves them first")
        void project() throws Exception {
            store.put(CredentialAccount.global("sonar"), "global");
            store.put(CredentialAccount.project("p1", "sonar"), "project");

            assertEquals("rwx------", mode(directory.resolve("p1")));
            assertEquals(Optional.of("project"), store.resolve("p1", "sonar"));
            assertEquals(Optional.of("global"), store.resolve("p2", "sonar"));
        }

        @Test
        @DisplayName("replaces an existing file atomically without leftovers")
        void replace() throws Exception {
            store.put(CredentialAccount.global("github"), "one");
            store.put(CredentialAccount.global("github"), "two");

            assertEquals(Optional.of("two"), store.get(CredentialAccount.global("github")));
            try (var files = Files.list(directory)) {
                assertEquals(1, files.count());
            }
        }
    }

    @Nested
    @DisplayName("entries")
    class Entries {

        @Test
        @DisplayName("an absent directory is an empty store")
        void absent() {
            assertEquals(List.of(), store.entries());
        }

        @Test
        @DisplayName("lists global and project entries in account order, skipping temporary files")
        void globalAndProject() throws Exception {
            store.put(CredentialAccount.project("p1", "sonar"), "project");
            store.put(CredentialAccount.global("sonar"), "global");
            store.put(CredentialAccount.global("github"), "global");
            Files.createFile(directory.resolve(FileSecretStore.TEMP_PREFIX + "123.json"),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.createFile(directory.resolve("p1").resolve(FileSecretStore.TEMP_PREFIX + "456.json"),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));

            var entries = store.entries();

            assertEquals(List.of(CredentialAccount.global("github"), CredentialAccount.project("p1", "sonar"),
                    CredentialAccount.global("sonar")), entries);
        }

        @Test
        @DisplayName("lists and reads an entry and a project named .tmp-ci, and skips a temporary file a crash left")
        void legalNamesLikeTemporaryFiles() throws Exception {
            var key = CredentialAccount.global(".tmp-ci");
            var project = CredentialAccount.project(".tmp-ci", "sonar");
            store.put(key, "global");
            store.put(project, "project");
            Files.writeString(directory.resolve(FileSecretStore.TEMP_PREFIX + "789.json"), "{\"format_version\":1,",
                    StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(directory.resolve(FileSecretStore.TEMP_PREFIX + "789.json"),
                    PosixFilePermissions.fromString("rw-------"));

            var entries = store.entries();

            assertAll(
                    () -> assertEquals(List.of(key, project), entries),
                    () -> assertEquals(List.of(Optional.of("global"), Optional.of("project")),
                            entries.stream().map(store::get).toList()));
        }

        @Test
        @DisplayName("a temporary file of an atomic write carries a name no account can have")
        void temporaryNameNoAccount() {
            assertThrows(InvalidCredentialNameException.class,
                    () -> CredentialAccount.global(FileSecretStore.TEMP_PREFIX + "123"));
        }

        @Test
        @DisplayName("an empty directory is an empty store")
        void empty() throws Exception {
            store.put(CredentialAccount.global("github"), "x");
            store.delete(CredentialAccount.global("github"));

            assertEquals(List.of(), store.entries());
        }

        @ParameterizedTest(name = "entry {0}")
        @ValueSource(strings = {"bad name.json", "README", "..json"})
        @DisplayName("refuses an entry whose name is no account as invalid")
        void invalidName(String name) throws Exception {
            store.put(CredentialAccount.global("github"), "x");
            Files.writeString(directory.resolve(name), "{}");

            var e = assertThrows(SecretStoreException.class, store::entries);
            assertEquals(SecretStoreException.Reason.INVALID, e.reason());
            assertEquals("credentials_file_invalid", e.reason().code());
        }

        @Test
        @DisplayName("refuses a directory inside a project directory as invalid")
        void nestedDirectory() throws Exception {
            store.put(CredentialAccount.project("p1", "sonar"), "x");
            Files.createDirectory(directory.resolve("p1").resolve("deeper"),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));

            var e = assertThrows(SecretStoreException.class, store::entries);
            assertEquals(SecretStoreException.Reason.INVALID, e.reason());
        }

        @Test
        @DisplayName("refuses a project directory that fails the private-mode check as insecure")
        void insecureProjectDirectory() throws Exception {
            store.put(CredentialAccount.project("p1", "sonar"), "x");
            Files.setPosixFilePermissions(directory.resolve("p1"), PosixFilePermissions.fromString("rwxr-xr-x"));

            var e = assertThrows(SecretStoreException.class, store::entries);
            assertEquals(SecretStoreException.Reason.INSECURE, e.reason());
            assertEquals("credentials_file_insecure", e.reason().code());
        }

        @Test
        @DisplayName("refuses a store directory that fails the private-mode check as insecure")
        void insecureDirectory() throws Exception {
            store.put(CredentialAccount.global("github"), "x");
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-x---"));

            var e = assertThrows(SecretStoreException.class, store::entries);
            assertEquals(SecretStoreException.Reason.INSECURE, e.reason());
        }

        @Test
        @DisplayName("refuses a symbolic link without following it as insecure")
        void symlink() throws Exception {
            store.put(CredentialAccount.global("other"), "x");
            Files.createSymbolicLink(directory.resolve("github.json"), directory.resolve("other.json"));

            var e = assertThrows(SecretStoreException.class, store::entries);
            assertEquals(SecretStoreException.Reason.INSECURE, e.reason());
        }
    }

    @Nested
    @DisplayName("refuses")
    class Refuses {

        @ParameterizedTest(name = "file mode {0}")
        @ValueSource(strings = {"rw-r-----", "rw----r--", "r--------"})
        @DisplayName("a file with another mode as insecure")
        void insecureFile(String mode) throws Exception {
            store.put(CredentialAccount.global("github"), "x");
            Files.setPosixFilePermissions(directory.resolve("github.json"), PosixFilePermissions.fromString(mode));

            var e = assertThrows(SecretStoreException.class, () -> store.get(CredentialAccount.global("github")));
            assertEquals(SecretStoreException.Reason.INSECURE, e.reason());
            assertEquals("credentials_file_insecure", e.reason().code());
        }

        @Test
        @DisplayName("a directory readable by others as insecure")
        void insecureDirectory() throws Exception {
            store.put(CredentialAccount.global("github"), "x");
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"));

            var e = assertThrows(SecretStoreException.class, () -> store.get(CredentialAccount.global("github")));
            assertEquals(SecretStoreException.Reason.INSECURE, e.reason());
            assertThrows(SecretStoreException.class, () -> store.put(CredentialAccount.global("github"), "y"));
        }

        @Test
        @DisplayName("a symbolic link in place of the file")
        void symlink() throws Exception {
            store.put(CredentialAccount.global("other"), "x");
            Files.createSymbolicLink(directory.resolve("github.json"), directory.resolve("other.json"));

            var e = assertThrows(SecretStoreException.class, () -> store.get(CredentialAccount.global("github")));
            assertEquals(SecretStoreException.Reason.INSECURE, e.reason());
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
            store.put(CredentialAccount.global("github"), "x");
            Files.writeString(directory.resolve("github.json"), content);

            var e = assertThrows(SecretStoreException.class, () -> store.get(CredentialAccount.global("github")));
            assertEquals(SecretStoreException.Reason.INVALID, e.reason());
            assertTrue(e.getMessage().contains("github.json"), e.getMessage());
        }

        @Test
        @DisplayName("an unwritable directory as failed")
        void unwritable() throws Exception {
            Files.writeString(base.resolve("credentials"), "a file, not a directory");

            var e = assertThrows(SecretStoreException.class, () -> store.put(CredentialAccount.global("github"), "x"));
            assertEquals(SecretStoreException.Reason.FAILED, e.reason());
        }
    }
}
