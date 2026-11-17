/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Compiles a JSON schema of an implementing module and validates a document against it (test helper of the schema
 * tests of {@code pm-core}).
 * <p>
 * The schema-parity tests of this module read the schema files below {@code src/main/resources} through
 * {@link SchemaResource} and check the documents their codecs write against them. They did each build the validator
 * themselves; since the validator moved to the 3.x line the call is one place, so a later change of the library's entry
 * points is a change here and not in every test (the answer to B5 of PLAN-V2-130).
 * <p>
 * The helper is test code and is deliberately NOT part of the published {@code tests} artifact of {@code pm-core}
 * (ADR {@code doc/adr/012} and {@code doc/adr/016}): the artifact carries the generator package, the assertion-message
 * scanner and {@link SchemaResource}, and handing the validator's types to consumers is not wanted.
 * <p>
 * The registry is held once and reused: it is thread-safe while its configuration is not modified, and a registry
 * caches the schemas it compiles. The default dialect is draft 2020-12, the dialect every schema of this module
 * declares, and the result of a validation is the list of {@link Error} items the 3.x line returns — empty for a valid
 * document.
 */
@UtilityClass
public class SchemaValidation {

    /** The registry every schema of the module is compiled in; draft 2020-12 is the default dialect. */
    private static final SchemaRegistry REGISTRY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    /**
     * Compiles schema text into a schema a document can be validated against.
     *
     * @param schemaText the schema document as JSON text
     * @return the compiled schema
     */
    public static Schema compile(String schemaText) {
        return REGISTRY.getSchema(schemaText);
    }

    /**
     * Validates a document against a compiled schema.
     *
     * @param schema the compiled schema
     * @param document the document as JSON text
     * @return the violations the schema reports; an empty list for a valid document
     */
    public static List<Error> validate(Schema schema, String document) {
        return schema.validate(document, InputFormat.JSON);
    }
}
