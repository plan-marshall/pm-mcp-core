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
 * The endpoint harness: a model this machine serves over HTTP on the loopback interface, or one service that answers the
 * protocol {@code system_one} over HTTPS in the remote form (PM-CLIENT-2, PM-ARCH-2, PM-CRED-5, Client Profiles §
 * Endpoint Harness, § Judgment Endpoint).
 * <p>
 * The package sends the one request of a task ({@link de.planmarshall.runtime.endpoint.EndpointRequest}), calls the
 * endpoint through {@code cui-http} ({@link de.planmarshall.runtime.endpoint.EndpointClient}), accepts the answer and
 * records its usage ({@link de.planmarshall.runtime.endpoint.EndpointOutcome}), checks before every request that the
 * enrolled program owns the listening socket ({@link de.planmarshall.runtime.endpoint.SocketOwner},
 * {@link de.planmarshall.runtime.endpoint.EndpointHarness}), reads the weights identity of the models
 * ({@link de.planmarshall.runtime.endpoint.WeightsReader}) and records it in the enrolment
 * ({@link de.planmarshall.runtime.endpoint.EndpointEnrolment}), and verifies a draft by the stages {@code identity},
 * {@code launch}, {@code classes} and {@code limits} ({@link de.planmarshall.runtime.endpoint.EndpointVerification}).
 * <p>
 * The protocol {@code system_one} composes the two typed requests of a judgment task
 * ({@link de.planmarshall.runtime.endpoint.SystemOneRequest}), reads and checks each answer
 * ({@link de.planmarshall.runtime.endpoint.SystemOneAnswer}), sends them with a pinned origin and without a redirect
 * ({@link de.planmarshall.runtime.endpoint.JudgmentClient}), runs the task and records it
 * ({@link de.planmarshall.runtime.endpoint.JudgmentEndpoint}), and verifies the draft without the {@code classes} stage
 * ({@link de.planmarshall.runtime.endpoint.JudgmentVerification}).
 * <p>
 * Two invariants hold across the package: the runtime never starts, stops or configures the serving program, and no
 * task text is sent before the owner check has found the enrolled program on the port (the verification checks once,
 * before any of its requests; the remote form has no serving program and pins the credential entry's origin instead).
 * The answer is always validated by the server ({@link de.planmarshall.runtime.endpoint.AnswerSchema},
 * {@link de.planmarshall.runtime.endpoint.SystemOneAnswer}), never trusted because the endpoint enforces a schema.
 * <p>
 * Specification: {@code doc/specification/client-profiles/03-endpoint-harness.adoc} and
 * {@code doc/specification/client-profiles/04-judgment-endpoint.adoc}. Design decisions:
 * {@code pm-runtime/doc/adr/runtime-endpoint/}.
 *
 * @since 0.1
 */
package de.planmarshall.runtime.endpoint;
