// Payment 도메인에서 결제 완료 후 team 컨텍스트의 팀 생성·석수 변경을 요청하는 포트
package com.codeprint.domain.payment.port;

import java.util.UUID;

public interface TeamProvisioningPort {

    // 결제 완료 후 팀 생성(팀장을 OWNER로 등록) — 생성된 팀 ID 반환
    UUID createTeam(UUID ownerUserId, String teamName, int seats);

    // 결제 완료 후 기존 팀의 총 좌석 수 변경
    void changeSeats(UUID teamId, int newSeats);

    // 좌석 증가 결제 준비 시 필요한 팀 소유자·현재 좌석 수 조회
    TeamSummary getTeamSummary(UUID teamId);

    record TeamSummary(UUID ownerUserId, int seats) {}
}
