package com.blocbuy.common.gateway;

/**
 * Port for write-endpoint idempotency (Idempotency-Key header / stable business request number).
 *
 * <p>Skeleton seam only: the implementation arrives with the first write use case. Contract:
 * persist the request fingerprint and its result; the same key with a different request body is a
 * 409 conflict; duplicate submissions return the recorded result without re-executing.
 */
public interface IdempotencyGateway {

  /**
   * Records or replays the outcome of an idempotent request.
   *
   * @param key client-supplied idempotency key
   * @param fingerprint stable hash of the normalized request body
   * @return the previously stored result if the key was already processed with the same
   *     fingerprint; empty if this key is new and processing should proceed
   * @throws com.blocbuy.common.exception.BizException with a 409 code when the key exists with a
   *     different fingerprint
   */
  java.util.Optional<String> tryAcquire(String key, String fingerprint);
}
