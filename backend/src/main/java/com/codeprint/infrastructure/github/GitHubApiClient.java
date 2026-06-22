// GitHub REST API를 호출하는 인프라 클라이언트
package com.codeprint.infrastructure.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GitHubApiClient {

    private static final Pattern REPO_PATTERN = Pattern.compile("github\\.com[/:]([^/]+)/([^/.]+?)(\\.git)?$");
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // GitHub 레포 URL에서 브랜치 목록을 조회 (최대 100개)
    public List<String> fetchBranches(String githubRepoUrl, String githubAccessToken) {
        String ownerRepo = extractOwnerRepo(githubRepoUrl);
        String apiUrl = "https://api.github.com/repos/" + ownerRepo + "/branches?per_page=100";

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28");
            if (githubAccessToken != null && !githubAccessToken.isBlank()) {
                builder.header("Authorization", "Bearer " + githubAccessToken);
            }
            HttpRequest request = builder.GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            List<String> branches = new ArrayList<>();
            JsonNode nodes = objectMapper.readTree(response.body());
            if (nodes.isArray()) {
                for (JsonNode node : nodes) {
                    branches.add(node.get("name").asText());
                }
            }
            return branches;
        } catch (Exception e) {
            throw new RuntimeException("GitHub 브랜치 조회 실패: " + ownerRepo, e);
        }
    }

    // 특정 브랜치의 최신 커밋 SHA를 조회
    public String fetchLatestCommitSha(String githubRepoUrl, String branch, String githubAccessToken) {
        String ownerRepo = extractOwnerRepo(githubRepoUrl);
        String apiUrl = "https://api.github.com/repos/" + ownerRepo + "/commits/" + branch;

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28");
            if (githubAccessToken != null && !githubAccessToken.isBlank()) {
                builder.header("Authorization", "Bearer " + githubAccessToken);
            }
            HttpRequest request = builder.GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("GitHub API " + response.statusCode() + " — " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode shaNode = root.get("sha");
            if (shaNode == null) {
                throw new RuntimeException("GitHub API 응답에 sha 필드 없음: " + response.body());
            }
            return shaNode.asText();
        } catch (Exception e) {
            throw new RuntimeException("GitHub 커밋 SHA 조회 실패: " + ownerRepo + " / " + branch, e);
        }
    }

    // PR 번호로 head 브랜치명(소스 브랜치)을 조회 — PR 리뷰 분석 대상
    public String fetchPullRequestHeadBranch(String githubRepoUrl, int prNumber, String githubAccessToken) {
        String ownerRepo = extractOwnerRepo(githubRepoUrl);
        String apiUrl = "https://api.github.com/repos/" + ownerRepo + "/pulls/" + prNumber;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28");
            if (githubAccessToken != null && !githubAccessToken.isBlank()) {
                builder.header("Authorization", "Bearer " + githubAccessToken);
            }
            HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("GitHub API " + response.statusCode() + " — " + response.body());
            }
            JsonNode head = objectMapper.readTree(response.body()).get("head");
            if (head == null || head.get("ref") == null) {
                throw new RuntimeException("GitHub PR 응답에 head.ref 없음: " + response.body());
            }
            return head.get("ref").asText();
        } catch (Exception e) {
            throw new RuntimeException("GitHub PR 조회 실패: " + ownerRepo + " #" + prNumber, e);
        }
    }

    // PR이 변경한 파일 경로 목록을 조회 — 페이지네이션(per_page=100)으로 전부 수집. filename은 레포 루트 상대경로(슬래시)
    public java.util.Set<String> fetchPullRequestChangedFiles(String githubRepoUrl, int prNumber, String githubAccessToken) {
        String ownerRepo = extractOwnerRepo(githubRepoUrl);
        java.util.Set<String> files = new java.util.LinkedHashSet<>();
        try {
            // 최대 20페이지(2000파일) 안전 상한 — 그 이상이면 사실상 전체 리팩토링 PR이라 diff-scope 의미 희박
            for (int page = 1; page <= 20; page++) {
                String apiUrl = "https://api.github.com/repos/" + ownerRepo + "/pulls/" + prNumber
                        + "/files?per_page=100&page=" + page;
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28");
                if (githubAccessToken != null && !githubAccessToken.isBlank()) {
                    builder.header("Authorization", "Bearer " + githubAccessToken);
                }
                HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new RuntimeException("GitHub API " + response.statusCode() + " — " + response.body());
                }
                JsonNode arr = objectMapper.readTree(response.body());
                if (!arr.isArray() || arr.isEmpty()) break;
                for (JsonNode node : arr) {
                    JsonNode fn = node.get("filename");
                    if (fn != null) files.add(fn.asText());
                }
                if (arr.size() < 100) break;
            }
            return files;
        } catch (Exception e) {
            throw new RuntimeException("GitHub PR 변경 파일 조회 실패: " + ownerRepo + " #" + prNumber, e);
        }
    }

    // PR(이슈)에 코멘트를 작성하고 생성된 코멘트의 html_url을 반환
    public String postIssueComment(String githubRepoUrl, int prNumber, String body, String githubAccessToken) {
        String ownerRepo = extractOwnerRepo(githubRepoUrl);
        String apiUrl = "https://api.github.com/repos/" + ownerRepo + "/issues/" + prNumber + "/comments";
        try {
            String payload = objectMapper.writeValueAsString(java.util.Map.of("body", body));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("Authorization", "Bearer " + githubAccessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                throw new RuntimeException("GitHub 코멘트 작성 실패 " + response.statusCode() + " — " + response.body());
            }
            JsonNode urlNode = objectMapper.readTree(response.body()).get("html_url");
            return urlNode != null ? urlNode.asText() : null;
        } catch (Exception e) {
            throw new RuntimeException("GitHub PR 코멘트 작성 실패: " + ownerRepo + " #" + prNumber, e);
        }
    }

    // 마커가 포함된 기존 봇 코멘트가 있으면 갱신, 없으면 새로 작성 — 커밋 push마다 봇 코멘트가 누적되는 것 방지
    public String upsertIssueComment(String githubRepoUrl, int prNumber, String body, String marker, String githubAccessToken) {
        Long existingId = findCommentIdByMarker(githubRepoUrl, prNumber, marker, githubAccessToken);
        if (existingId != null) {
            return updateIssueComment(githubRepoUrl, existingId, body, githubAccessToken);
        }
        return postIssueComment(githubRepoUrl, prNumber, body, githubAccessToken);
    }

    // 마커가 포함된 첫 코멘트의 id 조회 — 없거나 조회 실패면 null(새 작성으로 폴백, 리뷰를 깨뜨리지 않음)
    private Long findCommentIdByMarker(String githubRepoUrl, int prNumber, String marker, String githubAccessToken) {
        String ownerRepo = extractOwnerRepo(githubRepoUrl);
        try {
            for (int page = 1; page <= 10; page++) {
                String apiUrl = "https://api.github.com/repos/" + ownerRepo + "/issues/" + prNumber
                        + "/comments?per_page=100&page=" + page;
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28");
                if (githubAccessToken != null && !githubAccessToken.isBlank()) {
                    builder.header("Authorization", "Bearer " + githubAccessToken);
                }
                HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new RuntimeException("GitHub API " + response.statusCode() + " — " + response.body());
                }
                JsonNode arr = objectMapper.readTree(response.body());
                Long id = matchMarkerCommentId(arr, marker);
                if (id != null) return id;
                if (!arr.isArray() || arr.size() < 100) break;
            }
            return null;
        } catch (Exception e) {
            log.warn("기존 PR 코멘트 조회 실패 — 새 코멘트 작성으로 폴백: {} #{}", ownerRepo, prNumber, e);
            return null;
        }
    }

    // 코멘트 배열 JSON에서 body에 마커를 포함한 첫 코멘트의 id — 순수 함수(단위 테스트 대상)
    static Long matchMarkerCommentId(JsonNode commentsArray, String marker) {
        if (commentsArray == null || !commentsArray.isArray()) return null;
        for (JsonNode c : commentsArray) {
            JsonNode bodyNode = c.get("body");
            JsonNode idNode = c.get("id");
            if (bodyNode != null && idNode != null && bodyNode.asText().contains(marker)) {
                return idNode.asLong();
            }
        }
        return null;
    }

    // 기존 코멘트를 PATCH로 갱신하고 html_url 반환
    private String updateIssueComment(String githubRepoUrl, long commentId, String body, String githubAccessToken) {
        String ownerRepo = extractOwnerRepo(githubRepoUrl);
        String apiUrl = "https://api.github.com/repos/" + ownerRepo + "/issues/comments/" + commentId;
        try {
            String payload = objectMapper.writeValueAsString(java.util.Map.of("body", body));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("Authorization", "Bearer " + githubAccessToken)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("GitHub 코멘트 갱신 실패 " + response.statusCode() + " — " + response.body());
            }
            JsonNode urlNode = objectMapper.readTree(response.body()).get("html_url");
            return urlNode != null ? urlNode.asText() : null;
        } catch (Exception e) {
            throw new RuntimeException("GitHub PR 코멘트 갱신 실패: " + ownerRepo + " 코멘트 #" + commentId, e);
        }
    }

    // 인증된 사용자의 GitHub 레포 목록 조회 (최대 100개, 최근 업데이트 순)
    public List<GitHubRepoDto> fetchUserRepos(String githubAccessToken) {
        String apiUrl = "https://api.github.com/user/repos?per_page=100&sort=updated&type=all";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + githubAccessToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<GitHubRepoDto> repos = new ArrayList<>();
            JsonNode nodes = objectMapper.readTree(response.body());
            if (nodes.isArray()) {
                for (JsonNode node : nodes) {
                    String desc = (node.has("description") && !node.get("description").isNull())
                            ? node.get("description").asText() : null;
                    repos.add(new GitHubRepoDto(
                            node.get("name").asText(),
                            node.get("full_name").asText(),
                            node.get("html_url").asText(),
                            desc,
                            node.get("private").asBoolean()
                    ));
                }
            }
            return repos;
        } catch (Exception e) {
            throw new RuntimeException("GitHub 레포 목록 조회 실패", e);
        }
    }

    // GitHub URL에서 owner/repo 경로를 추출
    private String extractOwnerRepo(String githubRepoUrl) {
        Matcher m = REPO_PATTERN.matcher(githubRepoUrl);
        if (!m.find()) {
            throw new IllegalArgumentException("GitHub URL 파싱 실패: " + githubRepoUrl);
        }
        return m.group(1) + "/" + m.group(2);
    }
}
