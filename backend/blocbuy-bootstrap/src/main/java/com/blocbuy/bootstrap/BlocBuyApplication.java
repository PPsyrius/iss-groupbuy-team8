package com.blocbuy.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BlocBuy backend entry point.
 *
 * <p>Assembly module only — no business logic here. Scanning is limited to {@code com.blocbuy};
 * test sources and unrelated packages are never scanned.
 */
@SpringBootApplication(scanBasePackages = "com.blocbuy")
public class BlocBuyApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlocBuyApplication.class, args);
    }
}
