package com.blocbuy.identity.service;

import com.blocbuy.identity.repository.RoleRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Reads one consistent authorization snapshot before opening a browser login session. */
@Service
@RequiredArgsConstructor
public class LoginAuthorizationService {
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 3)
    public Snapshot snapshot(UUID userId) {
        return new Snapshot(
                roleRepository.findRoleCodesByUserId(userId),
                roleRepository.findPermissionCodesByUserId(userId));
    }

    /** Framework-free role and permission values for the authentication adapter. */
    public record Snapshot(List<String> roles, List<String> permissions) {
        public Snapshot {
            roles = List.copyOf(roles);
            permissions = List.copyOf(permissions);
        }
    }
}
