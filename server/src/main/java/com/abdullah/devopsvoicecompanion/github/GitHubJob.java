package com.abdullah.devopsvoicecompanion.github;

import java.util.List;

/**
 * Workflow job metadata used to identify failed steps and request job logs.
 *
 * @param id job ID used by the log-download endpoint
 * @param name job display name
 * @param status current execution state
 * @param conclusion final result, or null while active
 * @param steps steps returned by GitHub; may be null if omitted
 */
public record GitHubJob(
    Long id,
    String name,
    String status,
    String conclusion,
    List<GitHubStep> steps
) {}
