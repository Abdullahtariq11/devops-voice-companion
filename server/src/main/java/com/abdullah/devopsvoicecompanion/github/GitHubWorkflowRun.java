package com.abdullah.devopsvoicecompanion.github;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

/**
 * Maps a workflow run from GitHub's snake_case JSON to the application's run model.
 *
 * @param id workflow run ID
 * @param status current execution state
 * @param conclusion final result, or null while active
 * @param headBranch branch associated with the run
 * @param createdAt run creation time
 * @param updatedAt last update time
 * @param displayTitle GitHub's display title for the run
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GitHubWorkflowRun(
    Long id,
    String status,
    String conclusion,
    String headBranch,
    Instant createdAt,
    Instant updatedAt,
    String displayTitle
) {
    /**
     * Copies API fields into the model consumed by the tools.
     *
     * @return run data including the ID needed for subsequent job requests
     */
    public GitHubRun toGitHubRun() {
        return new GitHubRun(
                id,
                status,
                conclusion,
                headBranch,
                createdAt,
                updatedAt,
                displayTitle
        );
    }
}
