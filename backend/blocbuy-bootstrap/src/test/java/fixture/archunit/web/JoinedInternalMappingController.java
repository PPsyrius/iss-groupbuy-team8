package fixture.archunit.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Violation: {@code /} and {@code internal/batch} combine to {@code /internal/batch}. */
@RestController
@RequestMapping("/")
public class JoinedInternalMappingController {

    @GetMapping("internal/batch")
    public String batch() {
        return "ok";
    }
}
