-- 프로젝트별 게이트 등급(0/1/2단계) 설정 — 1단계(architecture)는 기본 켜짐, 2단계(experimental)는 기본 꺼짐
ALTER TABLE projects
    ADD COLUMN gate_architecture_enabled BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN gate_experimental_enabled BOOLEAN NOT NULL DEFAULT false;
