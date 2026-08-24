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
