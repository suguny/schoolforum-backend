package com.example.schoolforum.config;

import com.manticoresearch.client.ApiClient;
import com.manticoresearch.client.api.IndexApi;
import com.manticoresearch.client.api.SearchApi;
import com.manticoresearch.client.api.UtilsApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ManticoreConfig {

    @Value("${manticore.host:http://127.0.0.1}")
    private String host;

    @Value("${manticore.port:9308}")
    private int port;

    @Bean
    public ApiClient manticoreApiClient() {
        ApiClient apiClient = com.manticoresearch.client.Configuration.getDefaultApiClient();
        apiClient.setBasePath(host + ":" + port);
        log.info("Manticore Search client initialized: {}:{}", host, port);
        return apiClient;
    }

    @Bean
    public IndexApi indexApi(ApiClient apiClient) {
        return new IndexApi(apiClient);
    }

    @Bean
    public SearchApi searchApi(ApiClient apiClient) {
        return new SearchApi(apiClient);
    }

    @Bean
    public UtilsApi utilsApi(ApiClient apiClient) {
        return new UtilsApi(apiClient);
    }
}
