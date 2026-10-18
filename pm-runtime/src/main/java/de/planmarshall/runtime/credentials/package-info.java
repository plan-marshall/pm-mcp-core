/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The credential stores of the runtime (PM-CRED-2, PM-CRED-3, PM-CRED-4): exactly one
 * {@link de.planmarshall.runtime.credentials.SecretStore} per machine, the one the operator recorded in
 * {@code credentials.store} of {@code machine-config.json}, read and written by
 * {@link de.planmarshall.runtime.credentials.CredentialStoreChoice}. {@code os} is the macOS login Keychain
 * (Security.framework through FFM) or the Linux Secret Service (D-Bus over the JDK Unix domain socket channel);
 * {@code file} is the permission-guarded file store below {@code <PM_MCP_BASE>/credentials/}. The runtime makes no
 * automatic choice and has no fallback: before a store is recorded no credential is entered, and an unavailable
 * recorded OS store makes the credentials unavailable without a file being written in its place. Every keyring entry
 * lives under one service name ({@link de.planmarshall.runtime.credentials.ServiceName}).
 *
 * @since 0.1
 */
package de.planmarshall.runtime.credentials;
