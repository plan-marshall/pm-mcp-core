envelope_version=1
sender_type=plan
sender_id=http-baseline
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T14:44:44Z

Candidate lesson (source: review-bot finding d89a45, sourcery-ai on PR #28, remediated in run; plan http-baseline).

The mock web server dispatchers written for the provider tests (CiHttpClientTest, GitHubClientTest, InstallationTokensTest, GitLabClientTest) reused a configured route response for every request, so a client sending the same request twice (including a duplicate mutation call) still got success and the tests passed. Fix (TASK-9): each configured response is consumed once; a route with no responses left answers 404.

Rule candidate: when writing a route-based dispatcher for MockWebServer (cui-http-testing), make stubbed responses single-use so duplicate or unexpected requests fail visibly, rather than relying on tests to assert request counts.

Component candidates: pm-dev-java-cui:cui-http-testing (dispatcher guidance), pm-mcp-core provider test fixtures.
