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

import java.io.Serial;

/**
 * A credential store operation that failed, with the reason the API reports. Every reason is a failure of the store;
 * a request refused before any store is asked (no store recorded yet, a writer that does not own the setting) is a
 * {@link CredentialStoreChoice.Refusal}.
 */
public class SecretStoreException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Why the operation failed. */
    public enum Reason {
        /**
         * The keyring is locked and unlocking needs the user (a prompt), or the recorded OS store is unavailable for a
         * write; nothing was changed.
         */
        LOCKED("credential_store_locked"),
        /**
         * The recorded OS store is unavailable for a read (a locked collection, a session without the user's session
         * bus, a Security.framework that cannot be loaded); no other store is read in its place (PM-CRED-2).
         */
        UNAVAILABLE("credential_store_unavailable"),
        /** A credential file fails the ownership or permission check (PM-CRED-4). */
        INSECURE("credentials_file_insecure"),
        /** A credential file is not in the current format (PM-CRED-3). */
        INVALID("credentials_file_invalid"),
        /** Any other failure of the backend. */
        FAILED("credential_store_failed");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        /** @return the error code of the API */
        public String code() {
            return code;
        }
    }

    /** The reason. */
    private final Reason reason;

    /**
     * @param reason  the reason
     * @param message the description, never containing a secret
     */
    public SecretStoreException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * @param reason  the reason
     * @param message the description, never containing a secret
     * @param cause   the cause
     */
    public SecretStoreException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** @return the reason */
    public Reason reason() {
        return reason;
    }
}
