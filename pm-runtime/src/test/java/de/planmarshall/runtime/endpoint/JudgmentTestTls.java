/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * The TLS context of the remote-form tests: the mock web server answers with a self-signed certificate it generates per
 * run, so the test client trusts that peer. Only the peer's trust is relaxed; the request itself, its headers and the
 * redirect policy are the ones the runtime uses.
 */
final class JudgmentTestTls {

    private JudgmentTestTls() {
    }

    /** @return the TLS context the tests hand to the judgment client */
    static SSLContext trusting() {
        try {
            var context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new TrustingPeer()}, new SecureRandom());
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The test TLS context cannot be built", e);
        }
    }

    /** Trusts the peer's certificate; the test server signs its own. */
    private static final class TrustingPeer implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authenticationType) {
            // The test client is never a server.
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authenticationType) {
            // The mock web server's self-signed certificate is trusted for this test.
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
