/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Builder;

/**
 * One finding as the findings ledger records it (PM-IMPL-1, PM-IMPL-2): a provider-neutral, normalized finding from
 * a quality gate, a review, a code analysis provider, a CI check or another producer of the closed
 * {@link FindingSourceKind} vocabulary. The components are exactly the members of the schema {@code FindingRecord}
 * (Store Schemas § Findings Ledger Record), in schema order, with {@code format_version} among them.
 * <p>
 * The compact constructor checks every rule the schema states and refuses a violation with an
 * {@link IllegalArgumentException} naming the rule. A required member that is {@code null} is refused with a
 * {@link NullPointerException} naming the member. Writing the record to the ledger is not part of this type.
 *
 * @param formatVersion        the version of the record format; always 1
 * @param findingId            the id of the finding, {@code F-} followed by digits
 * @param source               the kind of producer that filed the finding
 * @param provider             the id of the code analysis provider iff {@code source} is
 *                             {@link FindingSourceKind#ANALYSIS}; {@code null} otherwise
 * @param ciFailure            the failed check iff {@code source} is {@link FindingSourceKind#CI}; {@code null}
 *                             otherwise
 * @param phase                the phase the finding was filed against; required for
 *                             {@link FindingSourceKind#QGATE}, else nullable
 * @param queueTaskId          the queue task whose submit filed the finding; nullable
 * @param batchId              the screening batch that ingested the finding's external text; nullable
 * @param screen               the screen of the finding's external text; {@code null} without external text
 * @param requirementRefs      the requirement ids or specification anchors the finding bears on; nullable
 * @param severity             the severity, set at creation and never changed
 * @param blocking             whether the finding blocks its gate
 * @param status               the status in the finding's lifecycle
 * @param disposition          the triage disposition; nullable
 * @param resolution           the server-written outcome of the finding
 * @param scopeClass           the classification of the finding's path against the footprint; nullable
 * @param carriedTo            where the finding was carried out of the plan; nullable
 * @param filePath             the path the finding names; nullable
 * @param line                 the line the finding names, at least 1; nullable
 * @param summary              the non-empty summary of the finding
 * @param body                 the full text of the finding, untrusted content; nullable
 * @param author               the structured provider login of the author of a review comment; nullable
 * @param botKind              the review-bot registry key resolved from the author; nullable
 * @param raisedAtHead         the HEAD the finding was raised against, 40 lower-case hexadecimal digits; required
 *                             for self-review, quality-gate, code-review, security-audit, architecture-gate and CI
 *                             findings, else nullable
 * @param reviewedCommitSha    the commit the reviewer covered, 40 lower-case hexadecimal digits; nullable
 * @param editMarker           the provider's update instant of an edited comment; nullable
 * @param evidence             the evidence a resolution rested on; nullable
 * @param providerRef          the coordinates of the finding on its provider; nullable
 * @param dispositionRationale the rationale of the disposition; nullable
 * @param transmitOutcome      the outcome of the outbound reply or dismissal
 * @param transmitReason       the reason the transmission did not fully succeed; {@code null} while
 *                             {@code transmitOutcome} is {@link TransmitOutcome#PENDING} or
 *                             {@link TransmitOutcome#SENT}
 * @param transmitDisposition  the disposition the last transmission carried; nullable
 * @param respondedAt          the instant the provider accepted the reply; nullable
 * @param resolveState         the state of the finding's provider thread
 * @param createdAt            the instant the finding was created
 * @param updatedAt            the instant of the last change of the finding; nullable
 * @since 0.1
 */
@Builder(toBuilder = true)
public record Finding(
int formatVersion,
String findingId,
FindingSourceKind source,
String provider,
CiFailure ciFailure,
FindingPhase phase,
String queueTaskId,
String batchId,
Screen screen,
List<String> requirementRefs,
Severity severity,
boolean blocking,
FindingStatus status,
Disposition disposition,
Resolution resolution,
ScopeClass scopeClass,
CarriedTo carriedTo,
String filePath,
Integer line,
String summary,
String body,
String author,
String botKind,
String raisedAtHead,
String reviewedCommitSha,
Instant editMarker,
Evidence evidence,
ProviderRef providerRef,
String dispositionRationale,
TransmitOutcome transmitOutcome,
TransmitReason transmitReason,
String transmitDisposition,
Instant respondedAt,
ResolveState resolveState,
Instant createdAt,
Instant updatedAt) {

    /** The only version of the record format. */
    public static final int FORMAT_VERSION = 1;

    private static final Pattern FINDING_ID = Pattern.compile("^F-\\d+$");
    private static final Pattern QUEUE_TASK_ID = Pattern.compile("^qt-[0-9a-f]{16}$");
    private static final Pattern BATCH_ID = Pattern.compile("^BT-\\d+$");
    private static final Pattern BOT_KIND = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");
    /** 40 digits only: the schema {@code FindingRecord} admits no other length for a commit of the ledger. */
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");
    private static final int REQUIREMENT_REF_MAX_LENGTH = 120;
    private static final String CHARACTERS = " characters";
    private static final Set<FindingSourceKind> HEAD_REQUIRED = EnumSet.of(FindingSourceKind.SELF_REVIEW,
            FindingSourceKind.QGATE, FindingSourceKind.CODE_REVIEW, FindingSourceKind.SECURITY_AUDIT,
            FindingSourceKind.ARCH_GATE, FindingSourceKind.CI);

    /**
     * @throws NullPointerException     if a required member is {@code null}
     * @throws IllegalArgumentException if a rule of the schema is violated; the message names the rule
     */
    public Finding {
        Objects.requireNonNull(findingId, "finding_id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(transmitOutcome, "transmit_outcome");
        Objects.requireNonNull(resolveState, "resolve_state");
        Objects.requireNonNull(createdAt, "created_at");
        requireThat(formatVersion == FORMAT_VERSION, "format_version must be " + FORMAT_VERSION);
        requireMatch(FINDING_ID, findingId, "finding_id");
        checkSourceMembers(source, provider, ciFailure, phase, raisedAtHead, providerRef);
        requireMatchIfSet(QUEUE_TASK_ID, queueTaskId, "queue_task_id");
        requireMatchIfSet(BATCH_ID, batchId, "batch_id");
        if (requirementRefs != null) {
            requirementRefs = List.copyOf(requirementRefs);
            requireThat(requirementRefs.stream().allMatch(
                            ref -> !ref.isEmpty() && ref.length() <= REQUIREMENT_REF_MAX_LENGTH),
                    "every requirement_refs item must be 1 to " + REQUIREMENT_REF_MAX_LENGTH + CHARACTERS);
        }
        requireThat(line == null || line >= 1, "line must be at least 1");
        requireThat(!summary.isEmpty(), "summary must not be empty");
        requireMatchIfSet(BOT_KIND, botKind, "bot_kind");
        requireMatchIfSet(COMMIT_SHA, raisedAtHead, "raised_at_head");
        requireMatchIfSet(COMMIT_SHA, reviewedCommitSha, "reviewed_commit_sha");
        requireThat(transmitReason == null
                || transmitOutcome != TransmitOutcome.PENDING && transmitOutcome != TransmitOutcome.SENT,
                "transmit_reason must be null when transmit_outcome is pending or sent");
    }

    private static void checkSourceMembers(FindingSourceKind source, String provider, CiFailure ciFailure,
            FindingPhase phase, String raisedAtHead, ProviderRef providerRef) {
        var analysis = source == FindingSourceKind.ANALYSIS;
        requireThat(analysis == (provider != null), "provider is required iff source is analysis");
        requireThat(provider == null || FindingSourceKey.isProviderId(provider),
                "provider must match the provider id pattern");
        requireThat((source == FindingSourceKind.CI) == (ciFailure != null),
                "ci_failure is required iff source is ci");
        requireThat(source != FindingSourceKind.QGATE || phase != null, "phase is required for source qgate");
        requireThat(!HEAD_REQUIRED.contains(source) || raisedAtHead != null,
                "raised_at_head is required for source " + source.wireName());
        requireThat(analysis || providerRef == null || providerRef.issueKey() == null,
                "provider_ref.issue_key is set only for source analysis");
    }

    private static void requireMatchIfSet(Pattern pattern, String value, String member) {
        if (value != null) {
            requireMatch(pattern, value, member);
        }
    }

    private static void requireMatch(Pattern pattern, String value, String member) {
        requireThat(pattern.matcher(value).matches(), member + " must match " + pattern.pattern());
    }

    private static void requireThat(boolean rule, String message) {
        if (!rule) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * The failed CI check a finding of {@link FindingSourceKind#CI} reports (member {@code ci_failure}).
     *
     * @param checkName     the non-empty provider name of the failed check or pipeline job
     * @param failureClass  the class of the failure, from the provider's conclusion
     * @param failureKind   the kind of the failure, from the check's log
     * @param requiredCheck whether the check is a required check of the base branch
     */
    public record CiFailure(String checkName, FailureClass failureClass, FailureKind failureKind,
    boolean requiredCheck) {

        /**
         * @throws NullPointerException     if a member is {@code null}
         * @throws IllegalArgumentException if the check name is empty
         */
        public CiFailure {
            Objects.requireNonNull(checkName, "check_name");
            Objects.requireNonNull(failureClass, "failure_class");
            Objects.requireNonNull(failureKind, "failure_kind");
            requireThat(!checkName.isEmpty(), "ci_failure.check_name must not be empty");
        }

        /**
         * The closed class of a CI failure, from the provider's conclusion.
         */
        public enum FailureClass {
            /** The check failed. */
            FAILURE("failure"),
            /** The check was cancelled. */
            CANCELLED("cancelled"),
            /** The check timed out. */
            TIMED_OUT("timed_out"),
            /** The check waits for an action. */
            ACTION_REQUIRED("action_required"),
            /** The check is stale. */
            STALE("stale"),
            /** The check failed to start. */
            STARTUP_FAILURE("startup_failure"),
            /** The check was not triggered. */
            NOT_TRIGGERED("not_triggered"),
            /** The change has no checks. */
            NO_CHECKS("no_checks");

            private final String wireName;

            FailureClass(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in the findings store */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in the findings store
             * @return the value of that name; empty if no value has it
             */
            public static Optional<FailureClass> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }

        /**
         * The closed kind of a CI failure, parsed from the check's log; {@link #UNKNOWN} is handled fail-closed.
         */
        public enum FailureKind {
            /** The build did not compile. */
            COMPILE("compile"),
            /** A test failed. */
            TEST("test"),
            /** A linter reported a violation. */
            LINT("lint"),
            /** A policy check failed. */
            POLICY("policy"),
            /** No parser recognised the log, or the log is unreadable. */
            UNKNOWN("unknown");

            private final String wireName;

            FailureKind(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in the findings store */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in the findings store
             * @return the value of that name; empty if no value has it
             */
            public static Optional<FailureKind> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }
    }

    /**
     * The level-2 screen of a finding's external text (member {@code screen}).
     *
     * @param verdict  the verdict of the screen
     * @param withheld whether the text is withheld from every model; true iff the verdict is
     *                 {@link Verdict#SUSPICIOUS}
     * @param spans    the flagged spans of a suspicious text, operator and audit data only; empty if none
     */
    public record Screen(Verdict verdict, boolean withheld, List<Span> spans) {

        /**
         * @throws NullPointerException     if the verdict is {@code null}
         * @throws IllegalArgumentException if {@code withheld} disagrees with the verdict
         */
        public Screen {
            Objects.requireNonNull(verdict, "verdict");
            requireThat(withheld == (verdict == Verdict.SUSPICIOUS),
                    "screen.withheld must be true iff verdict is suspicious");
            spans = spans == null ? List.of() : List.copyOf(spans);
        }

        /**
         * One flagged span of a suspicious text.
         *
         * @param start  the start offset, at least 0
         * @param end    the end offset, at least 0
         * @param reason why the span is flagged, at most 200 characters
         */
        public record Span(int start, int end, String reason) {

            private static final int REASON_MAX_LENGTH = 200;

            /**
             * @throws NullPointerException     if the reason is {@code null}
             * @throws IllegalArgumentException if an offset is negative or the reason is too long
             */
            public Span {
                Objects.requireNonNull(reason, "reason");
                requireThat(start >= 0, "screen.spans.start must be at least 0");
                requireThat(end >= 0, "screen.spans.end must be at least 0");
                requireThat(reason.length() <= REASON_MAX_LENGTH,
                        "screen.spans.reason must be at most " + REASON_MAX_LENGTH + CHARACTERS);
            }
        }

        /**
         * The closed verdict of a screen; a missing or unparseable verdict is recorded {@link #SUSPICIOUS}.
         */
        public enum Verdict {
            /** The text is clean. */
            CLEAN("clean"),
            /** The text is suspicious and withheld. */
            SUSPICIOUS("suspicious");

            private final String wireName;

            Verdict(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in the findings store */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in the findings store
             * @return the value of that name; empty if no value has it
             */
            public static Optional<Verdict> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }
    }

    /**
     * Where a finding still pending after the merge was carried out of the plan (member {@code carried_to}).
     *
     * @param target    the kind of destination
     * @param ref       the inbox message id or lesson id
     * @param carriedAt the instant the destination accepted the record
     */
    public record CarriedTo(Target target, String ref, Instant carriedAt) {

        /**
         * @throws NullPointerException if a member is {@code null}
         */
        public CarriedTo {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(carriedAt, "carried_at");
        }

        /**
         * The closed kind of destination of a carried finding.
         */
        public enum Target {
            /** The inbox of the parent epic. */
            EPIC_INBOX("epic_inbox"),
            /** A lesson. */
            LESSON("lesson");

            private final String wireName;

            Target(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in the findings store */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in the findings store
             * @return the value of that name; empty if no value has it
             */
            public static Optional<Target> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }
    }

    /**
     * The evidence a resolution rested on (member {@code evidence}).
     *
     * @param evidenceClass the class of the evidence (member {@code class})
     * @param ref           the file and line, job id or URL the resolution rests on; nullable
     * @param claimRefuted  whether the evidence refutes the finding's claim
     */
    public record Evidence(EvidenceClass evidenceClass, String ref, boolean claimRefuted) {

        /**
         * @throws NullPointerException if the class is {@code null}
         */
        public Evidence {
            Objects.requireNonNull(evidenceClass, "class");
        }

        /**
         * The closed class of evidence.
         */
        public enum EvidenceClass {
            /** The refutation sub-state of the verifier. */
            VERIFIER("verifier"),
            /** Substitute evidence with a concrete reference. */
            SUBSTITUTE("substitute"),
            /** No evidence. */
            NONE("none");

            private final String wireName;

            EvidenceClass(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in the findings store */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in the findings store
             * @return the value of that name; empty if no value has it
             */
            public static Optional<EvidenceClass> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }
    }

    /**
     * The coordinates of a finding on its provider, for the outbound reply or dismissal (member
     * {@code provider_ref}).
     *
     * @param prNumber    the number of the pull request, at least 1; nullable
     * @param threadId    the id of the review thread; nullable
     * @param commentId   the id of the comment; nullable
     * @param commentKind the kind of the comment; nullable
     * @param issueKey    the analysis provider's own key of the issue, at most 256 characters, set only for an
     *                    analysis finding; nullable
     */
    public record ProviderRef(Integer prNumber, String threadId, String commentId, CommentKind commentKind,
    String issueKey) {

        private static final int ISSUE_KEY_MAX_LENGTH = 256;

        /**
         * @throws IllegalArgumentException if the pull-request number is less than 1 or the issue key is too long
         */
        public ProviderRef {
            requireThat(prNumber == null || prNumber >= 1, "provider_ref.pr_number must be at least 1");
            requireThat(issueKey == null || issueKey.length() <= ISSUE_KEY_MAX_LENGTH,
                    "provider_ref.issue_key must be at most " + ISSUE_KEY_MAX_LENGTH + CHARACTERS);
        }

        /**
         * The closed kind of a provider comment.
         */
        public enum CommentKind {
            /** A comment in a review thread. */
            REVIEW_THREAD("review_thread"),
            /** A comment on the issue of the pull request. */
            ISSUE_COMMENT("issue_comment"),
            /** The body of a review. */
            REVIEW_BODY("review_body");

            private final String wireName;

            CommentKind(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in the findings store */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in the findings store
             * @return the value of that name; empty if no value has it
             */
            public static Optional<CommentKind> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }
    }
}
