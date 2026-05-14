package com.example.schoolforum.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private String allowedOrigins;

    @Value("${cors.allowed-headers:Content-Type, Authorization, X-Requested-With, Accept, Origin, Token, cache-control}")
    private String allowedHeaders;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Bean
    public FilterRegistrationBean<Filter> corsFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CorsHeaderFilter(allowedOrigins, allowedHeaders, allowCredentials));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("corsHeaderFilter");
        return registration;
    }

    private static class CorsHeaderFilter implements Filter {

        private final String[] origins;
        private final String allowedHeaders;
        private final boolean allowCredentials;

        CorsHeaderFilter(String allowedOrigins, String allowedHeaders, boolean allowCredentials) {
            this.origins = allowedOrigins.split("\\s*,\\s*");
            this.allowedHeaders = allowedHeaders;
            this.allowCredentials = allowCredentials;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;

            String origin = req.getHeader("Origin");
            if (origin != null) {
                for (String allowedOrigin : origins) {
                    if (origin.equals(allowedOrigin)) {
                        res.setHeader("Access-Control-Allow-Origin", origin);
                        if (allowCredentials) {
                            res.setHeader("Access-Control-Allow-Credentials", "true");
                        }
                        break;
                    }
                }
            }

            if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
                res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                res.setHeader("Access-Control-Allow-Headers", allowedHeaders);
                res.setHeader("Access-Control-Max-Age", "3600");
                res.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            chain.doFilter(request, response);
        }

        @Override
        public void init(FilterConfig filterConfig) {
        }

        @Override
        public void destroy() {
        }
    }
}
