package com.abdullah.devopsvoicecompanion.github.dtos;

import com.abdullah.devopsvoicecompanion.github.GitHubWorkflowRun;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GitHubRunsResponse(
        int totalCount,
        List<GitHubWorkflowRun> workflowRuns
) {}
