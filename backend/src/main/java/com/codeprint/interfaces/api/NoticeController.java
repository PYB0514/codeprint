// 공지사항 API — 공개 조회 및 어드민 관리
package com.codeprint.interfaces.api;

import com.codeprint.domain.notice.Notice;
import com.codeprint.domain.notice.NoticeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeRepository noticeRepository;

    // 활성 공지사항 목록 조회 (비로그인 포함 전체 공개)
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getActiveNotices() {
        List<Map<String, Object>> result = noticeRepository.findAllActive()
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    // 전체 공지사항 목록 조회 (어드민 전용)
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllNotices() {
        List<Map<String, Object>> result = noticeRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    // 공지사항 생성 (어드민 전용)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createNotice(@Valid @RequestBody CreateNoticeRequest req) {
        Notice notice = Notice.create(req.title(), req.content());
        Notice saved = noticeRepository.save(notice);
        return ResponseEntity.ok(toResponse(saved));
    }

    // 공지사항 활성화 (어드민 전용)
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> activateNotice(@PathVariable UUID id) {
        return noticeRepository.findById(id)
                .map(notice -> {
                    notice.activate();
                    noticeRepository.save(notice);
                    return ResponseEntity.ok(Map.of("message", "공지사항이 활성화되었습니다."));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 공지사항 비활성화 (어드민 전용)
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deactivateNotice(@PathVariable UUID id) {
        return noticeRepository.findById(id)
                .map(notice -> {
                    notice.deactivate();
                    noticeRepository.save(notice);
                    return ResponseEntity.ok(Map.of("message", "공지사항이 비활성화되었습니다."));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 공지사항 삭제 (어드민 전용)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteNotice(@PathVariable UUID id) {
        noticeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Notice 엔티티를 응답 Map으로 변환
    private Map<String, Object> toResponse(Notice notice) {
        return Map.of(
                "id", notice.getId().toString(),
                "title", notice.getTitle(),
                "content", notice.getContent(),
                "active", notice.isActive(),
                "createdAt", notice.getCreatedAt().toString()
        );
    }

    record CreateNoticeRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String content
    ) {}
}
