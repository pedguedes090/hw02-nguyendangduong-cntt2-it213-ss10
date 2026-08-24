package com.rikkeipay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LangfuseProperties - đọc cấu hình Langfuse từ application.yml thông qua @ConfigurationProperties.
 *
 * Tất cả giá trị nhạy cảm (api-key, secret-key) được đưa ra khỏi mã nguồn
 * và khai báo trong application.yml (hỗ trợ placeholder ${ENV_VAR}).
 */
@ConfigurationProperties(prefix = "langfuse")
public class LangfuseProperties {

    /** Public key dùng để xác thực khi gửi telemetry lên Langfuse. */
    private String publicKey;

    /** Secret key dùng để xác thực khi đọc/ghi dữ liệu từ Langfuse API. */
    private String secretKey;

    /** Base URL của Langfuse server (self-host hoặc cloud). */
    private String baseUrl;

    /** Bật/tắt tracing (false khi dev muốn giảm nhiễu). */
    private boolean enabled = true;

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
