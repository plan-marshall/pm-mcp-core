/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Reads the shipped profile set from the resources of {@code pm-api} (PM-CLIENT-5, Profile Sets): the documents
 * {@code classpath:/profiles/<client_id>.json}, in a directory of the build or in the JAR of the dependency.
 * <p>
 * The reader is a build test's entry: the release compiles the documents into the binary, and the build validates every
 * one of them. The directory holds the profiles and nothing else, and a file whose name is not a client id is not
 * returned as one.
 *
 * @since 0.1
 */
public final class ShippedProfiles {

    private static final String DIRECTORY = "profiles";
    private static final Pattern CLIENT_ID = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");

    private ShippedProfiles() {
    }

    /**
     * @param loader the loader of the resources
     * @return the shipped profiles by client id
     * @throws UncheckedIOException if a resource cannot be listed or read
     */
    public static ProfileSets read(ClassLoader loader) {
        var documents = new TreeMap<String, byte[]>();
        try {
            var resources = loader.getResources(DIRECTORY);
            while (resources.hasMoreElements()) {
                readResource(resources.nextElement(), documents);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The shipped profiles cannot be listed", e);
        }
        var shipped = new LinkedHashMap<String, ProfileSets.ShippedProfile>();
        for (var entry : documents.entrySet()) {
            shipped.put(entry.getKey(), new ProfileSets.ShippedProfile(entry.getValue()));
        }
        return ProfileSets.of(shipped);
    }

    /**
     * @return the shipped profiles of this classpath
     */
    public static ProfileSets read() {
        return read(ShippedProfiles.class.getClassLoader());
    }

    private static void readResource(URL directory, Map<String, byte[]> documents) throws IOException {
        if ("file".equals(directory.getProtocol())) {
            try {
                readDirectory(Path.of(directory.toURI()), documents);
            } catch (java.net.URISyntaxException e) {
                throw new IOException("Not a file URL: " + directory, e);
            }
        } else if ("jar".equals(directory.getProtocol())) {
            readJar((JarURLConnection) directory.openConnection(), documents);
        }
    }

    private static void readDirectory(Path directory, Map<String, byte[]> documents) throws IOException {
        try (var entries = Files.list(directory)) {
            for (var entry : entries.sorted().toList()) {
                var name = entry.getFileName().toString();
                if (Files.isRegularFile(entry) && name.endsWith(".json")) {
                    add(clientId(name), Files.readAllBytes(entry), documents);
                }
            }
        }
    }

    private static void readJar(JarURLConnection connection, Map<String, byte[]> documents) throws IOException {
        var prefix = connection.getEntryName() == null ? DIRECTORY : connection.getEntryName();
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        var jar = connection.getJarFile();
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            var name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(prefix) || !name.endsWith(".json")) {
                continue;
            }
            var relative = name.substring(prefix.length());
            if (!relative.contains("/")) {
                add(clientId(relative), read(jar, name), documents);
            }
        }
    }

    private static byte[] read(JarFile jar, String name) throws IOException {
        try (InputStream stream = jar.getInputStream(jar.getEntry(name))) {
            return stream.readAllBytes();
        }
    }

    private static void add(String clientId, byte[] document, Map<String, byte[]> documents) {
        if (clientId != null) {
            documents.put(clientId, document);
        }
    }

    private static String clientId(String fileName) {
        var name = fileName.substring(0, fileName.length() - ".json".length());
        return CLIENT_ID.matcher(name).matches() ? name : null;
    }

}
