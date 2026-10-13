package com.blocbuy.service.commerce.identity;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The {@code UserAppService} of commerce-svc: identity answers over HTTP (backend ADR 0008, #468).
 * Imported only by {@code CommerceServiceApplication}; the monolith and identity-svc keep the local
 * {@code UserAppServiceImpl}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityClientProperties.class)
public class RemoteIdentityConfiguration {

    @Bean
    RemoteUserAppService remoteUserAppService(
            RestClient.Builder builder, IdentityClientProperties properties) {
        return new RemoteUserAppService(identityClient(builder, properties));
    }

    /**
     * The context's builder keeps the platform's {@code X-Trace-Id} propagation; this adds the base
     * URL and the two timeouts. HTTP/1.1 because the Service Connect proxy speaks it and the JDK
     * client would otherwise try an h2c upgrade on every new connection.
     */
    static RestClient identityClient(
            RestClient.Builder builder, IdentityClientProperties properties) {
        return builder.baseUrl(properties.baseUrl().toString())
                .requestFactory(
                        ClientHttpRequestFactoryBuilder.jdk()
                                .withHttpClientCustomizer(
                                        client -> client.version(HttpClient.Version.HTTP_1_1))
                                .build(
                                        HttpClientSettings.defaults()
                                                .withTimeouts(
                                                        properties.connectTimeout(),
                                                        properties.readTimeout())))
                .build();
    }
}
