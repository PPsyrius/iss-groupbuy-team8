package fixture.archunit.web.internal;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Compliant: hidden and active only for blocbuy.topology=distributed. */
@RestController
@Hidden
@ConditionalOnProperty(name = "blocbuy.topology", havingValue = "distributed")
@RequestMapping("/internal/users")
public class DistributedOnlyInternalController {

    @GetMapping("/{id}/active")
    public String active() {
        return "ok";
    }
}
