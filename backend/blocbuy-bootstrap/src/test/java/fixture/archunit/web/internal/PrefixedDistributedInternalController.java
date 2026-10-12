package fixture.archunit.web.internal;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Compliant: the same condition written with {@code prefix}. */
@RestController
@Hidden
@ConditionalOnProperty(prefix = "blocbuy", name = "topology", havingValue = "distributed")
@RequestMapping("/internal/nicknames")
public class PrefixedDistributedInternalController {

    @GetMapping
    public String nicknames() {
        return "ok";
    }
}
