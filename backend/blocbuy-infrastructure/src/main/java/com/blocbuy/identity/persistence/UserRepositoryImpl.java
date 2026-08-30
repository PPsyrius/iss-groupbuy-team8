package com.blocbuy.identity.persistence;

import com.blocbuy.identity.model.User;
import com.blocbuy.identity.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Repository implementation for User. Placeholder until the UserMapper + DO land with the first
 * identity migration. Batch reads via {@code findByIds}; queries inside loops are forbidden.
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

  @Override
  public Optional<User> findById(String id) {
    // TODO(TEAM8-identity): select via UserMapper once the table exists
    return Optional.empty();
  }
}
