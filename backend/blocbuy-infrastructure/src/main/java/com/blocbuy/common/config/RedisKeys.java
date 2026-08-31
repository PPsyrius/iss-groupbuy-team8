package com.blocbuy.common.config;

/** Redis key conventions. Every key has an owner, a TTL, and a max cardinality — defined at use site. */
public final class RedisKeys {

  /** Key prefix: blocbuy:{env}:{domain}:{type}:{id}. */
  public static final String PREFIX = "blocbuy";

  private RedisKeys() {}

  /** Builds a full key; callers must always set a TTL when writing. */
  public static String key(String env, String domain, String type, String id) {
    return PREFIX + ":" + env + ":" + domain + ":" + type + ":" + id;
  }
}
