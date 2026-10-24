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
 * The shared HTTP base of the provider modules over {@code cui-http}.
 * <p>
 * {@link de.planmarshall.provider.http.HttpClient} is the one HTTP client of the native clients of
 * the provider adapters: it pins the bearer token to the configured origin (the token is sent only
 * there, and {@code cui-http} strips it on a redirect to another origin), follows {@code Link}
 * pagination with a completeness flag, sends conditional requests with an {@code ETag}, and reports
 * a provider rate limit as an outcome with its reset instant, never as a failure.
 * <p>
 * The module names no forge and no analysis service: it holds the transport with its closed
 * outcomes ({@link de.planmarshall.provider.http.Response}, {@link de.planmarshall.provider.http.Result}),
 * the endpoint the token is pinned to ({@link de.planmarshall.provider.http.Endpoint}), the pages of
 * a paginated read ({@link de.planmarshall.provider.http.PagedResult}), the token seam
 * ({@link de.planmarshall.provider.http.TokenSource}) and the JSON tree of a provider body
 * ({@link de.planmarshall.provider.http.Json}). A provider adapter builds its contract on it.
 */
package de.planmarshall.provider.http;
