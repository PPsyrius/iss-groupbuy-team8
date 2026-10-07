# refactor(groupbuy): type START and JOIN receipt fingerprint encoding

## Background

A generic `Object...` fingerprint entry point in the paid service makes operation field order implicit. Receipt hashes are persisted, so a refactor must retain historical start-v1/join-v1 identity, including decimal scale, delimiter safety and UTF-16 length prefixes.

## Scope

1. Extract package-private typed START and JOIN encoding entry points.
2. Share the payment-field encoder and retain existing SHA-256/UTF-8 hashing.
3. Pin both historical hashes with fixed UUIDs, scaled amounts and an initiation reference containing delimiters and a supplementary Unicode character.

## Non-goals

- Changing the fingerprint version or persisted identity, including sharer attribution in the hash, new migrations, or replacing the receipt repository.

## Acceptance Criteria

- [x] Golden START and JOIN hashes match the pre-refactor implementation byte for byte.
- [x] Decimal scale, field order, UTF-16 prefix lengths and operation tags remain unchanged.
- [x] Changed payment/intent facts are rejected and identical requests replay their original receipts.
- [x] Full backend verification passes without a schema or public contract change.

## Traceability

Related use cases: UC3/UC4 (#26/#27). Owner: Wang Ziang (Eric), @EricWang1358. Evidence: NFR-56 and [October 7 log](../log/2026-10-07.md). Local implementation only; publication and merge are pending.
