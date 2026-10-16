package fixture.archunit.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Violation: Spring registers {@code internal/users} as {@code /internal/users}. */
@RestController
@RequestMapping("internal/users")
public class SlashlessClassMappingController {

    @GetMapping("{id}")
    public String user() {
        return "ok";
    }
}
