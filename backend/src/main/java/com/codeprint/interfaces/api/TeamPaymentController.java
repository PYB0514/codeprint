// 팀 생성·좌석 증가 토스페이먼츠 결제 준비·승인 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.payment.TeamPaymentApplicationService;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamPaymentController {

    private final TeamPaymentApplicationService teamPaymentApplicationService;

    @Value("${toss.client-key:}")
    private String clientKey;

    // 신규 팀 생성 결제 주문 생성
    @PostMapping("/payment/prepare")
    public ResponseEntity<Map<String, Object>> prepareNewTeam(
            @Valid @RequestBody PrepareNewTeamRequest req,
            @AuthenticationPrincipal User user) {
        var result = teamPaymentApplicationService.prepareNewTeam(user.getId(), req.teamName(), req.seats());
        return ResponseEntity.ok(toPrepareResponse(result, "Codeprint Team: " + req.teamName(), user));
    }

    // 기존 팀 좌석 증가 결제 주문 생성
    @PostMapping("/{teamId}/seats/payment/prepare")
    public ResponseEntity<Map<String, Object>> prepareSeatIncrease(
            @PathVariable UUID teamId,
            @Valid @RequestBody PrepareSeatIncreaseRequest req,
            @AuthenticationPrincipal User user) {
        var result = teamPaymentApplicationService.prepareSeatIncrease(user.getId(), teamId, req.newSeats());
        return ResponseEntity.ok(toPrepareResponse(result, "Codeprint Team 좌석 증가", user));
    }

    // 팀 결제 승인 — 신규 생성/좌석 증가 공용(주문에 저장된 teamId 유무로 서버가 분기)
    @PostMapping("/payment/confirm")
    public ResponseEntity<Map<String, Object>> confirm(
            @AuthenticationPrincipal User user,
            @RequestBody ConfirmRequest req) {
        var outcome = teamPaymentApplicationService.confirm(user.getId(), req.paymentKey(), req.orderId(), req.amount());

        return switch (outcome.result()) {
            case ALREADY_CONFIRMED -> ResponseEntity.ok(Map.of("result", "already_confirmed"));
            case FORBIDDEN -> ResponseEntity.status(403).body(Map.of("error", "접근 권한 없음"));
            case AMOUNT_MISMATCH -> ResponseEntity.badRequest().body(Map.of("error", "결제 금액 불일치"));
            case OK -> ResponseEntity.ok(Map.of("result", "ok", "teamId", outcome.teamId().toString()));
        };
    }

    private Map<String, Object> toPrepareResponse(TeamPaymentApplicationService.PrepareResult result, String orderName, User user) {
        return Map.of(
            "orderId", result.orderId(),
            "amount", result.amount(),
            "orderName", orderName,
            "customerName", user.getUsername(),
            "customerKey", user.getId().toString(),
            "clientKey", clientKey
        );
    }

    record PrepareNewTeamRequest(@NotBlank String teamName, @Min(1) int seats) {}
    record PrepareSeatIncreaseRequest(@Min(1) int newSeats) {}
    record ConfirmRequest(String paymentKey, String orderId, long amount) {}
}
