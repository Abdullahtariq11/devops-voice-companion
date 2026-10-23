package com.abdullah.devopsvoicecompanion.github;

import com.abdullah.devopsvoicecompanion.github.dtos.GitHubRunsResponse;
import com.abdullah.devopsvoicecompanion.github.dtos.GitHubJobsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Comparator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
   * Downloads the end of a job's UTF-8 log using GitHub's temporary HTTPS redirect.
   * Uses a separate client so the API token is not forwarded to the download host.
   * Uses the log's content length to request its final 64 KiB with an HTTP byte range.
   * Reads at most 64 KiB plus one overflow byte and rejects oversized or incomplete
   * responses rather than presenting the start of a log as its end. A prefix marker
   * counts toward the character limit when earlier log content is omitted.
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

    long contentLength = logDownloadClient.head().uri(location)
        .header("Accept-Encoding", "identity")
        .retrieve().toBodilessEntity().getHeaders().getContentLength();
    int byteLimit = 64 * 1024;
    long start = contentLength > byteLimit ? contentLength - byteLimit : 0;
    var downloadRequest = logDownloadClient.get().uri(location)
        .header("Accept-Encoding", "identity");
    if (start > 0) {
      downloadRequest.header("Range", "bytes=" + start + "-" + (contentLength - 1));
    }

    return downloadRequest.exchange((request, download) -> {
      int status = download.getStatusCode().value();
      if (status != 200 && status != 206) {
        throw new RestClientException("Job logs are unavailable.");
      }
      if (status == 206 && (start == 0 || !("bytes " + start + "-" + (contentLength - 1)
          + "/" + contentLength).equals(download.getHeaders().getFirst("Content-Range")))) {
        throw new RestClientException("Log download returned an unexpected byte range.");
      }
      byte[] bytes = download.getBody().readNBytes(byteLimit + 1);
      if (bytes.length > byteLimit) {
        throw new RestClientException("Log download exceeds the bounded download limit.");
      }
      long expectedLength = status == 206 ? contentLength - start : contentLength;
      if (expectedLength >= 0 && bytes.length != expectedLength) {
        throw new RestClientException("Log download is incomplete.");
      }

      String text = new String(bytes, StandardCharsets.UTF_8);
      if (start == 0 && text.length() <= maxChars) {
        return text;
      }
      String marker = "[Earlier log content omitted]\n";
      String prefix = marker.substring(0, Math.min(marker.length(), maxChars));
      int tailLength = maxChars - prefix.length();
      int tailStart = Math.max(0, text.length() - tailLength);
      if (tailStart < text.length() && Character.isLowSurrogate(text.charAt(tailStart))) {
        tailStart++;
      }
      return prefix + text.substring(tailStart);
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

  /**
   * Reads up to three changed source/test/workflow files at the failed run's immutable head SHA.
   * Only the first 30 files of that single commit are considered, ranked by names in the logs.
   * Fork runs are skipped rather than querying another repository. No model-selected URLs are used.
   * Each patch and source excerpt is limited to 2000 characters; partial access stays explicit.
   *
   * @param owner selected repository owner
   * @param repo selected repository name
   * @param runId positive workflow run ID
   * @param logs bounded failure evidence used to rank changed filenames
   * @return bounded evidence, including coverage and missing-source notices
   */
  public String getRunSourceEvidence(String owner, String repo, long runId, String logs) {
    if (runId < 1) {
      throw new IllegalArgumentException("Run ID must be positive.");
    }
    String base = "/repos/" + owner + "/" + repo;
    JsonNode run = boundedJson(base + "/actions/runs/" + runId);
    String sha = run.path("head_sha").asText("");
    if (!sha.matches("[a-fA-F0-9]{40}")
        || !(owner + "/" + repo).equalsIgnoreCase(run.path("head_repository").path("full_name").asText(""))) {
      return "Source unavailable: run head SHA or same-repository head could not be verified.";
    }
    JsonNode commit = boundedJson(base + "/commits/" + sha + "?per_page=30");
    List<JsonNode> candidates = new ArrayList<>();
    for (JsonNode file : commit.path("files")) {
      String path = file.path("filename").asText("");
      if (safeSourcePath(path)) {
        candidates.add(file);
      }
      if (candidates.size() == 30) {
        break;
      }
    }
    candidates.sort(Comparator.comparingInt(file -> {
      String path = file.path("filename").asText();
      String name = path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.[^.]+$", "");
      return logs.contains(name) ? 0 : 1;
    }));
    StringBuilder evidence = new StringBuilder("Head commit: " + sha
        + "\nCoverage: first 30 changed files of this single commit only, not the entire PR or repository."
        + " Source excerpts may be truncated; unchanged files are not searched.\n");
    for (JsonNode file : candidates.stream().limit(3).toList()) {
      String path = file.path("filename").asText();
      evidence.append("\nFile: ").append(path).append(" at ").append(sha).append('\n');
      evidence.append("Patch excerpt:\n").append(excerpt(file.path("patch").asText("Patch unavailable."))).append('\n');
      if ("removed".equals(file.path("status").asText())) {
        evidence.append("Source unavailable: file deleted in this commit.\n");
        continue;
      }
      try {
        String source = restClient.get()
            .uri(builder -> builder.path(base + "/contents/").pathSegment(path.split("/"))
                .queryParam("ref", sha).build())
            .header("Accept", "application/vnd.github.raw+json")
            .exchange((request, response) -> {
              if (response.getStatusCode().value() != 200) {
                throw new RestClientException("Source unavailable.");
              }
              byte[] bytes = response.getBody().readNBytes(16_385);
              if (bytes.length > 16_384) {
                return "Source omitted: exceeds 16 KiB limit.";
              }
              String text = new String(bytes, StandardCharsets.UTF_8);
              return text.indexOf('\0') >= 0 ? "Binary source omitted." : excerpt(text);
            });
        evidence.append("Source excerpt (from line 1):\n").append(source).append('\n');
      } catch (RestClientException exception) {
        evidence.append("Source unavailable: access denied, missing, or request failed.\n");
      }
    }
    if (candidates.isEmpty()) {
      evidence.append("No eligible changed source/test/workflow files found in this page.\n");
    }
    return evidence.toString();
  }

  /** Reads only the first 100 latest-attempt jobs for a bounded investigation, without pagination. */
  public List<GitHubJob> getInvestigationJobs(String owner, String repo, long runId) {
    if (runId < 1) {
      throw new IllegalArgumentException("Run ID must be positive.");
    }
    var response = JsonMapper.builder().build().treeToValue(boundedJson("/repos/" + owner + "/" + repo
        + "/actions/runs/" + runId + "/jobs?filter=latest&per_page=100&page=1"), GitHubJobsResponse.class);
    return response == null || response.jobs() == null ? List.of() : response.jobs().stream().limit(100).toList();
  }

  private JsonNode boundedJson(String path) {
    return restClient.get().uri(path).exchange((request, response) -> {
      if (response.getStatusCode().value() != 200) {
        throw new RestClientException("GitHub evidence unavailable.");
      }
      byte[] bytes = response.getBody().readNBytes(1_048_577);
      if (bytes.length > 1_048_576) {
        throw new RestClientException("GitHub evidence exceeds the response limit.");
      }
      try {
        return JsonMapper.builder().build().readTree(bytes);
      } catch (tools.jackson.core.JacksonException exception) {
        throw new RestClientException("GitHub returned malformed evidence.");
      }
    });
  }

  private boolean safeSourcePath(String path) {
    return path.length() <= 300 && path.matches("[A-Za-z0-9_./-]+")
        && !List.of(path.split("/")).contains("..")
        && !path.toLowerCase(java.util.Locale.ROOT).matches(".*(?:secret|credential|token|password|\\.env).*")
        && (path.matches(".*\\.(?:java|kt|ts|tsx|js|jsx|py|go|rs|cs)$")
            || path.matches("\\.github/workflows/[A-Za-z0-9_-]+\\.ya?ml"));
  }

  private String excerpt(String text) {
    return text.length() <= 2000 ? text : text.substring(0, 1978) + "\n[Excerpt truncated]";
  }
}
