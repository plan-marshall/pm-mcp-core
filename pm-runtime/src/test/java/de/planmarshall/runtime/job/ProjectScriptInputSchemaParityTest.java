/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.store.ProjectEnrolment;

/**
 * The {@code worker_kind} enumeration of the project-script input schema is the wire-name list of
 * {@link ProjectEnrolment.WorkerKind}, in its order: a kind the enrolment file accepts is a kind the payload names, and
 * the reverse. The schema is the file {@code project-script-input-v1.schema.json} in
 * {@code pm-runtime/src/main/resources/de/planmarshall/runtime/job/}, read through {@link SchemaResource}. The member
 * names are the wire format; they are read from the type and the file, never copied here.
 */
@DisplayName("Project-script input schema parity")
class ProjectScriptInputSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/runtime/job/project-script-input-v1.schema.json";

    @Test
    @DisplayName("the worker kinds are the enrolment type's, in its order")
    void workerKinds() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));
        var kind = SchemaResource.object(properties.get("worker_kind"));

        assertEquals(Arrays.stream(ProjectEnrolment.WorkerKind.values())
                        .map(ProjectEnrolment.WorkerKind::wireName).toList(),
                names(SchemaResource.array(kind.get("enum"))),
                "The worker kinds are the type's in order");
    }

    @Test
    @DisplayName("the format version is the one version every document declares")
    void formatVersion() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));

        assertEquals(1, ((Number) SchemaResource.object(properties.get("format_version")).get("const")).intValue(),
                "The format version is one");
    }

    private static List<String> names(Iterable<?> values) {
        var names = new ArrayList<String>();
        values.forEach(value -> names.add(String.valueOf(value)));
        return names;
    }
}
