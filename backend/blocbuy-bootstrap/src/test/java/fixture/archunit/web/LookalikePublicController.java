package fixture.archunit.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compliant: {@code /internals} and a nested {@code internal} segment are not {@code /internal}.
 */
@RestController
@RequestMapping("internals")
public class LookalikePublicController {

    @GetMapping("{id}/internal")
    public String item() {
        return "ok";
    }
}
