envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:22:38Z

component=pm-runtime
category=improvement
source=pr-comment e03ed7 (PR #90, coderabbitai review body, nitpick 1 fixed TASK-27, nitpick 2 declined)

# Cache the Keychain store instead of rebuilding it per credential operation

## Observation

`ProbedOsStore.available()` rebuilt the OS store before every get/put/delete/entries. On macOS each construction ran `SymbolLookup.libraryLookup(..., Arena.global())` and about 15 downcall handles, and a global-arena lookup is never released. On Linux it doubled the Secret Service bus round trips. Nitpick 1 was taken: the Keychain store is loaded once per `CredentialStoreChoice`. Nitpick 2 (parity of the machine-config defaults with the schema) was declined: the schema belongs to the onboarding work package and is not yet loadable here.

## Rule

Probe per operation only for availability that can actually change at runtime (Secret Service lock state). Load a native library binding (Security.framework) once. A probe wrapper should not silently change cost from O(1) to O(native setup) per request.
