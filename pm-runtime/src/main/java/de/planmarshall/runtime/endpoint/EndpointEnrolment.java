/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.runtime.launch.HarnessEnrolment;

/**
 * Records the weights identity of an endpoint's models in the harness enrolment (Client Profiles § Weights Identity): at
 * the harness's enrolment, at every start of the runtime and at every project setup run.
 * <p>
 * A model whose identity changed is re-recorded, and the change is audited {@code harness_updated} naming the model and
 * both identities. The identity is a member of the qualification digest, so a model whose weights changed is unqualified
 * until it is graded again; this class records the change and never grades. A model whose identity cannot be read keeps
 * the identity the enrolment holds, and a profile that declares no probe is left as it is.
 *
 * @since 0.1
 */
public final class EndpointEnrolment {

    private final WeightsReader reader;
    private final Writer writer;
    private final AuditSink audit;
    private final InstantSource clock;

    /**
     * @param reader the reading of a model's weights identity
     * @param writer where the enrolment is rewritten
     * @param audit  where a changed identity is recorded
     * @param clock  the clock of the recorded read
     */
    public EndpointEnrolment(WeightsReader reader, Writer writer, AuditSink audit, InstantSource clock) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Reads the identity of every allowed model and records the changed ones beside their model.
     *
     * @param profile  the endpoint profile
     * @param enrolled the enrolment as it is recorded now
     * @param listing  the model objects of the listing; {@code null} when it was not read
     * @return the enrolment after the read, and the models whose identity changed
     */
    public Refreshed refresh(EndpointProfile profile, HarnessEnrolment enrolled, JsonNode listing) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(enrolled, "enrolled");
        if (profile.weightsProbe() == null) {
            return new Refreshed(enrolled, List.of(), false, "the profile declares no weights probe");
        }
        var models = new LinkedHashSet<>(profile.modelTiers().values());
        if (models.isEmpty()) {
            return new Refreshed(enrolled, List.of(), false, "the profile names no model to read");
        }
        var weights = new LinkedHashMap<>(enrolled.weights());
        var changed = new LinkedHashMap<String, String>();
        var dirty = false;
        var read = 0;
        for (var model : models) {
            var reading = reader.read(profile, model, listing);
            if (reading.isRead()) {
                read++;
                var current = weights.get(model);
                var unchanged = current != null && current.weightsId().equals(reading.weightsId());
                if (!unchanged) {
                    weights.put(model, new HarnessEnrolment.Weights(reading.weightsId(), clock.instant()));
                    dirty = true;
                    if (current != null) {
                        changed.put(model, current.weightsId());
                    }
                }
            }
        }
        if (!dirty) {
            return new Refreshed(enrolled, List.of(), false,
                    "%d of %d models read, no identity changed".formatted(read, models.size()));
        }
        var updated = enrolled.withWeights(weights, clock.instant());
        var written = writer.enrol(updated);
        changed.forEach((model, oldIdentity) -> audit.append(
                AuditEvent.harnessWeightsUpdated(enrolled.clientId(), model, oldIdentity, weights.get(model).weightsId())));
        return new Refreshed(written, List.copyOf(changed.keySet()), true,
                "%d of %d models read, %d changed".formatted(read, models.size(), changed.size()));
    }

    /**
     * The enrolment after one read of the weights identities.
     *
     * @param enrolment    the enrolment as it is recorded now
     * @param changedModels the models whose identity changed
     * @param written      whether the store was rewritten
     * @param detail       what happened, for a record
     * @since 0.1
     */
    public record Refreshed(HarnessEnrolment enrolment, List<String> changedModels, boolean written, String detail) {

        /** Copies the models. */
        public Refreshed {
            Objects.requireNonNull(enrolment, "enrolment");
            changedModels = List.copyOf(changedModels);
            Objects.requireNonNull(detail, "detail");
        }

        /** @return the weights identity per model, as the enrolment records it now */
        public Map<String, HarnessEnrolment.Weights> weights() {
            return enrolment.weights();
        }
    }

    /** Where an enrolment is rewritten: the harness enrolment store in production. */
    @FunctionalInterface
    public interface Writer {

        /**
         * @param enrolment the enrolment to write
         * @return the enrolment as written
         */
        HarnessEnrolment enrol(HarnessEnrolment enrolment);
    }
}
