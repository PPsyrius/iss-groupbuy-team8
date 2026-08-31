package com.blocbuy.identity.repository;

import com.blocbuy.identity.model.User;
import java.util.Optional;

/**
 * Repository port for the User aggregate (one repository per aggregate root). Implemented in the
 * infrastructure layer; DOs and MyBatis mappers never cross this boundary.
 * TODO(TEAM8-identity): add intent-named methods (e.g. findById, saveIfVersion) per use case.
 */
public interface UserRepository {

  Optional<User> findById(String id);
}
