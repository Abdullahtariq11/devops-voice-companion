package com.abdullah.devopsvoicecompanion.tools;

import com.abdullah.devopsvoicecompanion.github.GitHubClient;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;

@Component
public class BuildTools {
  private final GitHubClient gitHubClient;

  public BuildTools(GitHubClient gitHubClient) {
    this.gitHubClient = gitHubClient;
  }

  /** */
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

  /** */
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
}
