package org.example.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    // spring注册bean，调用以下方法并起名productRestClient，在ProductClient中注入使用
    // 注入配置文件中的services.product.base-url的值
    @Bean
    public RestClient productRestClient(RestClient.Builder builder,
                                        @Value("${services.product.base-url}") String productBaseUrl){
        return builder
                .baseUrl(productBaseUrl)
                .build();
    }
}
