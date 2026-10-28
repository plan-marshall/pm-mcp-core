/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.util.Objects;
import java.util.stream.Collectors;

import de.planmarshall.core.store.OutlineRecord.Deliverable;
import de.planmarshall.core.store.OutlineRecord.Profile;

/**
 * Renders {@code outline.md} from the accepted outline record (Store Schemas § Outline Record): deliverable by
 * deliverable, in the section form of {@code plan-marshall}'s solution outline. The rendered document is never read
 * back; the record is the source of truth.
 *
 * @since 0.1
 */
public final class OutlineRenderer {

    private OutlineRenderer() {
    }

    /**
     * @param store the outline store
     * @return the rendered {@code outline.md}, ending with a newline
     */
    public static String render(OutlineStore store) {
        Objects.requireNonNull(store, "store");
        var outlineRecord = store.current().outlineRecord();
        var out = new StringBuilder("# Solution Outline\n\n");
        out.append(outlineRecord.summary()).append("\n\n");
        if (outlineRecord.overview() != null) {
            out.append(outlineRecord.overview()).append("\n\n");
        }
        out.append("**Scope estimate:** ").append(outlineRecord.scopeEstimate().wireName()).append("\n\n");
        for (var deliverable : outlineRecord.deliverables()) {
            renderDeliverable(out, deliverable, store.status(deliverable.number()).orElseThrow());
        }
        return out.toString();
    }

    private static void renderDeliverable(StringBuilder out, Deliverable deliverable,
            OutlineStore.DeliveryStatus status) {
        out.append("## ").append(deliverable.number()).append(". ").append(deliverable.title()).append("\n\n");
        out.append("**Metadata:** ").append(deliverable.changeType().wireName()).append(", ")
                .append(deliverable.executionMode().wireName()).append(", ")
                .append(deliverable.domain()).append("/").append(deliverable.module());
        if (status != OutlineStore.DeliveryStatus.ACTIVE) {
            out.append(", ").append(status.wireName());
        }
        out.append('\n');
        if (!deliverable.depends().isEmpty()) {
            out.append("**Depends on:** ")
                    .append(deliverable.depends().stream().map(String::valueOf).collect(Collectors.joining(", ")))
                    .append('\n');
        }
        out.append("**Intent gloss:** ").append(deliverable.intentGloss()).append("\n\n");
        out.append("**Profiles:** ")
                .append(deliverable.profiles().stream().map(Profile::wireName)
                        .collect(Collectors.joining(", ")))
                .append("\n\n");
        if (!deliverable.affectedFiles().isEmpty()) {
            out.append("**Affected files:**\n");
            for (var file : deliverable.affectedFiles()) {
                out.append("- `").append(file.path()).append("` (").append(file.intent().wireName()).append(")");
                if (file.gate() != null) {
                    out.append(" gate: ").append(file.gate());
                }
                out.append('\n');
            }
            out.append('\n');
        }
        out.append("**Change per file:** ").append(deliverable.changePerFile()).append("\n\n");
        if (!deliverable.verification().isEmpty()) {
            out.append("**Verification:**\n");
            deliverable.verification().forEach(verification -> out.append("- `")
                    .append(verification.command()).append("` — ").append(verification.criterion()).append('\n'));
            out.append('\n');
        }
        out.append("**Satisfies:** ")
                .append(deliverable.satisfies().isEmpty() ? "none" : String.join(", ", deliverable.satisfies()))
                .append("\n\n");
        if (!deliverable.addedDependencies().isEmpty()) {
            out.append("**Added dependencies:**\n");
            deliverable.addedDependencies().forEach(dependency -> out.append("- `")
                    .append(dependency.coordinate()).append("` — ").append(dependency.why())
                    .append(" (rejected: ").append(dependency.rejectedAlternative()).append(")\n"));
            out.append('\n');
        }
        if (!deliverable.interfaces().isEmpty()) {
            out.append("**Interfaces:**\n");
            deliverable.interfaces().forEach(contract -> out.append("- ").append(contract.direction().wireName())
                    .append(" `").append(contract.symbol()).append("`: `").append(contract.signature()).append("`\n"));
            out.append('\n');
        }
        if (!deliverable.keyLinks().isEmpty()) {
            out.append("**Key links:**\n");
            deliverable.keyLinks().forEach(link -> out.append("- `").append(link.from()).append("` → ")
                    .append(link.to()).append(": `").append(link.pattern()).append("`\n"));
            out.append('\n');
        }
        if (deliverable.designNotes() != null) {
            out.append("**Design notes:** ").append(deliverable.designNotes()).append("\n\n");
        }
    }
}
