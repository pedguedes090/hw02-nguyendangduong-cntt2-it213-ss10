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
