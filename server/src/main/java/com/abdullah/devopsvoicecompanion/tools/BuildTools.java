package com.abdullah.devopsvoicecompanion.tools;

import com.abdullah.devopsvoicecompanion.bedrock.BedrockSummarizer;
import com.abdullah.devopsvoicecompanion.github.GitHubClient;
import com.abdullah.devopsvoicecompanion.github.GitHubJob;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import software.amazon.awssdk.core.exception.SdkException;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Exposes GitHub Actions status, recent failures, and failure details as MCP tools. Log output is
 * intended for analysis before producing a spoken summary.
 */
@Component
public class BuildTools {
  private final GitHubClient gitHubClient;
  private final BedrockSummarizer bedrockSummarizer;

  /**
   * Creates tools backed by the GitHub API client.
   *
   * @param gitHubClient client supplied by Spring
   * @param bedrockSummarizer service that explains failures using Nova Lite
   */
  public BuildTools(GitHubClient gitHubClient, BedrockSummarizer bedrockSummarizer) {
    this.gitHubClient = gitHubClient;
    this.bedrockSummarizer = bedrockSummarizer;
  }

  /**
   * Describes the latest build using speakable status and relative timing. Completed runs use their
   * last update as an approximate completion time; active runs use their creation time.
   *
   * @param owner GitHub account or organization
   * @param repo repository name without the owner
   * @return build description, no-runs message, or friendly message for handled GitHub errors
   */
  @McpTool(
      name = "get_build_status",
      description = "Get the latest GitHub Actions build status for a repository")
  public String getBuildStatus(
      @McpToolParam(description = "GitHub owner or organization", required = true) String owner,
      @McpToolParam(description = "Repository name", required = true) String repo) {
    try {
      return gitHubClient
          .getLatestRun(owner, repo)
          .map(
              run -> {
                String result;
                if ("completed".equals(run.status())) {
                  result =
                      switch (run.conclusion() == null ? "" : run.conclusion()) {
                        case "success" -> "passed";
                        case "failure" -> "failed";
                        case "cancelled" -> "was cancelled";
                        case "timed_out" -> "timed out";
                        default -> "finished without a pass or fail result";
                      };
                } else {
                  result =
                      switch (run.status() == null ? "" : run.status()) {
                        case "in_progress" -> "is running";
                        case "queued" -> "is queued";
                        case "waiting", "pending", "requested" -> "is waiting to start";
                        default -> "has not finished yet";
                      };
                }

                boolean completed = "completed".equals(run.status());
                String time = relativeTime(completed ? run.updatedAt() : run.createdAt());

                String timing = "";
                if (!time.isEmpty()) {
                  timing = completed ? " " + time : "; created " + time;
                }

                return "The latest build on "
                    + owner
                    + "/"
                    + repo
                    + " "
                    + result
                    + timing
                    + " on branch "
                    + run.headBranch()
                    + ".";
              })
          .orElse("No builds found for " + owner + "/" + repo + ".");
    } catch (HttpClientErrorException.NotFound exception) {
      return "I couldn't find "
          + owner
          + "/"
          + repo
          + ". Check the repository name and whether it is accessible.";
    } catch (HttpClientErrorException.Unauthorized exception) {
      return "I couldn't check " + owner + "/" + repo + " because GitHub authentication failed.";
    } catch (HttpClientErrorException.Forbidden exception) {
      return "GitHub denied access to "
          + owner
          + "/"
          + repo
          + ". The token may lack permission, or a rate limit may apply.";
    } catch (HttpClientErrorException.TooManyRequests exception) {
      return "GitHub's rate limit was reached while checking "
          + owner
          + "/"
          + repo
          + ". Please try again later.";
    } catch (HttpServerErrorException exception) {
      return "GitHub is temporarily unavailable. Please try checking "
          + owner
          + "/"
          + repo
          + " again later.";
    } catch (ResourceAccessException exception) {
      return "I couldn't reach GitHub to check "
          + owner
          + "/"
          + repo
          + ". Please try again shortly.";
    }
  }

  /**
   * Lists recent failed builds with IDs that can be passed to {@link #getFailureLogs}.
   *
   * @param owner GitHub account or organization
   * @param repo repository name without the owner
   * @param limit maximum builds to return, from 1 to 5; {@code null} defaults to 5
   * @return failure listing, empty-result message, validation message, or friendly HTTP error
   */
  @McpTool(
      name = "get_failed_runs",
      description = "Get recent failed GitHub Actions builds for a repository")
  public String getFailedRuns(
      @McpToolParam(description = "GitHub owner or organization", required = true) String owner,
      @McpToolParam(description = "Repository name", required = true) String repo,
      @McpToolParam(description = "Number of failed builds, from 1 to 5. Defaults to 5", required = false)
          Integer limit) {
    int requestedLimit = limit == null ? 5 : limit;
    if (requestedLimit < 1 || requestedLimit > 5) {
      return "Please request between 1 and 5 failed builds.";
    }

    try {
      var runs = gitHubClient.getFailedRuns(owner, repo, requestedLimit);

      if (runs.isEmpty()) {
        return "No failed builds found for " + owner + "/" + repo + ".";
      }

      String details =
          runs.stream()
              .map(
                  run ->
                      "Run "
                          + run.id()
                          + ": branch "
                          + run.headBranch()
                          + ", "
                          + relativeTime(run.updatedAt()))
              .collect(Collectors.joining(". "));

      return "Recent failed builds on " + owner + "/" + repo + ": " + details + ".";
    } catch (RestClientException exception) {
      return "I couldn't retrieve failed builds for "
          + owner
          + "/"
          + repo
          + ". Please try again later.";
    }
  }

  /**
   * Collects failed or timed-out jobs and steps from the run's latest attempt. Includes up to five
   * jobs and the final 1500 log characters per job, with total output capped at 10000 characters.
   * Job metadata is retained when a log download fails. Excerpts may omit errors that occur later
   * in a long log and are not a spoken summary.
   *
   * @param owner GitHub account or organization
   * @param repo repository name without the owner
   * @param runId positive workflow run ID, available from {@code get_failed_runs}
   * @return failure details or a friendly validation, empty-result, or HTTP error message
   */
  @McpTool(
      name = "get_failure_logs",
      description =
          "Get failed jobs, failed steps, and log excerpts for a GitHub Actions run. "
              + "For analysis, not for reading aloud. Output is capped at 10000 characters.")
  public String getFailureLogs(
      @McpToolParam(description = "GitHub owner or organization", required = true) String owner,
      @McpToolParam(description = "Repository name", required = true) String repo,
      @McpToolParam(description = "Workflow run ID from get_failed_runs", required = true)
          long runId) {
    return collectFailureDetails(owner, repo, runId).text();
  }

  /**
   * Explains a workflow failure using GitHub evidence and Nova Lite.
   * Errors and empty job results bypass Bedrock. Missing logs still allow a summary
   * based on the available failed-job metadata.
   *
   * @param owner GitHub account or organization
   * @param repo repository name without the owner
   * @param runId workflow run ID from {@code get_failed_runs}
   * @return spoken summary or a friendly error message
   */
  @McpTool(
      name = "summarize_failure",
      description = "Explain why a GitHub Actions run failed in a short spoken summary")
  public String summarizeFailure(
      @McpToolParam(description = "GitHub owner or organization", required = true) String owner,
      @McpToolParam(description = "Repository name", required = true) String repo,
      @McpToolParam(description = "Workflow run ID", required = true) long runId) {
    FailureDetails details = collectFailureDetails(owner, repo, runId);

    if (!details.summarizable()) {
      return details.text();
    }

    try {
      String summary = bedrockSummarizer.summarize(owner + "/" + repo, details.text());
      if (summary == null || summary.isBlank()) {
        return "The failure summary was empty. Please try again.";
      }
      return summary;
    } catch (SdkException exception) {
      return "I found failure details for " + owner + "/" + repo
          + ", but couldn't generate a summary. Please try again later.";
    }
  }

  /**
   * Collects bounded failure evidence for reuse by log and summary tools.
   * Validation errors, empty results, and GitHub request failures bypass summarization.
   * Failed-job metadata remains summarizable even when individual logs are unavailable.
   *
   * @param owner GitHub account or organization
   * @param repo repository name without the owner
   * @param runId positive workflow run ID
   * @return failure evidence or a message marked to bypass Bedrock
   */
  private FailureDetails collectFailureDetails(String owner, String repo, long runId) {
    if (runId < 1) {
      return new FailureDetails("Please provide a positive workflow run ID.", false);
    }

    try {
      var failedJobs =
          gitHubClient.getJobs(owner, repo, runId).stream()
              .filter(
                  job -> "failure".equals(job.conclusion()) || "timed_out".equals(job.conclusion()))
              .toList();
      if (failedJobs.isEmpty()) {
        return new FailureDetails(
            "No failed jobs found for run " + runId + " on " + owner + "/" + repo + ".", false);
      }

      StringBuilder output =
          new StringBuilder("Failure details for " + owner + "/" + repo + ", run " + runId + ".\n");
      for (GitHubJob job : failedJobs.stream().limit(5).toList()) {
        output
            .append("\nJob: ")
            .append(job.name())
            .append(" (")
            .append(job.conclusion())
            .append(")\n");
        if (job.steps() != null) {
          job.steps().stream()
              .filter(
                  step ->
                      "failure".equals(step.conclusion()) || "timed_out".equals(step.conclusion()))
              .forEach(step -> output.append("Failed step: ").append(step.name()).append('\n'));
        }
        try {
          if (job.id() == null) {
            output.append("Logs unavailable: missing job ID.\n");
          } else {
            output
                .append("Log excerpt:\n")
                .append(gitHubClient.getJobLogs(owner, repo, job.id(), 1500))
                .append('\n');
          }
        } catch (RestClientResponseException exception) {
          output.append("Logs unavailable: GitHub returned HTTP ")
              .append(exception.getStatusCode().value())
              .append(".\n");
        } catch (RestClientException exception) {
          output.append("Logs unavailable. They may have expired or access may be restricted.\n");
        }
        if (output.length() > 10_000) {
          break;
        }
      }
      if (failedJobs.size() > 5) {
        output.append("\nOnly the first five failed jobs are included.");
      }
      if (output.length() > 10_000) {
        return new FailureDetails(output.substring(0, 9981) + "\n[Output truncated]", true);
      }
      return new FailureDetails(output.toString(), true);
    } catch (HttpClientErrorException.NotFound exception) {
      return new FailureDetails("I couldn't find run "
          + runId
          + " on "
          + owner
          + "/"
          + repo
          + ". Check the run ID and repository access.", false);
    } catch (RestClientException exception) {
      return new FailureDetails("I couldn't retrieve failed jobs for "
          + owner
          + "/"
          + repo
          + ". Check GitHub access or try again later.", false);
    }
  }

  /**
   * Formats elapsed time in whole minutes, hours, or days, using the current clock. Timestamps less
   * than a minute old or in the future produce {@code just now}.
   *
   * @param timestamp time to compare with now, or {@code null}
   * @return relative time phrase, or an empty string for a missing timestamp
   */
  private String relativeTime(Instant timestamp) {
    if (timestamp == null) {
      return "";
    }
    long seconds = Math.max(0, Duration.between(timestamp, Instant.now()).getSeconds());

    if (seconds < 60) {
      return "just now";
    }

    long minutes = seconds / 60;
    if (minutes < 60) {
      return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
    }

    long hours = minutes / 60;
    if (hours < 24) {
      return hours + (hours == 1 ? " hour ago" : " hours ago");
    }

    long days = hours / 24;
    return days + (days == 1 ? " day ago" : " days ago");
  }

  /**
   * Separates failure evidence from messages that should bypass Bedrock.
   *
   * @param text failure details or a user-facing message
   * @param summarizable whether the text contains failure evidence
   */
  private record FailureDetails(String text, boolean summarizable) {}
}
