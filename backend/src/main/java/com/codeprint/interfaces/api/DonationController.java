// 후원 결제 확인 및 후원자 목록 조회 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.donation.DonationApplicationService;
import com.codeprint.domain.donation.Donation;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/donations")
@RequiredArgsConstructor
public class DonationController {

    private final DonationApplicationService donationApplicationService;

    // 토스 결제 승인 요청 처리
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirm(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ConfirmRequest request) {
        donationApplicationService.confirm(user.getId(), user.getUsername(), request.paymentKey(), request.orderId(), request.amount());
        return ResponseEntity.ok(Map.of("message", "후원 감사합니다!"));
    }

    // 전체 후원자 목록 조회 (공개)
    @GetMapping
    public ResponseEntity<List<DonationResponse>> list() {
        List<DonationResponse> result = donationApplicationService.findAll().stream()
            .map(DonationResponse::from)
            .toList();
        return ResponseEntity.ok(result);
    }

    public record ConfirmRequest(
        @NotBlank String paymentKey,
        @NotBlank String orderId,
        @Min(1000) @Max(1000000) long amount
    ) {}

    public record DonationResponse(UUID id, String username, long amount, Instant createdAt) {
        static DonationResponse from(Donation d) {
            return new DonationResponse(d.getId(), d.getUsername(), d.getAmount(), d.getCreatedAt());
        }
    }
}
