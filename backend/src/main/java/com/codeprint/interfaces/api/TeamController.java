// 팀 플랜 관리 REST API 컨트롤러
package com.codeprint.interfaces.api;

import com.codeprint.application.team.TeamApplicationService;
import com.codeprint.domain.team.Team;
import com.codeprint.domain.team.TeamMember;
import com.codeprint.domain.team.TeamProjectAllocation;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamApplicationService teamService;

    // 팀 삭제
    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> deleteTeam(
            @PathVariable UUID teamId,
            @AuthenticationPrincipal User user) {
        teamService.deleteTeam(teamId, user.getId());
        return ResponseEntity.noContent().build();
    }

    // 내 팀 목록 조회
    @GetMapping("/mine")
    public ResponseEntity<List<TeamResponse>> getMyTeams(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(teamService.getMyTeams(user.getId()).stream()
                .map(this::toResponse).toList());
    }

    // 팀 멤버 목록
    @GetMapping("/{teamId}/members")
    public ResponseEntity<List<MemberResponse>> getMembers(
            @PathVariable UUID teamId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(teamService.getMembers(teamId).stream()
                .map(m -> new MemberResponse(m.getId(), m.getUserId(), m.getRole().name(), m.getJoinedAt()))
                .toList());
    }

    // 멤버 추가
    @PostMapping("/{teamId}/members")
    public ResponseEntity<MemberResponse> addMember(
            @PathVariable UUID teamId,
            @Valid @RequestBody AddMemberRequest req,
            @AuthenticationPrincipal User user) {
        TeamMember m = teamService.addMember(teamId, user.getId(), req.userId());
        return ResponseEntity.status(201).body(
                new MemberResponse(m.getId(), m.getUserId(), m.getRole().name(), m.getJoinedAt()));
    }

    // 멤버 제거
    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID teamId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal User user) {
        teamService.removeMember(teamId, user.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    // 석수 배분 현황 조회
    @GetMapping("/{teamId}/allocations")
    public ResponseEntity<List<AllocationResponse>> getAllocations(
            @PathVariable UUID teamId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(teamService.getAllocations(teamId).stream()
                .map(a -> new AllocationResponse(a.getProjectId(), a.getAllocatedSeats()))
                .toList());
    }

    // 프로젝트 석수 배분 설정
    @PutMapping("/{teamId}/allocations/{projectId}")
    public ResponseEntity<Map<String, Object>> allocateSeats(
            @PathVariable UUID teamId,
            @PathVariable UUID projectId,
            @Valid @RequestBody AllocateSeatsRequest req,
            @AuthenticationPrincipal User user) {
        teamService.allocateSeats(teamId, user.getId(), projectId, req.seats());
        int remaining = teamService.getMyTeams(user.getId()).stream()
                .filter(t -> t.getId().equals(teamId)).findFirst()
                .map(t -> t.getTotalSeats() - teamService.getAllocations(teamId).stream()
                        .mapToInt(TeamProjectAllocation::getAllocatedSeats).sum())
                .orElse(0);
        return ResponseEntity.ok(Map.of("allocatedSeats", req.seats(), "remainingSeats", remaining));
    }

    // 좌석 수 감소 (증가는 /api/teams/{teamId}/seats/payment/prepare 경유)
    @PutMapping("/{teamId}/plan")
    public ResponseEntity<TeamResponse> decreaseSeats(
            @PathVariable UUID teamId,
            @Valid @RequestBody DecreaseSeatsRequest req,
            @AuthenticationPrincipal User user) {
        Team team = teamService.decreaseSeats(teamId, user.getId(), req.seats());
        return ResponseEntity.ok(toResponse(team));
    }

    private TeamResponse toResponse(Team t) {
        int used = teamService.getAllocations(t.getId()).stream()
                .mapToInt(TeamProjectAllocation::getAllocatedSeats).sum();
        return new TeamResponse(t.getId(), t.getName(), t.getPlan().name(),
                t.getTotalSeats(), used, t.getCreatedAt());
    }

    record AddMemberRequest(@NotNull UUID userId) {}
    record AllocateSeatsRequest(@Min(0) int seats) {}
    record DecreaseSeatsRequest(@Min(1) int seats) {}

    record TeamResponse(UUID id, String name, String plan, int totalSeats, int usedSeats, Instant createdAt) {}
    record MemberResponse(UUID id, UUID userId, String role, Instant joinedAt) {}
    record AllocationResponse(UUID projectId, int allocatedSeats) {}
}
