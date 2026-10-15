/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Locale;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

/**
 * Runs each test with a default format locale whose digits are not ASCII, {@code fa-IR}, and restores the previous
 * one after it (test helper): a number the code writes into a file, a wire format or a detail through a
 * locale-sensitive conversion then shows up as Persian digits, so a test that expects ASCII digits fails.
 * <p>
 * Before each test it checks that the locale does format digits of its own, so a test under it can fail.
 */
public final class NonLatinDigits implements BeforeEachCallback, AfterEachCallback {

    /** The default format locale of each test. */
    public static final Locale PERSIAN = Locale.forLanguageTag("fa-IR");

    private static final Namespace NAMESPACE = Namespace.create(NonLatinDigits.class);
    private static final String PREVIOUS = "previous";

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getStore(NAMESPACE).put(PREVIOUS, Locale.getDefault(Locale.Category.FORMAT));
        Locale.setDefault(Locale.Category.FORMAT, PERSIAN);
        assertNotEquals("1", "%d".formatted(1), "the locale formats digits of its own, so the test can fail");
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Locale.setDefault(Locale.Category.FORMAT, context.getStore(NAMESPACE).remove(PREVIOUS, Locale.class));
    }
}
