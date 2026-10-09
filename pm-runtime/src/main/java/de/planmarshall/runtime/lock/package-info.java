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
 * The lock manager of the runtime and the stale lease sweep: the implementation of the locking interface of
 * {@code pm-core} (PM-IMPL-7).
 * <p>
 * The lock manager is the one place of the runtime that takes a transaction lock. It keeps one in-process lock per
 * lock key and at most one lock of the operating system per lock file, and it asserts the total lock order on every
 * acquisition. The stale lease sweep decides, at a runtime start, what happens to the leases whose holder is no
 * longer alive. The process locks of the runtime ({@code runtime.lock}, {@code runtime-start.lock}) are not
 * transaction locks and are not taken here.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/module-structure.adoc} (Modules, {@code pm-runtime})</li>
 * <li>{@code doc/specification/store-schemas/13-leases-integrity-testing.adoc}</li>
 * <li>{@code doc/specification/runtime-model/01-processes-and-transport.adoc} (In-Process Lock Manager)</li>
 * </ul>
 */
package de.planmarshall.runtime.lock;
