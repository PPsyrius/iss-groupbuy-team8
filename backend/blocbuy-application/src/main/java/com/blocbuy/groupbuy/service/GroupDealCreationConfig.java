package com.blocbuy.groupbuy.service;

import com.blocbuy.groupbuy.model.PaidGroupDealCreator;
import com.blocbuy.groupbuy.model.StandardPaidGroupDealCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the existing Factory Method at the application composition boundary. */
@Configuration(proxyBeanMethods = false)
public class GroupDealCreationConfig {
    @Bean
    public PaidGroupDealCreator paidGroupDealCreator() {
        return new StandardPaidGroupDealCreator();
    }
}
