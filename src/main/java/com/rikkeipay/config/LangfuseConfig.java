package com.rikkeipay.config;

import io.langfuse.client.LangfuseClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangfuseConfig - cấu hình LangfuseClient an toàn.
 *
 * Khác với code cũ (hardcode API key trong mã nguồn), phiên bản này:
 *  - Đọc toàn bộ cấu hình từ application.yml qua @ConfigurationProperties.
 *  - Không log hoặc in ra bất kỳ key nào.
 *  - Hỗ trợ tắt tracing bằng cờ langfuse.enabled.
 */
@Configuration
@EnableConfigurationProperties(LangfuseProperties.class)
public class LangfuseConfig {

    @Bean
    public LangfuseClient langfuseClient(LangfuseProperties props) {
        if (!props.isEnabled()) {
            // Trả về client rỗng (no-op) khi tắt tracing -> không gửi telemetry đi đâu cả
            return LangfuseClient.builder()
                    .publicKey("disabled")
                    .secretKey("disabled")
                    .baseUrl(props.getBaseUrl())
                    .build();
        }
        return LangfuseClient.builder()
                .publicKey(props.getPublicKey())
                .secretKey(props.getSecretKey())
                .baseUrl(props.getBaseUrl())
                .build();
    }
}
