package com.blocbuy.identity.web.internal;

import com.blocbuy.common.constant.Topology;
import com.blocbuy.common.web.Result;
import com.blocbuy.identity.service.UserAppService;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identity answers for peer services (backend ADR 0008, #468): {@code commerce-svc} reaches it
 * through its remote {@code UserAppService} over ECS Service Connect. Exists only in the
 * distributed topology and is hidden from the public contract; the ALB never routes {@code
 * /internal/**} and only peer services reach it. Errors come from the global handler as everywhere
 * else, so the caller maps them by the identity error code rather than by HTTP status alone.
 */
@Hidden
@Validated
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = Topology.PROPERTY, havingValue = Topology.DISTRIBUTED)
@RequestMapping(InternalUserController.PATH)
public class InternalUserController {

    /** Base path; shared with the remote adapter in {@code commerce-svc}. */
    public static final String PATH = "/internal/users";

    /** Relative path of the account-status check. */
    public static final String ACTIVE = "/{id}/active";

    /** Relative path of the public nickname lookup. */
    public static final String PUBLIC_NICKNAME = "/{id}/public-nickname";

    private final UserAppService users;

    /**
     * 200 when the account is active; 404 {@code IDENTITY_404_001} when it does not exist; 403
     * {@code IDENTITY_403_001} when it is disabled.
     */
    @GetMapping(ACTIVE)
    public Result<Void> requireActive(@PathVariable UUID id) {
        users.requireActive(id);
        return Result.ok();
    }

    /** Always 200; the nickname is null when the user is unknown, inactive or has none to show. */
    @GetMapping(PUBLIC_NICKNAME)
    public Result<PublicNicknameView> publicNickname(@PathVariable UUID id) {
        return Result.ok(new PublicNicknameView(users.publicNickname(id).orElse(null)));
    }

    /**
     * Wire shape of the public nickname answer, shared with the remote adapter.
     *
     * @param nickname display nickname, null when there is none to show
     */
    public record PublicNicknameView(String nickname) {}
}
