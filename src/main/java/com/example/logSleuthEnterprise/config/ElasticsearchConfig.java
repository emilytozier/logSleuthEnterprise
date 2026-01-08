package com.example.logSleuthEnterprise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris}")
    private String elasticsearchUris;

    @Override
    public ClientConfiguration clientConfiguration() {
        // Удаляем "http://" если есть
        String hostPort = elasticsearchUris.replace("http://", "").replace("https://", "");
        return ClientConfiguration.builder()
                .connectedTo(hostPort)
                .build();
    }
}
