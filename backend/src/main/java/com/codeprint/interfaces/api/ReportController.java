// 게시글·댓글 신고 제출 및 관리자 조회 API
package com.codeprint.interfaces.api;

import com.codeprint.domain.community.Report;
import com.codeprint.domain.community.ReportRepository;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportRepository reportRepository;

    // 신고 제출 요청 DTO
    record SubmitRequest(
        @NotBlank String targetType,
        @NotBlank String targetId,
        @NotBlank @Size(max = 500) String reason
    ) {}

    // 신고 응답 DTO
    record ReportResponse(UUID id, UUID reporterId, String targetType, UUID targetId, String reason, Instant createdAt, String status) {}

    // 처리 상태 변경 요청 DTO
    record StatusRequest(@NotBlank String status) {}

    // 신고 제출 (로그인 사용자만)
    @PostMapping
    public ResponseEntity<Void> submit(
        @Valid @RequestBody SubmitRequest req,
        @AuthenticationPrincipal User user
    ) {
        if (!"POST".equals(req.targetType()) && !"COMMENT".equals(req.targetType())) {
            return ResponseEntity.badRequest().build();
        }
        UUID targetId;
        try {
            targetId = UUID.fromString(req.targetId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        Report report = Report.create(user.getId(), req.targetType(), targetId, req.reason());
        reportRepository.save(report);
        return ResponseEntity.status(201).build();
    }

    // 관리자 전체 조회 (최신순)
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReportResponse>> listAll() {
        List<ReportResponse> list = reportRepository.findAll().stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .map(r -> new ReportResponse(r.getId(), r.getReporterId(), r.getTargetType(), r.getTargetId(), r.getReason(), r.getCreatedAt(), r.getStatus()))
            .toList();
        return ResponseEntity.ok(list);
    }

    // 관리자 신고 처리 상태 변경 (OPEN↔RESOLVED)
    @PatchMapping("/admin/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        if (!"OPEN".equals(req.status()) && !"RESOLVED".equals(req.status())) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Report> opt = reportRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Report report = opt.get();
        if ("RESOLVED".equals(req.status())) report.resolve();
        else report.reopen();
        reportRepository.save(report);
        return ResponseEntity.noContent().build();
    }
}
