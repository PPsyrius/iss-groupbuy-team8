package com.blocbuy.bootstrap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocbuy.catalog.model.ListingDetails;
import com.blocbuy.common.web.GlobalExceptionHandler;
import com.blocbuy.common.web.Result;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Domain-to-HTTP contract integration. The probe deliberately bypasses Bean Validation, which
 * otherwise rejects these listing inputs before the domain invariant can run on the admin API.
 */
class CatalogFieldErrorsIT {
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new ListingProbe())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();

    @Test
    void listingDomainRejectionUsesBeanValidationArrayShapeAndSafeMessage() throws Exception {
        mvc.perform(
                        post("/test/catalog/listing")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"originalPrice\":\"12.345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_400_002"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("listing.originalPrice"))
                .andExpect(
                        jsonPath("$.errors[0].message")
                                .value("Must have at most two decimal places"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @RestController
    static class ListingProbe {
        @PostMapping("/test/catalog/listing")
        Result<Void> create(@RequestBody PriceInput input) {
            new ListingDetails(
                    "Tea",
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    new BigDecimal(input.originalPrice()),
                    "sgd",
                    5);
            return Result.ok();
        }
    }

    record PriceInput(String originalPrice) {}
}
