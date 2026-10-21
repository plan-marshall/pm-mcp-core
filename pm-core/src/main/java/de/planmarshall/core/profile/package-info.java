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
 * The client profiles: the loader of the profile documents of {@code pm-api}, their validation, the placeholders
 * and the vocabulary translation. No class names a harness.
 * <p>
 * A harness exists for the system as its profile document and as nothing else. The package reads the whole documents of
 * the shipped, machine and project sets with the streaming API of {@code jackson-core}, validates each against the
 * client profile schema and the hardening rules as a whole, resolves the nearest set that counts, and binds a machine
 * or project profile to its verification record and its HMAC sidecar. The stages {@code isolation}, {@code egress} and
 * {@code usage} of the profile verification are recorded here as the values a profile is loaded with.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/client-profiles/01-profile-schema.adoc}</li>
 * <li>{@code doc/specification/client-profiles/02-placeholders.adoc}</li>
 * </ul>
 */
package de.planmarshall.core.profile;
