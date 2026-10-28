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

import de.cuioss.tools.logging.CuiLogger;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.HarnessUsage;
import de.planmarshall.runtime.log.RuntimeLogMessages;

/**
 * A task of a judgment role (Client Profiles § Judgment Endpoint): the two requests of {@link SystemOneRequest}, the
 * server's own check of each answer by {@link SystemOneAnswer}, and the one record the task leaves.
 * <p>
 * The attempt is the task: both requests are sent from one composed entity, nothing is retried inside the attempt, and
 * an answer that does not validate, an HTTP error (including {@code 429} with a {@code retry-after}), a timeout, a
 * refused credential or a redirect ends the attempt like a worker without a valid result. The task's own rules then
 * apply. Logs and the audit record hold the endpoint, the model id, the status, the token counts and the duration, and
 * never a request body or the credential.
 *
 * @since 0.1
 */
public final class JudgmentEndpoint {

    private static final CuiLogger LOGGER = new CuiLogger(JudgmentEndpoint.class);

    private final JudgmentClient client;
    private final AuditSink audit;
    private final Clock clock;

    /**
     * @param client the one request of the protocol
     * @param audit  where the record of the task is appended
     * @param clock  the clock of the measured duration
     */
    public JudgmentEndpoint(JudgmentClient client, AuditSink audit, Clock clock) {
        this.client = Objects.requireNonNull(client, "client");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Runs one task: the request whose options are sorted by {@code branch_key}, then the request in the opposite order
     * once the first answered.
     *
     * @param profile   the judgment endpoint profile
     * @param model     the versioned model id of the role
     * @param state     the facts of the vote entity
     * @param questions the questions of the entity
     * @return the two attempts of the task, and the composition the requests were sent from
     */
    public Answers task(EndpointProfile profile, String model, JsonNode state,
            List<SystemOneRequest.Question> questions) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(model, "model");
        var composed = SystemOneRequest.compose(profile, model, state, questions);
        var started = clock.millis();
        if (!composed.isComposed()) {
            var refused = new Attempt(EndpointOutcome.refused(EndpointOutcome.Reason.REQUEST_REFUSED,
                    composed.detail()), null);
            var refusal = new Answers(null, refused, null);
            record(profile, model, refusal, started);
            return refusal;
        }
        var options = SystemOneRequest.options(questions);
        var ascending = attempt(profile, model, composed.request().ascending(), options);
        Attempt descending = null;
        if (ascending.answered()) {
            descending = attempt(profile, model, composed.request().descending(), options);
        }
        var answers = new Answers(composed.request(), ascending, descending);
        record(profile, model, answers, started);
        return answers;
    }

    private Attempt attempt(EndpointProfile profile, String model, String body, Map<String, List<String>> options) {
        var response = client.post(profile, SystemOneRequest.PATH, body);
        if (response.failure() == EndpointClient.Failure.REDIRECT) {
            return new Attempt(EndpointOutcome.refused(EndpointOutcome.Reason.REDIRECT_REFUSED, response.detail()),
                    null);
        }
        if (response.failure() == EndpointClient.Failure.REFUSED) {
            return new Attempt(EndpointOutcome.refused(EndpointOutcome.Reason.REQUEST_REFUSED, response.detail()), null);
        }
        if (response.failure() == EndpointClient.Failure.TIMEOUT) {
            return new Attempt(EndpointOutcome.ended(EndpointOutcome.Reason.TIMEOUT, response.detail()), null);
        }
        if (!response.successful()) {
            return new Attempt(EndpointOutcome.ended(EndpointOutcome.Reason.HTTP_ERROR,
                    "HTTP %d".formatted(response.status())), null);
        }
        var read = SystemOneAnswer.read(response.body(), model, options);
        if (!read.isValid()) {
            var reason = read.refusal() == SystemOneAnswer.Refusal.MODEL_ALIAS
                    ? EndpointOutcome.Reason.MODEL_ALIAS
                    : EndpointOutcome.Reason.INVALID_ANSWER;
            return new Attempt(EndpointOutcome.ended(reason, read.detail()), null);
        }
        var answer = read.answer();
        return new Attempt(EndpointOutcome.answered(CanonicalJson.write(answer.document()), answer.usage()), answer);
    }

    /**
     * Appends the one record of the task and logs its status, both without a body or a credential.
     */
    private void record(EndpointProfile profile, String model, Answers answers, long started) {
        var endpoint = EndpointClient.shown(URI.create(profile.endpoint().baseUrl()));
        var status = answers.status();
        var duration = Math.max(0, clock.millis() - started);
        var input = tokens(answers, true);
        var output = tokens(answers, false);
        audit.append(AuditEvent.judgmentTaskCompleted(endpoint, model, status, input, output, duration));
        if (answers.answered()) {
            LOGGER.info(RuntimeLogMessages.INFO.JUDGMENT_TASK_ANSWERED, model, endpoint, input, output, duration);
        } else {
            LOGGER.warn(RuntimeLogMessages.WARN.ENDPOINT_ATTEMPT_ENDED, model, endpoint, answers.detail());
        }
    }

    private static long tokens(Answers answers, boolean input) {
        var ascending = answers.ascending();
        var descending = answers.descending();
        return measured(ascending, input) + (descending == null ? 0 : measured(descending, input));
    }

    private static long measured(Attempt attempt, boolean input) {
        if (attempt == null) {
            return 0;
        }
        HarnessUsage usage = attempt.outcome().usage();
        var figure = input ? usage.inputTokens() : usage.outputTokens();
        return figure.isMeasured() ? figure.value() : 0;
    }

    /**
     * One of the two attempts of a task.
     *
     * @param outcome the outcome of the attempt
     * @param answer  the validated answer; {@code null} when the attempt ended without one
     * @since 0.1
     */
    public record Attempt(EndpointOutcome outcome, SystemOneAnswer.Answer answer) {

        /** Refuses a missing outcome. */
        public Attempt {
            Objects.requireNonNull(outcome, "outcome");
        }

        /** @return whether the attempt ended with a valid answer */
        public boolean answered() {
            return outcome.fate() == EndpointOutcome.Fate.ANSWERED;
        }
    }

    /**
     * The two attempts of one task and the composition they were sent from.
     *
     * @param request    the two bodies; {@code null} when the entity could not be composed
     * @param ascending  the attempt whose options are sorted by {@code branch_key}
     * @param descending the attempt in the opposite order; {@code null} when the first ended the attempt
     * @since 0.1
     */
    public record Answers(SystemOneRequest.Composed request, Attempt ascending, Attempt descending) {

        /** Refuses a missing first attempt. */
        public Answers {
            Objects.requireNonNull(ascending, "ascending");
        }

        /** @return whether both requests answered with a valid answer */
        public boolean answered() {
            return ascending.answered() && descending != null && descending.answered();
        }

        /** @return the status the task ended with: {@code answered}, or the reason that ended it */
        public String status() {
            if (answered()) {
                return EndpointOutcome.Fate.ANSWERED.wireName();
            }
            if (!ascending.answered()) {
                return ascending.outcome().reason().wireName();
            }
            return descending == null ? EndpointOutcome.Fate.ENDED.wireName() : descending.outcome().reason().wireName();
        }

        /** @return what ended the task, for a record */
        public String detail() {
            return ascending.answered() && descending != null ? descending.outcome().detail()
                    : ascending.outcome().detail();
        }
    }
}
