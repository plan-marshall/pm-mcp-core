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
 * The integrity of the project enrolments (PM-EXEC-2, PM-SEC-4, PM-SEC-5, PM-MIG-1, PM-IMPL-7): the runtime's side of
 * the enrolment store of {@code pm-core}.
 * <ul>
 * <li>{@link de.planmarshall.runtime.enrolment.EnrolmentKey}: the operator-lane integrity key, from the active
 * credential store under the account {@code enrolment-integrity-key}, or on a host without a keyring from
 * {@code <PM_MCP_BASE>/state/enrolment-hmac.key} (mode {@code 0600}); created on the first enrolment write, never
 * logged and never put into a message.</li>
 * <li>{@link de.planmarshall.runtime.enrolment.EnrolmentHmac}: the HMAC-SHA-256 over the exact canonical bytes of an
 * enrolment file and the sidecar record that carries it with their size and SHA-256.</li>
 * <li>{@link de.planmarshall.runtime.enrolment.EnrolmentFileCheck}: the per-use checks of an enrolment file and its
 * sidecar (owner, mode, no symbolic link), refused with {@code enrolment_insecure}.</li>
 * <li>{@link de.planmarshall.runtime.enrolment.EnrolmentCheck}: the enrolment check, callable before a project is
 * served and before every verify-not-resolve check; a refused project ({@code enrolment_tampered},
 * {@code enrolment_insecure}) blocks only itself.</li>
 * <li>{@link de.planmarshall.runtime.enrolment.Enrolments}: the operator writes {@code enrol}, {@code unenrol}
 * (refused with {@code scope_active} while the project has live work) and the re-baseline of
 * {@code accept-edit enrolment}, each in one transaction under the project's enrolment lock and audited.</li>
 * </ul>
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/store-schemas/11-enrolment-store.adoc} (Project Enrolment Store)</li>
 * <li>{@code doc/specification/cli-and-security/02-credentials-and-operator.adoc} (Enrolment Integrity; Enrolment,
 * Lifecycle Gate &amp; Release)</li>
 * </ul>
 *
 * @since 0.1
 */
package de.planmarshall.runtime.enrolment;
