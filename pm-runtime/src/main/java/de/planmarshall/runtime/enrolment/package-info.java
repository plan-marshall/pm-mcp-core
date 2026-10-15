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
 * {@code <PM_MCP_BASE>/state/enrolment-hmac.key} (mode {@code 0600}); created on the first enrolment write under the
 * credentials lock ({@code EnrolmentKey.creationLock}), taken before any enrolment lock, so two runtime instances
 * never create two keys; a keyring entry is read back after its write and the key it holds is adopted. Never logged
 * and never put into a message.</li>
 * <li>{@link de.planmarshall.runtime.enrolment.EnrolmentHmac}: the HMAC-SHA-256 over the exact canonical bytes of an
 * enrolment file and the sidecar record that carries it with their size and SHA-256.</li>
 * <li>{@link de.planmarshall.runtime.enrolment.EnrolmentFileCheck}: the per-use checks of an enrolment file and its
 * sidecar (owner, mode, no symbolic link), refused with {@code enrolment_insecure}, bound to one no-follow snapshot
 * of the file: the attributes are read before the open and after the read, the file is opened and read without
 * following a link, and a file that changed while it was read is refused; the bytes of a link target are never
 * read.</li>
 * <li>{@link de.planmarshall.runtime.enrolment.EnrolmentCheck}: the enrolment check over the snapshots of the file and
 * its sidecar, run by the project's recovery as its first step before the project is served and by the
 * verify-not-resolve check of jobs as its first stage ({@code VerifyNotResolvePipeline} of {@code runtime.job});
 * {@code enter} and the operator verbs do not run it yet. A refused project ({@code enrolment_tampered},
 * {@code enrolment_insecure}, and a refused strict read, audited as {@code enrolment_invalid}) blocks only
 * itself.</li>
 * <li>{@code EnrolmentPolicy}: the rules an enrolment is checked against before it is signed (placeholders of an
 * argument vector, write paths against the base directory, refused build environment names), refused as
 * {@code enrolment_invalid}.</li>
 * <li>{@link de.planmarshall.runtime.enrolment.Enrolments}: the operator writes {@code enrol}, {@code unenrol}
 * (refused with {@code scope_active} while the project has live work) and the re-baseline of
 * {@code accept-edit enrolment}, each in one transaction under the project's enrolment lock, {@code enrol} and the
 * re-baseline under the credentials lock taken first; {@code enrol} writes the file and its sidecar as one atomic pair
 * of the store. Each owes its audit record in the project's intent file before the mutation, as the unconfirmed
 * record of an attempt, confirms it as succeeded once the mutation completed and hands it over, at least once.
 * {@code replayOwedAudit(String)} hands over what one project owes (step 2 of its recovery),
 * {@code replayOwedAuditOfUnenrolledProjects} what projects no longer enrolled owe (the background pass of the
 * recovery), and {@code replayOwedAudit()} sweeps every project; a sweep continues past a project whose intent file is
 * refused or fails on I/O and throws the first failure, the later ones suppressed. The re-baseline reads, checks and
 * signs one no-follow snapshot of the file, and its {@code enrolment_insecure} refusal is logged and audited like the
 * enrolment check's.</li>
 * </ul>
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/store-schemas/11-enrolment-store.adoc} (Project Enrolment Store)</li>
 * <li>{@code doc/specification/credentials-and-enrolment/02-enrolment-and-project-lifecycle.adoc} (Enrolment Integrity; Enrolment,
 * Lifecycle Gate &amp; Release)</li>
 * </ul>
 *
 * @since 0.1
 */
package de.planmarshall.runtime.enrolment;
