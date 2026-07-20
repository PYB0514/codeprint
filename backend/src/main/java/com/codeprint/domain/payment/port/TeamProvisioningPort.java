// Payment 도메인에서 결제 완료 후 team 컨텍스트의 팀 생성·석수 변경을 요청하는 포트
package com.codeprint.domain.payment.port;

import java.util.UUID;

public interface TeamProvisioningPort {

    // 결제 완료 후 팀 생성(팀장을 OWNER로 등록) — 생성된 팀 ID 반환
    UUID createTeam(UUID ownerUserId, String teamName, int seats);

    // 결제 완료 후 좌석 수를 증분만큼 원자적으로 증가(절대치 지정 아님) — 동시 확정 시에도 지불한 증분만큼만 반영되도록 보장
    void increaseSeatsBy(UUID teamId, int deltaSeats);

    // 좌석 증가 결제 준비 시 필요한 팀 소유자·현재 좌석 수 조회
    TeamSummary getTeamSummary(UUID teamId);

    record TeamSummary(UUID ownerUserId, int seats) {}
}
