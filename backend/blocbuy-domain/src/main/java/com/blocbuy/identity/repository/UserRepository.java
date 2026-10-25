package com.blocbuy.identity.repository;

import com.blocbuy.identity.model.User;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository port for the User aggregate. Implemented in the infrastructure layer; DOs and MyBatis
 * mappers never cross this boundary. All queries filter soft-deleted rows.
 */
public interface UserRepository {

    /** Active (non-deleted) user by primary key. */
    Optional<User> findById(UUID id);

    /**
     * Active (non-deleted) users among these ids, in one read and in no particular order; unknown
     * and deleted ids are simply absent.
     */
    List<User> findAllById(Set<UUID> ids);

    /** Active user by canonical email; the OAuth account-resolution lookup. */
    Optional<User> findActiveByEmail(String email);

    /** Inserts a new aggregate; the id is assigned by the domain factory (UUIDv7). */
    void saveNew(User user);

    /**
     * Persists the login stamp. Zero affected rows means the user vanished mid-request and surfaces
     * as {@link com.blocbuy.identity.exception.IdentityErrorCode#USER_NOT_FOUND}.
     */
    void updateLastLogin(User user);
}
