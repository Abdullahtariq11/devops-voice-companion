package com.abdullah.devopsvoicecompanion.github;

/**
 * A job step's execution result, used to name the step that failed.
 *
 * @param name step display name
 * @param status current execution state
 * @param conclusion final result, or null while active
 * @param number step number reported by GitHub
 */
public record GitHubStep(
    String name,
    String status,
    String conclusion,
    int number
) {}
