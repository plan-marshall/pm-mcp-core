/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;
import lombok.experimental.UtilityClass;

/**
 * Reads a numeric limit from a properties resource of the calling module (PM-ARCH-8, PM-IMPL-5).
 * <p>
 * A limit is read when the constant that names it is initialized. A resource that is absent, a key that is absent and
 * a value that is not a non-negative integer are faults of the build and are never defaulted: the load fails with an
 * {@link IllegalStateException} naming the resource and, where it applies, the key, or with an
 * {@link UncheckedIOException} when the resource cannot be read. A limit that sizes an {@code int} quantity is read
 * through {@link #readInt(String, String)}, which refuses a value above {@link Integer#MAX_VALUE} the same way.
 *
 * @since 0.1
 */
@UtilityClass
public class LimitProperties {

    /**
     * Reads a non-negative limit from a classpath resource.
     *
     * @param resource the absolute classpath resource that holds the limit
     * @param key      the key of the limit in the resource
     * @return the value
     * @throws IllegalStateException if the resource is absent, the key is absent or the value is not a non-negative
     *                               integer
     * @throws UncheckedIOException  if the resource cannot be read
     */
    public static long read(String resource, String key) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(key, "key");
        var properties = new Properties();
        try (InputStream in = LimitProperties.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("the limit resource '" + resource + "' is absent");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("the limit resource '" + resource + "' cannot be read", e);
        }
        return value(properties, key, resource);
    }

    /**
     * Reads a non-negative limit that must fit an {@code int} from a classpath resource.
     * <p>
     * A limit that sizes an {@code int} quantity (a length, an index) is read through here, so a value above
     * {@link Integer#MAX_VALUE} is refused as the same fault of the build as a negative one, naming the resource and the
     * key, instead of failing the class's initialization with the bare arithmetic exception of a narrowing.
     *
     * @param resource the absolute classpath resource that holds the limit
     * @param key      the key of the limit in the resource
     * @return the value
     * @throws IllegalStateException if the resource is absent, the key is absent or the value is not a non-negative
     *                               integer of at most {@link Integer#MAX_VALUE}
     * @throws UncheckedIOException  if the resource cannot be read
     */
    public static int readInt(String resource, String key) {
        return intRanged(read(resource, key), key, resource);
    }

    /**
     * Narrows a limit already read to an {@code int}, refusing a value above {@link Integer#MAX_VALUE}.
     *
     * @param value    the non-negative limit
     * @param key      the key of the limit, named in a diagnostic
     * @param resource the resource the limit was read from, named in a diagnostic
     * @return the value as an {@code int}
     * @throws IllegalStateException if the value is above {@link Integer#MAX_VALUE}
     */
    static int intRanged(long value, String key, String resource) {
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("the limit '" + key + "' of '" + resource + "' is above "
                    + Integer.MAX_VALUE + ": " + value);
        }
        return (int) value;
    }

    /**
     * Reads a non-negative limit from properties that are already loaded.
     *
     * @param properties the properties that hold the limit
     * @param key        the key of the limit
     * @param resource   the resource the properties were read from, named in a diagnostic
     * @return the value
     * @throws IllegalStateException if the key is absent or the value is not a non-negative integer
     */
    public static long value(Properties properties, String key, String resource) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resource, "resource");
        var raw = properties.getProperty(key);
        if (raw == null) {
            throw new IllegalStateException("the limit key '" + key + "' is absent from '" + resource + "'");
        }
        long value;
        try {
            value = Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("the limit '" + key + "' of '" + resource + "' is not a number: '"
                    + raw + "'", e);
        }
        if (value < 0) {
            throw new IllegalStateException("the limit '" + key + "' of '" + resource + "' is negative: " + value);
        }
        return value;
    }
}
