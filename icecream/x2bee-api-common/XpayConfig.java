package com.x2bee.api.common.base.config;

import lgdacom.XPayClient.XPayClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XpayConfig {

    @Bean
    public XPayClient xPayClient() {
        return new XPayClient();
    }
}
