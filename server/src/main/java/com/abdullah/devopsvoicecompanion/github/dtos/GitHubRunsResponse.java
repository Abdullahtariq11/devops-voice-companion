package com.abdullah.devopsvoicecompanion.github.dtos;

import com.abdullah.devopsvoicecompanion.github.WorkFlowRuns;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record GitHubRunsResponse(
        int totalCount,
        List<WorkFlowRuns> workflowRuns
) {}
