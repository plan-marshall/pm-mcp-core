/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

/**
 * The parsed {@code instance} header line of a plan instance version (PM-IMPL-5, PM-EXT-7): the members of the
 * {@code PlanInstanceHeader} schema the engine reads, and the header JSON exactly as it was read. The members the tree
 * does not model stay in the JSON.
 *
 * @param planId  the identifier of the plan
 * @param version the instance version
 * @param event   the composition event that produced the version
 * @param inputs  every input the version was composed from, named by its digest
 * @param json    the header JSON as read
 * @since 0.1
 */
public record InstanceHeader(String planId, int version, String event, InstanceInputs inputs, String json) {
}
