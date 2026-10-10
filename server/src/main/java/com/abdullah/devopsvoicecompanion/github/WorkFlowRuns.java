package com.abdullah.devopsvoicecompanion.github;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkFlowRuns(
    String status,
    String conclusion,
    String headBranch,
    Instant createdAt,
    Instant updatedAt,
    String displayTitle
) {}
