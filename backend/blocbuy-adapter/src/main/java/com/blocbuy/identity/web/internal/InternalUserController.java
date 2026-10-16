package com.blocbuy.identity.web.internal;

import com.blocbuy.common.constant.Topology;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.web.Result;
import com.blocbuy.identity.service.UserAppService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identity answers for peer services (backend ADR 0008, #468, #469): {@code commerce-svc} reaches
 * it through its remote {@code UserAppService} over ECS Service Connect. Exists only in the
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

    /** Relative path of the batch public nickname lookup (#469). */
    public static final String PUBLIC_NICKNAMES = "/public-nicknames";

    /**
     * Most ids one batch lookup accepts: the largest admin page. The remote adapter splits larger
     * sets by this same constant.
     */
    public static final int MAX_BATCH = PageQuery.MAX_SIZE;

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
     * Always 200 for a valid request; ids that are unknown, inactive or have no nickname to show
     * are absent from the map. 400 when {@code userIds} is missing, empty, larger than {@link
     * #MAX_BATCH} or holds a null.
     */
    @PostMapping(PUBLIC_NICKNAMES)
    public Result<PublicNicknamesView> publicNicknames(
            @RequestBody @Valid PublicNicknamesQuery query) {
        return Result.ok(new PublicNicknamesView(users.publicNicknames(query.userIds())));
    }

    /**
     * Wire shape of the public nickname answer, shared with the remote adapter.
     *
     * @param nickname display nickname, null when there is none to show
     */
    public record PublicNicknameView(String nickname) {}

    /**
     * Wire shape of the batch nickname request, shared with the remote adapter.
     *
     * @param userIds 1 to {@link #MAX_BATCH} user ids
     */
    public record PublicNicknamesQuery(
            @NotEmpty @Size(max = MAX_BATCH) Set<@NotNull UUID> userIds) {

        /** Defensive copy that keeps a null element, so validation answers 400 rather than NPE. */
        public PublicNicknamesQuery {
            userIds =
                    userIds == null
                            ? null
                            : Collections.unmodifiableSet(new LinkedHashSet<>(userIds));
        }
    }

    /**
     * Wire shape of the batch nickname answer, shared with the remote adapter.
     *
     * @param nicknames showable nickname by user id; ids without one are absent
     */
    public record PublicNicknamesView(Map<UUID, String> nicknames) {

        /** Immutable copy; a missing map stays null so the remote adapter can reject it. */
        public PublicNicknamesView {
            nicknames = nicknames == null ? null : Map.copyOf(nicknames);
        }
    }
}
