// GitHub 레포 URL에서 owner(계정명)를 추출·비교하는 순수 유틸 — project/community 여러 컨텍스트가 공유
package com.codeprint.shared;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GithubRepoOwner {

    private static final Pattern OWNER_PATTERN = Pattern.compile("github\\.com[/:]([^/]+)/");

    private GithubRepoOwner() {}

    // URL에서 owner 추출 — 파싱 실패 시 null
    public static String extract(String githubRepoUrl) {
        if (githubRepoUrl == null) return null;
        Matcher m = OWNER_PATTERN.matcher(githubRepoUrl);
        return m.find() ? m.group(1) : null;
    }

    // 레포 owner와 사용자명이 일치하는지 — GitHub 계정명은 대소문자 구분 없음.
    // 1차 구현: 문자열 비교만 — 조직(org) 레포는 본인 소유여도 false로 판정됨(알려진 한계)
    public static boolean matches(String githubRepoUrl, String username) {
        String owner = extract(githubRepoUrl);
        return owner != null && username != null && owner.equalsIgnoreCase(username);
    }
}
