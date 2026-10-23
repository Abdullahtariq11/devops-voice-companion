package com.abdullah.devopsvoicecompanion.github;

import com.abdullah.devopsvoicecompanion.bedrock.BedrockSummarizer;
import com.abdullah.devopsvoicecompanion.tools.BuildTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockMakers;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitHubClientTests {
  private MockRestServiceServer api;
  private MockRestServiceServer downloads;
  private GitHubClient client;

  @BeforeEach
  void setUp() {
    var apiBuilder = RestClient.builder().baseUrl("https://api.github.com")
        .defaultHeader("Authorization", "Bearer test-token");
    api = MockRestServiceServer.bindTo(apiBuilder).build();
    var downloadBuilder = RestClient.builder();
    downloads = MockRestServiceServer.bindTo(downloadBuilder).build();
    client = new GitHubClient(apiBuilder.build(), downloadBuilder.build());
  }

  @Test
  void sourceEvidenceUsesImmutableRunShaAndPrioritizesLogReferencedFiles() {
    String sha = "a".repeat(40);
    expectRunHead(sha, "owner/repo");
    api.expect(requestTo("https://api.github.com/repos/owner/repo/commits/" + sha + "?per_page=30"))
        .andRespond(withSuccess("""
            {"files":[
              {"filename":"src/Other.java","patch":"+other"},
              {"filename":"src/PaymentServiceTest.java","patch":"-100\\n+90"},
              {"filename":".env","patch":"secret"},
              {"filename":"src/Credentials.java","patch":"secret"},
              {"filename":"../outside.java","patch":"unsafe"}
            ]}
            """, MediaType.APPLICATION_JSON));
    api.expect(requestTo("https://api.github.com/repos/owner/repo/contents/src/PaymentServiceTest.java?ref=" + sha))
        .andExpect(header("Accept", "application/vnd.github.raw+json"))
        .andRespond(withSuccess("assertEquals(100, total);", MediaType.TEXT_PLAIN));
    api.expect(requestTo("https://api.github.com/repos/owner/repo/contents/src/Other.java?ref=" + sha))
        .andRespond(withForbiddenRequest());

    String result = client.getRunSourceEvidence("owner", "repo", 42, "PaymentServiceTest failed");

    assertTrue(result.contains(sha));
    assertTrue(result.contains("assertEquals(100, total)"));
    assertTrue(result.contains("Source unavailable"));
    assertTrue(result.indexOf("File: src/PaymentServiceTest") < result.indexOf("File: src/Other"));
    assertFalse(result.contains("secret"));
    assertFalse(result.contains("unsafe"));
    api.verify();
    downloads.verify();
  }

  @Test
  void sourceEvidenceDoesNotFollowForksOrUnverifiedHeads() {
    expectRunHead("b".repeat(40), "another/fork");
    assertTrue(client.getRunSourceEvidence("owner", "repo", 42, "logs").contains("could not be verified"));
    api.verify();
  }

  @Test
  void sourceEvidenceCapsFilesAndOmitsLargeSource() {
    String sha = "a".repeat(40);
    expectRunHead(sha, "owner/repo");
    api.expect(requestTo("https://api.github.com/repos/owner/repo/commits/" + sha + "?per_page=30"))
        .andRespond(withSuccess("{\"files\":[{\"filename\":\"One.java\",\"patch\":\"" + "p".repeat(5000)
            + "\"},{\"filename\":\"Two.java\"},{\"filename\":\"Three.java\",\"status\":\"removed\"},"
            + "{\"filename\":\"Four.java\"}]}", MediaType.APPLICATION_JSON));
    api.expect(requestTo("https://api.github.com/repos/owner/repo/contents/One.java?ref=" + sha))
        .andRespond(withSuccess("x".repeat(16385), MediaType.TEXT_PLAIN));
    api.expect(requestTo("https://api.github.com/repos/owner/repo/contents/Two.java?ref=" + sha))
        .andRespond(withSuccess("y".repeat(3000), MediaType.TEXT_PLAIN));

    String result = client.getRunSourceEvidence("owner", "repo", 42, "logs");
    assertTrue(result.contains("exceeds 16 KiB"));
    assertTrue(result.contains("file deleted"));
    assertTrue(result.contains("[Excerpt truncated]"));
    assertFalse(result.contains("File: Four.java"));
    assertFalse(result.contains("p".repeat(2001)));
    assertTrue(result.length() < 14000);
    api.verify();
  }

  @Test
  void rejectsOversizedCommitResponse() {
    String sha = "a".repeat(40);
    expectRunHead(sha, "owner/repo");
    api.expect(requestTo("https://api.github.com/repos/owner/repo/commits/" + sha + "?per_page=30"))
        .andRespond(withSuccess("x".repeat(1048577), MediaType.APPLICATION_JSON));
    assertThrows(RestClientException.class, () -> client.getRunSourceEvidence("owner", "repo", 42, "logs"));
    api.verify();
  }

  @Test
  void investigationJobsDoesNotPaginateAndPreservesFailureMetadata() {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs/42/jobs?filter=latest&per_page=100&page=1"))
        .andRespond(withSuccess("{\"total_count\":200,\"jobs\":[{\"id\":123,\"name\":\"Tests\",\"conclusion\":\"failure\"}]}",
            MediaType.APPLICATION_JSON));
    assertEquals("Tests", client.getInvestigationJobs("owner", "repo", 42).getFirst().name());
    api.verify();
  }

  private void expectRunHead(String sha, String repository) {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs/42"))
        .andRespond(withSuccess("{\"head_sha\":\"" + sha + "\",\"head_repository\":{\"full_name\":\""
            + repository + "\"}}", MediaType.APPLICATION_JSON));
  }

  @Test
  void mapsLatestRunFromGithubJson() {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs?per_page=1"))
        .andRespond(withSuccess("""
            {"total_count":1,"workflow_runs":[{
              "id":42,"status":"completed","conclusion":"failure","head_branch":"feature/test",
              "created_at":"2026-10-01T12:00:00Z","updated_at":"2026-10-01T12:05:00Z",
              "display_title":"Test payments","html_url":"https://github.com/owner/repo/actions/runs/42"
            }]}
            """, MediaType.APPLICATION_JSON));

    GitHubRun run = client.getLatestRun("owner", "repo").orElseThrow();

    assertEquals(42L, run.id());
    assertEquals("completed", run.status());
    assertEquals("failure", run.conclusion());
    assertEquals("feature/test", run.headBranch());
    assertEquals(Instant.parse("2026-10-01T12:00:00Z"), run.createdAt());
    assertEquals(Instant.parse("2026-10-01T12:05:00Z"), run.updatedAt());
    assertEquals("Test payments", run.displayTitle());
    api.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "{\"total_count\":0,\"workflow_runs\":[]}"})
  void handlesEmptyLatestRunResponses(String body) {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs?per_page=1"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertTrue(client.getLatestRun("owner", "repo").isEmpty());
    api.verify();
  }

  @Test
  void requestsFailureFilterAndMapsRunsInResponseOrder() {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs?status=failure&per_page=2"))
        .andRespond(withSuccess("""
            {"total_count":2,"workflow_runs":[
              {"id":43,"status":"completed","conclusion":"failure","head_branch":"main"},
              {"id":42,"status":"completed","conclusion":"failure","head_branch":"dev"}
            ]}
            """, MediaType.APPLICATION_JSON));

    var runs = client.getFailedRuns("owner", "repo", 2);

    assertEquals(java.util.List.of(43L, 42L), runs.stream().map(GitHubRun::id).toList());
    assertEquals("dev", runs.get(1).headBranch());
    api.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "{\"total_count\":0,\"workflow_runs\":[]}", "{\"total_count\":0}"})
  void handlesEmptyFailedRunResponses(String body) {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs?status=failure&per_page=5"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertTrue(client.getFailedRuns("owner", "repo", 5).isEmpty());
    api.verify();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 101})
  void rejectsInvalidFailureLimitsWithoutMakingRequests(int limit) {
    assertThrows(IllegalArgumentException.class, () -> client.getFailedRuns("owner", "repo", limit));
    api.verify();
  }

  @Test
  void fetchesAllJobPagesAndMapsSteps() {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs/42/jobs"
            + "?filter=latest&per_page=100&page=1"))
        .andRespond(withSuccess("""
            {"total_count":2,"jobs":[{"id":123,"name":"Tests","status":"completed",
              "conclusion":"failure","steps":[
                {"name":"Run tests","status":"completed","conclusion":"failure","number":2}
              ]}]}
            """, MediaType.APPLICATION_JSON));
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs/42/jobs"
            + "?filter=latest&per_page=100&page=2"))
        .andRespond(withSuccess("""
            {"total_count":2,"jobs":[{"id":124,"name":"Lint","conclusion":"success","steps":[]}]}
            """, MediaType.APPLICATION_JSON));

    var jobs = client.getJobs("owner", "repo", 42);

    assertEquals(java.util.List.of(123L, 124L), jobs.stream().map(GitHubJob::id).toList());
    assertEquals("Run tests", jobs.getFirst().steps().getFirst().name());
    assertEquals(2, jobs.getFirst().steps().getFirst().number());
    assertEquals("failure", jobs.getFirst().steps().getFirst().conclusion());
    assertThrows(UnsupportedOperationException.class, () -> jobs.clear());
    api.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "{\"total_count\":0}", "{\"total_count\":0,\"jobs\":[]}"})
  void stopsOnEmptyJobPages(String body) {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs/42/jobs"
            + "?filter=latest&per_page=100&page=1"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertTrue(client.getJobs("owner", "repo", 42).isEmpty());
    api.verify();
  }

  @Test
  void rejectsInvalidRunIdWithoutMakingRequests() {
    assertThrows(IllegalArgumentException.class, () -> client.getJobs("owner", "repo", 0));
    api.verify();
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 404, 429, 500})
  void propagatesApiFailuresToToolLayer(int status) {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs?per_page=1"))
        .andRespond(withStatus(HttpStatus.valueOf(status)));

    var failure = assertThrows(RestClientResponseException.class,
        () -> client.getLatestRun("owner", "repo"));

    assertEquals(status, failure.getStatusCode().value());
    api.verify();
  }

  @ParameterizedTest
  @CsvSource({"0,100", "-1,100", "123,0", "123,10001"})
  void rejectsInvalidLogArgumentsWithoutMakingRequests(long jobId, int limit) {
    assertThrows(IllegalArgumentException.class,
        () -> client.getJobLogs("owner", "repo", jobId, limit));
    api.verify();
    downloads.verify();
  }

  @Test
  void downloadsLogsWithoutForwardingCredentials() {
    expectRedirect("https://logs.example.com/job.txt?signature=test");
    expectLogSize("https://logs.example.com/job.txt?signature=test",
        "Test failed: expected 2 but got 1".length());
    downloads.expect(requestTo("https://logs.example.com/job.txt?signature=test"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(headerDoesNotExist("Authorization"))
        .andRespond(withSuccess("Test failed: expected 2 but got 1", MediaType.TEXT_PLAIN));

    assertEquals("Test failed: expected 2 but got 1",
        client.getJobLogs("owner", "repo", 123, 100));
    api.verify();
    downloads.verify();
  }

  @Test
  void truncatesOversizedLogsAndMarksExcerpt() {
    String failure = "PaymentServiceTest failed: 100/90";
    expectRedirect("https://logs.example.com/job.txt");
    expectLogSize("https://logs.example.com/job.txt", 20_000);
    downloads.expect(requestTo("https://logs.example.com/job.txt"))
        .andRespond(withSuccess("x".repeat(20_000 - failure.length()) + failure, MediaType.TEXT_PLAIN));

    String result = client.getJobLogs("owner", "repo", 123, 100);

    assertEquals(100, result.length());
    assertTrue(result.startsWith("[Earlier log content omitted]"));
    assertTrue(result.endsWith(failure));
    api.verify();
    downloads.verify();
  }

  @Test
  void rejectsNonHttpsDownloadLocation() {
    expectRedirect("http://logs.example.com/job.txt");

    assertThrows(RestClientException.class,
        () -> client.getJobLogs("owner", "repo", 123, 100));
    api.verify();
    downloads.verify();
  }

  @Test
  void propagatesMissingLogsWithoutDownloading() {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/jobs/123/logs"))
        .andRespond(withResourceNotFound());

    assertThrows(HttpClientErrorException.NotFound.class,
        () -> client.getJobLogs("owner", "repo", 123, 100));
    api.verify();
    downloads.verify();
  }

  private void expectRedirect(String location) {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/jobs/123/logs"))
        .andExpect(header("Authorization", "Bearer test-token"))
        .andRespond(withStatus(HttpStatus.FOUND).header("Location", location));
  }

  private void expectLogSize(String location, int length) {
    downloads.expect(requestTo(location))
        .andExpect(method(HttpMethod.HEAD))
        .andExpect(headerDoesNotExist("Authorization"))
        .andRespond(withStatus(HttpStatus.OK).header("Content-Length", Integer.toString(length)));
  }

  @Test
  void downloadsOnlyFinalByteRangeForLargeLogs() {
    expectRedirect("https://logs.example.com/job.txt");
    int totalBytes = 2_000_000;
    int tailBytes = 64 * 1024;
    String failure = "Expected payment total: 100. Actual payment total: 90.";
    expectLogSize("https://logs.example.com/job.txt", totalBytes);
    downloads.expect(requestTo("https://logs.example.com/job.txt"))
        .andExpect(header("Range", "bytes=" + (totalBytes - tailBytes) + "-" + (totalBytes - 1)))
        .andExpect(headerDoesNotExist("Authorization"))
        .andRespond(withStatus(HttpStatus.PARTIAL_CONTENT)
            .header("Content-Range", "bytes " + (totalBytes - tailBytes) + "-"
                + (totalBytes - 1) + "/" + totalBytes)
            .body("x".repeat(tailBytes - failure.length()) + failure));

    String result = client.getJobLogs("owner", "repo", 123, 1500);

    assertEquals(1500, result.length());
    assertTrue(result.endsWith(failure));
    api.verify();
    downloads.verify();
  }

  @Test
  void rejectsOversizedResponseWhenHostIgnoresRange() {
    expectRedirect("https://logs.example.com/job.txt");
    expectLogSize("https://logs.example.com/job.txt", 100_000);
    downloads.expect(requestTo("https://logs.example.com/job.txt"))
        .andRespond(withSuccess("x".repeat(100_000), MediaType.TEXT_PLAIN));

    assertThrows(RestClientException.class,
        () -> client.getJobLogs("owner", "repo", 123, 1500));
    api.verify();
    downloads.verify();
  }

  @Test
  void rejectsPartialResponseThatDoesNotReachTheLogEnd() {
    expectRedirect("https://logs.example.com/job.txt");
    expectLogSize("https://logs.example.com/job.txt", 100_000);
    downloads.expect(requestTo("https://logs.example.com/job.txt"))
        .andRespond(withStatus(HttpStatus.PARTIAL_CONTENT)
            .header("Content-Range", "bytes 0-65535/100000")
            .body("x".repeat(65536)));

    assertThrows(RestClientException.class,
        () -> client.getJobLogs("owner", "repo", 123, 1500));
    api.verify();
    downloads.verify();
  }

  @Test
  void failureToolKeepsJobDetailsWhenLogsHaveExpired() {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs/42/jobs"
            + "?filter=latest&per_page=100&page=1"))
        .andRespond(withSuccess("""
            {"total_count":2,"jobs":[
              {"id":122,"name":"Successful job","conclusion":"success","steps":[]},
              {"id":123,"name":"Tests","conclusion":"failure","steps":[
                {"name":"Run tests","conclusion":"failure","number":2}
              ]}
            ]}
            """, MediaType.APPLICATION_JSON));
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/jobs/123/logs"))
        .andRespond(withResourceNotFound());

    String result = new BuildTools(client,
        mock(BedrockSummarizer.class, withSettings().mockMaker(MockMakers.SUBCLASS)),
        mock(com.abdullah.devopsvoicecompanion.bedrock.BedrockFailureInvestigator.class))
        .getFailureLogs("owner", "repo", 42);

    assertTrue(result.contains("Job: Tests (failure)"));
    assertTrue(result.contains("Failed step: Run tests"));
    assertTrue(result.contains("Logs unavailable: GitHub returned HTTP 404."));
    assertFalse(result.contains("Successful job"));
    api.verify();
    downloads.verify();
  }

  @Test
  void failureToolBoundsOutputIncludingJobMetadata() {
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs/42/jobs"
            + "?filter=latest&per_page=100&page=1"))
        .andRespond(withSuccess("{\"total_count\":1,\"jobs\":[{\"id\":123,\"name\":\""
            + "x".repeat(11_000) + "\",\"conclusion\":\"failure\",\"steps\":[]}]}",
            MediaType.APPLICATION_JSON));
    api.expect(requestTo("https://api.github.com/repos/owner/repo/actions/jobs/123/logs"))
        .andRespond(withResourceNotFound());

    String result = new BuildTools(client,
        mock(BedrockSummarizer.class, withSettings().mockMaker(MockMakers.SUBCLASS)),
        mock(com.abdullah.devopsvoicecompanion.bedrock.BedrockFailureInvestigator.class))
        .getFailureLogs("owner", "repo", 42);

    assertTrue(result.length() <= 10_000);
    assertTrue(result.endsWith("[Output truncated]"));
    api.verify();
  }
}
