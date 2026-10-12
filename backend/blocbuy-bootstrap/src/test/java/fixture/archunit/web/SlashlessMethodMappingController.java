package fixture.archunit.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Violation: a method mapping without a class mapping or a leading slash. */
@RestController
public class SlashlessMethodMappingController {

    @GetMapping("internal")
    public String internal() {
        return "ok";
    }
}
