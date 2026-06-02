// GitHub REST API를 호출하는 인프라 클라이언트
package com.codeprint.infrastructure.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GitHubApiClient {

    private static final Pattern REPO_PATTERN = Pattern.compile("github\\.com[/:]([^/]+)/([^/.]+?)(\\.git)?$");
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // GitHub 레포 URL에서 브랜치 목록을 조회 (최대 100개)
    public List<String> fetchBranches(String githubRepoUrl) {
        String ownerRepo = extractOwnerRepo(githubRepoUrl);
        String apiUrl = "https://api.github.com/repos/" + ownerRepo + "/branches?per_page=100";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

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

    // "https://github.com/owner/repo" -> "owner/repo"
    private String extractOwnerRepo(String githubRepoUrl) {
        Matcher m = REPO_PATTERN.matcher(githubRepoUrl);
        if (!m.find()) {
            throw new IllegalArgumentException("GitHub URL 파싱 실패: " + githubRepoUrl);
        }
        return m.group(1) + "/" + m.group(2);
    }
}
