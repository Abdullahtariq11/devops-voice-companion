package com.abdullah.devopsvoicecompanion.github.dtos;

import com.abdullah.devopsvoicecompanion.github.GitHubJob;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * One page of GitHub's job listing response.
 *
 * @param totalCount total matching jobs, used to determine when pagination is complete
 * @param jobs jobs included in this page
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GitHubJobsResponse(
    int totalCount,
    List<GitHubJob> jobs
) {}
