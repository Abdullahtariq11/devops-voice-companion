package com.abdullah.devopsvoicecompanion.github;

import com.abdullah.devopsvoicecompanion.github.dtos.GitHubRunsResponse;
import com.abdullah.devopsvoicecompanion.github.dtos.GitHubJobsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads GitHub Actions runs, jobs, and bounded log excerpts.
 * HTTP failures propagate to callers so MCP tools can provide friendly responses.
 */
@Component
public class GitHubClient {
  private final RestClient restClient;
  private final RestClient logDownloadClient;

  /**
   * Creates separate API and log-download clients with redirects disabled.
   * Only API requests receive the optional {@code GITHUB_TOKEN} environment variable.
   * Connections time out after three seconds; requests use a five-second read timeout.
   */
  @Autowired
  public GitHubClient() {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(3))
            .build());
    requestFactory.setReadTimeout(Duration.ofSeconds(5));
    RestClient.Builder builder =
        RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl("https://api.github.com")
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28");

    String token = System.getenv("GITHUB_TOKEN");

    if (token != null && !token.isBlank()) {
      builder.defaultHeader("Authorization", "Bearer " + token);
    }
    this.restClient = builder.build();
    this.logDownloadClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  /**
   * Supplies clients for isolated HTTP tests.
   *
   * @param restClient GitHub API client
   * @param logDownloadClient download client without GitHub authorization headers
   */
  GitHubClient(RestClient restClient, RestClient logDownloadClient) {
    this.restClient = restClient;
    this.logDownloadClient = logDownloadClient;
  }

  /**
   * Downloads the beginning of a job's UTF-8 log using GitHub's temporary HTTPS redirect.
   * Uses a separate client so the API token is not forwarded to the download host.
   * Reads at most {@code maxChars + 1} decoded characters and includes a truncation marker
   * within the requested limit when more text is available.
   *
   * @param owner GitHub account or organization
   * @param repo repository name without the owner
   * @param jobId positive workflow job ID
   * @param maxChars maximum returned characters, from 1 to 10000
   * @return bounded log excerpt, possibly empty
   * @throws IllegalArgumentException if the job ID or character limit is invalid
   * @throws RestClientException if the request fails or the download URL is invalid
   */
  public String getJobLogs(String owner, String repo, long jobId, int maxChars) {
    if (jobId < 1 || maxChars < 1 || maxChars > 10_000) {
      throw new IllegalArgumentException("A positive job ID and a log limit from 1 to 10000 are required.");
    }

    var response = restClient.get()
        .uri("/repos/{owner}/{repo}/actions/jobs/{jobId}/logs", owner, repo, jobId)
        .retrieve()
        .toBodilessEntity();
    URI location = response.getHeaders().getLocation();
    if (response.getStatusCode().value() != 302 || location == null
        || !"https".equalsIgnoreCase(location.getScheme()) || location.getHost() == null
        || location.getUserInfo() != null) {
      throw new RestClientException("GitHub did not provide a valid log download URL.");
    }

    return logDownloadClient.get().uri(location).exchange((request, download) -> {
      if (!download.getStatusCode().is2xxSuccessful()) {
        throw new RestClientException("Job logs are unavailable.");
      }
      try (var reader = new InputStreamReader(download.getBody(), StandardCharsets.UTF_8)) {
        char[] excerpt = new char[maxChars + 1];
        int count = 0;
        int read;
        while (count < excerpt.length
            && (read = reader.read(excerpt, count, excerpt.length - count)) != -1) {
          count += read;
        }
        String text = new String(excerpt, 0, Math.min(count, maxChars));
        if (count > maxChars) {
          String marker = "\n[Log truncated]";
          return text.substring(0, Math.max(0, maxChars - marker.length()))
              + marker.substring(0, Math.min(marker.length(), maxChars));
        }
        return text;
      }
    });
  }

  /**
   * Fetches the latest workflow run across the repository's workflows and branches.
   *
   * @param owner GitHub account or organization
   * @param repo repository name without the owner
   * @return latest run, or empty when no runs are returned
   * @throws RestClientException if GitHub cannot be reached or rejects the request
   */
  public Optional<GitHubRun> getLatestRun(String owner, String repo) {
    GitHubRunsResponse response =
        restClient
            .get()
            .uri("/repos/{owner}/{repo}/actions/runs?per_page=1", owner, repo)
            .retrieve()
            .body(GitHubRunsResponse.class);

    if (response == null || response.workflowRuns().isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(response.workflowRuns().getFirst().toGitHubRun());
  }

  /**
   * Fetches all pages of jobs for a workflow run's latest attempt, including successful jobs.
   * Failure filtering is left to callers.
   *
   * @param owner GitHub account or organization
   * @param repo repository name without the owner
   * @param runId positive workflow run ID
   * @return immutable job list, empty when no jobs are returned
   * @throws IllegalArgumentException if the run ID is not positive
   * @throws RestClientException if a GitHub request fails
   */
  public List<GitHubJob> getJobs(String owner, String repo, long runId) {
    if (runId < 1) {
      throw new IllegalArgumentException("Run ID must be positive.");
    }

    List<GitHubJob> jobs = new ArrayList<>();
    int page = 1;
    while (true) {
      GitHubJobsResponse response = restClient.get()
          .uri("/repos/{owner}/{repo}/actions/runs/{runId}/jobs?filter=latest&per_page=100&page={page}",
              owner, repo, runId, page)
          .retrieve()
          .body(GitHubJobsResponse.class);

      if (response == null || response.jobs() == null || response.jobs().isEmpty()) {
        break;
      }

      jobs.addAll(response.jobs());
      if (jobs.size() >= response.totalCount()) {
        break;
      }
      page++;
    }

    return List.copyOf(jobs);
  }

  /**
   * Fetches one page of recent runs whose conclusion is {@code failure}.
   *
   * @param owner GitHub account or organization
   * @param repo repository name without the owner
   * @param limit maximum runs to request, from 1 to 100
   * @return failed runs, empty when none are returned
   * @throws IllegalArgumentException if the limit is outside the supported range
   * @throws RestClientException if a GitHub request fails
   */
  public List<GitHubRun> getFailedRuns(String owner, String repo, int limit) {
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("Limit must be between 1 and 100");
    }

    GitHubRunsResponse response =
        restClient
            .get()
            .uri(
                "/repos/{owner}/{repo}/actions/runs?status=failure&per_page={limit}",
                owner,
                repo,
                limit)
            .retrieve()
            .body(GitHubRunsResponse.class);

    if (response == null || response.workflowRuns() == null) {
      return List.of();
    }

    return response.workflowRuns().stream().map(GitHubWorkflowRun::toGitHubRun).toList();
  }
}
