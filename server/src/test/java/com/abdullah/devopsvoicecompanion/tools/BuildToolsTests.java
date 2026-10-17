package com.abdullah.devopsvoicecompanion.tools;

import com.abdullah.devopsvoicecompanion.bedrock.BedrockSummarizer;
import com.abdullah.devopsvoicecompanion.github.GitHubClient;
import com.abdullah.devopsvoicecompanion.github.GitHubJob;
import com.abdullah.devopsvoicecompanion.github.GitHubStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.web.client.RestClientException;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Verifies summary orchestration without GitHub or Bedrock network calls. */
class BuildToolsTests {
  private GitHubClient github;
  private BedrockSummarizer summarizer;
  private BuildTools tools;

  @BeforeEach
  void setUp() {
    github = mock(GitHubClient.class, withSettings().mockMaker(MockMakers.SUBCLASS));
    summarizer = mock(BedrockSummarizer.class, withSettings().mockMaker(MockMakers.SUBCLASS));
    tools = new BuildTools(github, summarizer);
  }

  @Test
  void sendsFailureEvidenceToBedrock() {
    givenFailedJob();
    when(github.getJobLogs("owner", "repo", 123, 1500)).thenReturn("Expected 100, actual 90.");
    when(summarizer.summarize(eq("owner/repo"), anyString())).thenReturn("The tests failed.");

    assertEquals("The tests failed.", tools.summarizeFailure("owner", "repo", 42));

    verify(summarizer).summarize(eq("owner/repo"), argThat(details ->
        details.contains("Failed step: Run tests") && details.contains("Expected 100, actual 90.")));
  }

  @Test
  void bypassesBedrockForInvalidRunId() {
    assertEquals("Please provide a positive workflow run ID.",
        tools.summarizeFailure("owner", "repo", 0));

    verifyNoInteractions(github, summarizer);
  }

  @Test
  void bypassesBedrockWhenNoJobsFailed() {
    when(github.getJobs("owner", "repo", 42)).thenReturn(List.of(
        new GitHubJob(123L, "Tests", "completed", "success", List.of())));

    assertTrue(tools.summarizeFailure("owner", "repo", 42).startsWith("No failed jobs found"));

    verifyNoInteractions(summarizer);
  }

  @Test
  void bypassesBedrockWhenGitHubFails() {
    when(github.getJobs("owner", "repo", 42)).thenThrow(new RestClientException("Unavailable"));

    assertTrue(tools.summarizeFailure("owner", "repo", 42).contains("couldn't retrieve failed jobs"));

    verifyNoInteractions(summarizer);
  }

  @Test
  void preservesMissingLogContextForBedrock() {
    givenFailedJob();
    when(github.getJobLogs("owner", "repo", 123, 1500))
        .thenThrow(new RestClientException("Expired"));
    when(summarizer.summarize(eq("owner/repo"), anyString()))
        .thenReturn("Tests failed, but the logs are unavailable.");

    assertEquals("Tests failed, but the logs are unavailable.",
        tools.summarizeFailure("owner", "repo", 42));

    verify(summarizer).summarize(eq("owner/repo"), argThat(details ->
        details.contains("Failed step: Run tests") && details.contains("Logs unavailable")));
  }

  @Test
  void handlesBedrockFailureWithoutExposingExceptionDetails() {
    givenFailedJob();
    when(summarizer.summarize(eq("owner/repo"), anyString()))
        .thenThrow(SdkClientException.create("Sensitive diagnostic details"));

    String result = tools.summarizeFailure("owner", "repo", 42);

    assertTrue(result.contains("couldn't generate a summary"));
    assertFalse(result.contains("Sensitive diagnostic details"));
  }

  @Test
  void handlesEmptyModelResponse() {
    givenFailedJob();
    when(summarizer.summarize(eq("owner/repo"), anyString())).thenReturn("  ");

    assertEquals("The failure summary was empty. Please try again.",
        tools.summarizeFailure("owner", "repo", 42));
  }

  private void givenFailedJob() {
    when(github.getJobs("owner", "repo", 42)).thenReturn(List.of(
        new GitHubJob(123L, "Tests", "completed", "failure", List.of(
            new GitHubStep("Run tests", "completed", "failure", 2)))));
  }
}
