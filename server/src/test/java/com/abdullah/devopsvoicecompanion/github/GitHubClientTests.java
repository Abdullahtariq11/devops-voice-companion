package com.abdullah.devopsvoicecompanion.github;

import com.abdullah.devopsvoicecompanion.bedrock.BedrockSummarizer;
import com.abdullah.devopsvoicecompanion.tools.BuildTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
  void downloadsLogsWithoutForwardingCredentials() {
    expectRedirect("https://logs.example.com/job.txt?signature=test");
    downloads.expect(requestTo("https://logs.example.com/job.txt?signature=test"))
        .andExpect(headerDoesNotExist("Authorization"))
        .andRespond(withSuccess("Test failed: expected 2 but got 1", MediaType.TEXT_PLAIN));

    assertEquals("Test failed: expected 2 but got 1",
        client.getJobLogs("owner", "repo", 123, 100));
    api.verify();
    downloads.verify();
  }

  @Test
  void truncatesOversizedLogsAndMarksExcerpt() {
    expectRedirect("https://logs.example.com/job.txt");
    downloads.expect(requestTo("https://logs.example.com/job.txt"))
        .andRespond(withSuccess("x".repeat(20_000), MediaType.TEXT_PLAIN));

    String result = client.getJobLogs("owner", "repo", 123, 100);

    assertEquals(100, result.length());
    assertTrue(result.endsWith("[Log truncated]"));
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
        mock(BedrockSummarizer.class, withSettings().mockMaker(MockMakers.SUBCLASS)))
        .getFailureLogs("owner", "repo", 42);

    assertTrue(result.contains("Job: Tests (failure)"));
    assertTrue(result.contains("Failed step: Run tests"));
    assertTrue(result.contains("Logs unavailable"));
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
        mock(BedrockSummarizer.class, withSettings().mockMaker(MockMakers.SUBCLASS)))
        .getFailureLogs("owner", "repo", 42);

    assertTrue(result.length() <= 10_000);
    assertTrue(result.endsWith("[Output truncated]"));
    api.verify();
  }
}
