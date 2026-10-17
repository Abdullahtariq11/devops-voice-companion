package com.abdullah.devopsvoicecompanion.github.dtos;

import com.abdullah.devopsvoicecompanion.github.GitHubWorkflowRun;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * One page of GitHub's workflow-run listing response.
 *
 * @param totalCount total matching runs across pages
 * @param workflowRuns runs included in this page
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GitHubRunsResponse(
        int totalCount,
        List<GitHubWorkflowRun> workflowRuns
) {}
