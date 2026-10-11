package com.abdullah.devopsvoicecompanion.github;

import com.abdullah.devopsvoicecompanion.github.dtos.GitHubRunsResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class GitHubClient {
    private final RestClient restClient;

    public GitHubClient() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");

        String token = System.getenv("GITHUB_TOKEN");

        if(token != null && !token.isBlank()){
            builder.defaultHeader("Authorization", "Bearer " + token);
        }
        this.restClient = builder.build();
    }

    public Optional<GitHubRun> getLatestRun(String owner, String repo){
        GitHubRunsResponse response = restClient.get()
                .uri("/repos/{owner}/{repo}/actions/runs?per_page=1", owner, repo)
                .retrieve()
                .body(GitHubRunsResponse.class);

        if(response == null || response.workflowRuns().isEmpty()){
            return Optional.empty();
        }

        return Optional.of(response.workflowRuns().getFirst().toGitHubRun());
    }

}
