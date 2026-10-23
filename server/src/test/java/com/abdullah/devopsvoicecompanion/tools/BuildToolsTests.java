package com.abdullah.devopsvoicecompanion.tools;

import com.abdullah.devopsvoicecompanion.bedrock.BedrockSummarizer;
import com.abdullah.devopsvoicecompanion.github.GitHubClient;
import com.abdullah.devopsvoicecompanion.github.GitHubJob;
import com.abdullah.devopsvoicecompanion.github.GitHubStep;
import com.abdullah.devopsvoicecompanion.github.GitHubRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.mockito.MockMakers;
import org.springframework.web.client.RestClientException;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Verifies summary orchestration without GitHub or Bedrock network calls. */
class BuildToolsTests {
  private GitHubClient github;
  private BedrockSummarizer summarizer;
  private BuildTools tools;
  private com.abdullah.devopsvoicecompanion.bedrock.BedrockFailureInvestigator investigator;

  @BeforeEach
  void setUp() {
    github = mock(GitHubClient.class, withSettings().mockMaker(MockMakers.SUBCLASS));
    summarizer = mock(BedrockSummarizer.class, withSettings().mockMaker(MockMakers.SUBCLASS));
    investigator = mock(com.abdullah.devopsvoicecompanion.bedrock.BedrockFailureInvestigator.class);
    tools = new BuildTools(github, summarizer, investigator);
  }

  @Test
  void investigatesWithLongerLogsAndPinnedSourceEvidence() {
    when(github.getInvestigationJobs("owner", "repo", 42)).thenReturn(List.of(
        new GitHubJob(123L, "Tests", "completed", "failure", List.of()),
        new GitHubJob(456L, "Other", "completed", "failure", List.of())));
    when(github.getJobLogs("owner", "repo", 123, 6000)).thenReturn("Expected 100, actual 90");
    when(github.getRunSourceEvidence(eq("owner"), eq("repo"), eq(42L), anyString()))
        .thenReturn("File: PaymentService.java at immutable SHA");
    when(investigator.investigate(anyString())).thenReturn("Observed mismatch. Cause not confirmed.");

    String result = tools.investigateFailure("owner", "repo", 42);

    assertTrue(result.contains("Cause not confirmed"));
    assertTrue(result.contains("first 100"));
    verify(investigator).investigate(argThat(text -> text.contains("Expected 100, actual 90")
        && text.contains("PaymentService.java") && text.length() <= 30000));
    verify(github, never()).getJobLogs(eq("owner"), eq("repo"), eq(456L), anyInt());
    verifyNoInteractions(summarizer);
  }

  @Test
  void investigatesPartialEvidenceWhenSourceAccessFails() {
    when(github.getInvestigationJobs("owner", "repo", 42)).thenReturn(List.of(
        new GitHubJob(null, "Tests", "completed", "failure", List.of())));
    when(github.getRunSourceEvidence(anyString(), anyString(), anyLong(), anyString()))
        .thenThrow(new RestClientException("private upstream error"));
    when(investigator.investigate(anyString())).thenReturn("Cause undetermined.");
    assertTrue(tools.investigateFailure("owner", "repo", 42).contains("Cause undetermined"));
    verify(investigator).investigate(argThat(text -> text.contains("Contents read permission")
        && text.contains("missing job ID") && !text.contains("private upstream error")));
  }

  @Test
  void emptyOrInvalidInvestigationBypassesAwsAndSource() {
    assertTrue(tools.investigateFailure("owner", "repo", -1).contains("positive"));
    assertTrue(tools.investigateFailure("owner", "repo", 42).contains("No failed jobs"));
    assertTrue(tools.investigateFailure("../owner", "repo", 42).contains("valid GitHub"));
    verifyNoInteractions(investigator);
    verify(github, never()).getRunSourceEvidence(anyString(), anyString(), anyLong(), anyString());
  }

  @Test
  void investigationSanitizesUnsafeModelReports() {
    when(github.getInvestigationJobs("owner", "repo", 42)).thenReturn(List.of(
        new GitHubJob(null, "Tests", "completed", "failure", List.of())));
    when(github.getRunSourceEvidence(anyString(), anyString(), anyLong(), anyString())).thenReturn("Source unavailable.");
    when(investigator.investigate(anyString())).thenThrow(new IllegalStateException("secret model text"));
    assertEquals("I collected failure evidence, but couldn't produce a reliable investigation. "
        + "Try again or ask for the failure logs.", tools.investigateFailure("owner", "repo", 42));
  }

  @ParameterizedTest
  @CsvSource(value = {
      "completed,success,passed", "completed,failure,failed", "completed,cancelled,was cancelled",
      "completed,timed_out,timed out", "completed,NULL,finished without a pass or fail result",
      "completed,neutral,finished without a pass or fail result", "in_progress,NULL,is running",
      "queued,NULL,is queued", "waiting,NULL,is waiting to start", "pending,NULL,is waiting to start",
      "requested,NULL,is waiting to start", "unknown,NULL,has not finished yet",
      "NULL,NULL,has not finished yet"
  }, nullValues = "NULL")
  void formatsBuildStatesAsSpokenText(String status, String conclusion, String phrase) {
    when(github.getLatestRun("owner", "repo")).thenReturn(Optional.of(
        new GitHubRun(42L, status, conclusion, "main", null, null, "Build")));

    assertEquals("The latest build on owner/repo " + phrase + " on branch main.",
        tools.getBuildStatus("owner", "repo"));
  }

  @ParameterizedTest
  @CsvSource({
      "-3600,just now", "20,just now", "90,1 minute ago", "150,2 minutes ago",
      "5400,1 hour ago", "9000,2 hours ago", "129600,1 day ago", "216000,2 days ago"
  })
  void formatsRelativeTimeWithoutLeakingAbsoluteTimestamps(long ageSeconds, String phrase) {
    Instant updated = Instant.now().minusSeconds(ageSeconds);
    when(github.getLatestRun("owner", "repo")).thenReturn(Optional.of(
        new GitHubRun(42L, "completed", "success", "main", Instant.EPOCH, updated, "Build")));

    assertEquals("The latest build on owner/repo passed " + phrase + " on branch main.",
        tools.getBuildStatus("owner", "repo"));
  }

  @Test
  void activeBuildUsesCreationTimeInsteadOfUpdateTime() {
    when(github.getLatestRun("owner", "repo")).thenReturn(Optional.of(
        new GitHubRun(42L, "in_progress", null, "main", Instant.now().minusSeconds(150),
            Instant.now(), "Build")));

    assertEquals("The latest build on owner/repo is running; created 2 minutes ago on branch main.",
        tools.getBuildStatus("owner", "repo"));
  }

  @Test
  void reportsRepositoryWithNoRuns() {
    when(github.getLatestRun("owner", "repo")).thenReturn(Optional.empty());

    assertEquals("No builds found for owner/repo.", tools.getBuildStatus("owner", "repo"));
  }

  @ParameterizedTest
  @CsvSource({"401,authentication failed", "403,denied access", "404,couldn't find",
      "429,rate limit", "500,temporarily unavailable"})
  void convertsGithubHttpErrorsToFriendlyMessages(int status, String phrase) {
    RuntimeException failure = status >= 500
        ? HttpServerErrorException.create(HttpStatus.valueOf(status), "diagnostic", null, null, null)
        : HttpClientErrorException.create(HttpStatus.valueOf(status), "diagnostic", null, null, null);
    when(github.getLatestRun("owner", "repo")).thenThrow(failure);

    String result = tools.getBuildStatus("owner", "repo");

    assertTrue(result.contains("owner/repo"));
    assertTrue(result.contains(phrase));
    assertFalse(result.contains("diagnostic"));
  }

  @Test
  void convertsConnectionFailureToFriendlyMessage() {
    when(github.getLatestRun("owner", "repo")).thenThrow(new ResourceAccessException("diagnostic"));

    assertEquals("I couldn't reach GitHub to check owner/repo. Please try again shortly.",
        tools.getBuildStatus("owner", "repo"));
  }

  @Test
  void defaultsFailedRunLimitToFive() {
    when(github.getFailedRuns("owner", "repo", 5)).thenReturn(List.of());

    assertEquals("No failed builds found for owner/repo.", tools.getFailedRuns("owner", "repo", null));
    verify(github).getFailedRuns("owner", "repo", 5);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 6})
  void rejectsInvalidToolLimitsWithoutCallingGithub(int limit) {
    assertEquals("Please request between 1 and 5 failed builds.",
        tools.getFailedRuns("owner", "repo", limit));
    verifyNoInteractions(github, summarizer);
  }

  @Test
  void listsFailedRunIdsBranchesAndRelativeTimes() {
    when(github.getFailedRuns("owner", "repo", 1)).thenReturn(List.of(
        new GitHubRun(42L, "completed", "failure", "dev", null,
            Instant.now().minusSeconds(150), "Build")));

    assertEquals("Recent failed builds on owner/repo: Run 42: branch dev, 2 minutes ago.",
        tools.getFailedRuns("owner", "repo", 1));
  }

  @Test
  void failedRunListingHandlesGithubErrors() {
    when(github.getFailedRuns("owner", "repo", 5)).thenThrow(new RestClientException("diagnostic"));

    assertEquals("I couldn't retrieve failed builds for owner/repo. Please try again later.",
        tools.getFailedRuns("owner", "repo", 5));
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
