package fixture.archunit.web.internal;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Violation: matchIfMissing=true also activates it in the default monolith. */
@RestController
@Hidden
@ConditionalOnProperty(
        name = "blocbuy.topology",
        havingValue = "distributed",
        matchIfMissing = true)
@RequestMapping("/internal/users")
public class MatchIfMissingInternalController {

    @GetMapping("/{id}/active")
    public String active() {
        return "ok";
    }
}
