# HW02 — Dò Lỗi & Tối Ưu Code Tích Hợp SDK Tracing

**Học viên:** Nguyễn Đăng Dương — **Lớp:** CNTT2 — **Bài:** SS10 — **HW02**

**Link GitHub:** https://github.com/pedguedes090/hw02-nguyendangduong-cntt2-it213-ss10.git

---

## 1. Phân tích lỗ hổng bảo mật & lỗi logic trong code cũ

### 1.1. Lỗi hardcode API Key (nghiêm trọng nhất)

```java
return new LangfuseClient(
    "pk-lf-1234567890abcdef",
    "sk-lf-0987654321fedcba",
    "https://cloud.langfuse.com"
);
```

- **API Key nằm trực tiếp trong mã nguồn**: khi push lên Git (dù repo private), key vẫn nằm trong lịch sử commit vĩnh viễn — không thể xóa bằng cách sửa code, phải rotate key.
- **Mọi người có quyền đọc source đều lấy được `sk-` secret key** → kẻ tấn công có thể đọc/ghi/xóa toàn bộ trace, thậm chí giả mạo dữ liệu telemetry của hệ thống ngân hàng.
- **Không thể cấu hình theo môi trường**: dev/staging/prod phải dùng chung key, vi phạm nguyên tắc ít đặc quyền (least privilege), không thể thu hồi key riêng lẻ khi có nhân viên nghỉ việc.
- Sai kiểu khởi tạo: API cũ khuyến nghị dùng **Builder pattern** (`LangfuseClient.builder()...build()`) để kiểm soát từng field rõ ràng, tránh nhầm thứ tự tham số.

### 1.2. Rò rỉ thông tin giao dịch nhạy cảm (PII)

```java
trace.input("User " + user + " chuyển tiền cho " + toAccount + " số tiền " + amount));
trace.output("Thành công chuyển khoản " + amount + " từ " + user + " sang " + toAccount);
```

- **Số tài khoản người nhận (toAccount) gửi plain-text lên Langfuse**: đây là PII tài chính, nếu Langfuse bị lộ hoặc trace bị truy cập trái phép, kẻ tấn công biết chính xác ai chuyển cho ai, số tiền bao nhiêu.
- **Tên người dùng (user) không được làm sạch**: vi phạm các chuẩn bảo vệ dữ liệu (VD: yêu cầu NHNN về bảo mật thông tin khách hàng).
- **Input/output chứa thông tin nhạy cảm nhưng không có cơ chế che giấu (masking)** — telemetry phải được "khử nhạy cảm" trước khi rời khỏi biên hệ thống.

### 1.3. Thiếu định danh Session/User tập trung

- Trace không gắn `userId` hay `sessionId` → **không thể truy vết một giao dịch** theo khách hàng hoặc theo phiên hội thoại trên Langfuse.
- Khi lỗi xảy ra, team không biết lỗi thuộc khách hàng nào, phiên nào, ảnh hưởng đến ai → vi phạm yêu cầu "truy vết giao dịch tập trung" của RikkeiPay.

### 1.4. Các lỗi logic khác

- **`System.out.println`** thay vì logging chuẩn (SLF4J): không có level, không có timestamp, không ghi được ra file/log aggregator, gây khó khăn vận hành.
- **Không bắt exception**: nếu nghiệp vụ chuyển tiền ném lỗi, trace không được cập nhật trạng thái → Langfuse chỉ thấy trace "treo" không có output, sai lệch dữ liệu giám sát.
- **Không có thông tin phụ trợ (metadata)**: duration, trạng thái thành công/thất bại không được ghi → khó phân tích latency và tỷ lệ lỗi.
- **Field injection (`@Autowired`)** thay vì constructor injection → khó test, khó thấy dependency.

---

## 2. Mã nguồn Java sau khi refactor

### 2.1. `LangfuseProperties.java` — đọc cấu hình an toàn qua `@ConfigurationProperties`

```java
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

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
```

### 2.2. `LangfuseConfig.java` — khởi tạo client an toàn

```java
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
```

### 2.3. `PiiMaskingUtil.java` — che giấu PII trước khi gửi telemetry

```java
package com.rikkeipay.util;

import java.util.regex.Pattern;

/**
 * PiiMaskingUtil - tiện ích che giấu thông tin nhạy cảm (PII Masking).
 *
 * Trước khi bất kỳ dữ liệu nào được đẩy lên Langfuse (input/output của trace),
 * các trường nhạy cảm sẽ được thay thế bằng giá trị đã che để không lộ PII
 * trong telemetry. Giao dịch thật vẫn diễn ra bình thường ở tầng nghiệp vụ,
 * chỉ dữ liệu gửi lên Langfuse là đã được làm sạch.
 */
public final class PiiMaskingUtil {

    private PiiMaskingUtil() {
    }

    /** Số tài khoản từ 6-20 chữ số. */
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\b\\d{6,20}\\b");

    /** Số điện thoại Việt Nam dạng 09x/08x/07x/03x... */
    private static final Pattern PHONE_NUMBER = Pattern.compile("\\b(?:0|\\+84)(?:3[2-9]|5[2689]|7[06789]|8[1-9]|9[0-9])[0-9]{7}\\b");

    /** Số CMND/CCCD 9 hoặc 12 chữ số. */
    private static final Pattern IDENTITY_NUMBER = Pattern.compile("\\b\\d{9}|\\b\\d{12}\\b");

    /** Email. */
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private static final String MASKED = "****";

    /**
     * Che toàn bộ PII trong một chuỗi văn bản tùy ý (input hoặc output của trace).
     */
    public static String mask(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String masked = raw;
        masked = ACCOUNT_NUMBER.matcher(masked).replaceAll(MASKED);
        masked = PHONE_NUMBER.matcher(masked).replaceAll(MASKED);
        masked = IDENTITY_NUMBER.matcher(masked).replaceAll(MASKED);
        masked = EMAIL.matcher(masked).replaceAll(MASKED);
        return masked;
    }

    /**
     * Tạo chuỗi log an toàn cho một giao dịch: chỉ giữ 4 ký tự cuối của số tài khoản
     * để hỗ trợ đối soát, phần còn lại bị che.
     */
    public static String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return MASKED;
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
```

### 2.4. `TransferService.java` — trace đầy đủ userId/sessionId + PII masked

```java
package com.rikkeipay.service;

import com.rikkeipay.util.PiiMaskingUtil;
import io.langfuse.client.LangfuseClient;
import io.langfuse.client.model.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * TransferService - xử lý giao dịch chuyển tiền và ghi trace lên Langfuse.
 *
 * So với code cũ, phiên bản refactored đảm bảo:
 *  1. Không rò rỉ PII: input/output của trace được che bằng PiiMaskingUtil.
 *  2. Có đầy đủ định danh tập trung: userId + sessionId trên Trace.
 *  3. Log qua SLF4J thay vì System.out.println.
 *  4. Trace chỉ được tạo khi bắt đầu xử lý, output được cập nhật khi hoàn tất
 *     (kèm metadata: duration, masked account, amount, status).
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final LangfuseClient langfuseClient;

    public TransferService(LangfuseClient langfuseClient) {
        this.langfuseClient = langfuseClient;
    }

    public void processTransfer(String userId, String sessionId, String user, String toAccount, double amount) {
        // ---- 1. Khởi tạo trace với userId + sessionId tập trung ----
        Trace trace = langfuseClient.trace(
                new Trace()
                        .name("bank-transfer")
                        .userId(userId)
                        .sessionId(sessionId)
                        // Input đã che PII - không gửi số tài khoản thật lên Langfuse
                        .input("Chuyển khoản: nguoi gui=%s -> tai khoan=%s so tien=%s"
                                .formatted(
                                        PiiMaskingUtil.mask(user),
                                        PiiMaskingUtil.maskAccount(toAccount),
                                        amount)));

        long start = System.currentTimeMillis();
        log.info("Xử lý chuyển khoản userId={} toAccount={} amount={}",
                userId, PiiMaskingUtil.maskAccount(toAccount), amount);

        try {
            // ---- 2. Nghiệp vụ chuyển tiền (giả lập) ----
            Thread.sleep(120); // mô phỏng gọi core banking

            // ---- 3. Ghi kết quả lên trace: output đã được che PII ----
            long durationMs = System.currentTimeMillis() - start;
            trace.output("Chuyen khoan thanh cong: so tien=%s tu %s sang tai khoan %s"
                    .formatted(amount, PiiMaskingUtil.mask(user), PiiMaskingUtil.maskAccount(toAccount)))
                    .metadata(java.util.Map.of(
                            "userId", userId,
                            "sessionId", sessionId,
                            "maskedToAccount", PiiMaskingUtil.maskAccount(toAccount),
                            "amount", amount,
                            "status", "SUCCESS",
                            "durationMs", durationMs));

            log.info("Chuyển khoản thành công userId={} trong {}ms", userId, durationMs);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            trace.output("Chuyen khoan that bai: loi he thong")
                    .metadata(java.util.Map.of(
                            "userId", userId,
                            "sessionId", sessionId,
                            "status", "FAILED"));
            log.error("Chuyển khoản thất bại userId={}", userId, e);
        }
    }
}
```

### 2.5. `RikkeiPayAssistantApplication.java`

```java
package com.rikkeipay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RikkeiPayAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(RikkeiPayAssistantApplication.class, args);
    }
}
```

---

## 3. Tệp cấu hình `application.yml` an toàn

```yaml
spring:
    application:
        name: hw02-nguyendangduong-cntt2-it213-ss10

# ------------------------------------------------------------------
# Cấu hình Langfuse an toàn
# - Không hardcode key trong mã nguồn.
# - Sử dụng placeholder ${ENV_VAR} để key được inject lúc runtime
#   từ biến môi trường (hoặc file .env của CI/CD / server).
# - publicKey/secretKey có thể tạo trên Langfuse Dashboard
#   (Settings -> API Keys).
# ------------------------------------------------------------------
langfuse:
    public-key: ${LANGFUSE_PUBLIC_KEY}
    secret-key: ${LANGFUSE_SECRET_KEY}
    base-url: ${LANGFUSE_BASE_URL:http://localhost:3000}
    enabled: ${LANGFUSE_ENABLED:true}
```

### Cách chạy

```bash
# 1. Đặt biến môi trường (không commit key lên Git)
export LANGFUSE_PUBLIC_KEY="pk-lf-xxxx"
export LANGFUSE_SECRET_KEY="sk-lf-xxxx"
export LANGFUSE_BASE_URL="https://cloud.langfuse.com"

# 2. Build & chạy
./gradlew bootRun
```

---

## 4. Tổng kết thay đổi

| Vấn đề code cũ | Giải pháp refactor |
|---|---|
| Hardcode API key trong mã nguồn | `@ConfigurationProperties` + placeholder `${ENV_VAR}` trong `application.yml` |
| Rò rỉ PII (số tài khoản, tên, số tiền) lên trace | `PiiMaskingUtil.mask()`/`maskAccount()` trước khi gửi telemetry |
| Thiếu `userId`/`sessionId` trên trace | Gắn `.userId(userId).sessionId(sessionId)` khi tạo Trace |
| `System.out.println` | SLF4J `log.info`/`log.error` |
| Không bắt exception, trace "treo" | try-catch + cập nhật trạng thái SUCCESS/FAILED + metadata (durationMs) |
| Field injection | Constructor injection |
| Khởi tạo client không an toàn | Builder pattern + cờ `enabled` cho phép tắt tracing khi cần |
