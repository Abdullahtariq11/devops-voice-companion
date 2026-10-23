package com.abdullah.devopsvoicecompanion;

import com.abdullah.devopsvoicecompanion.bedrock.BedrockSummarizer;
import com.abdullah.devopsvoicecompanion.assistant.BedrockToolRouter;
import com.abdullah.devopsvoicecompanion.github.GitHubClient;
import com.abdullah.devopsvoicecompanion.github.GitHubJob;
import com.abdullah.devopsvoicecompanion.github.GitHubRun;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Verifies discovery and invocation through the real HTTP MCP endpoint with mocked services. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServerApplicationTests {
  @Value("${local.server.port}")
  private int port;

  @MockitoBean
  private GitHubClient github;

  @MockitoBean
  private BedrockSummarizer summarizer;

  @MockitoBean
  private BedrockToolRouter toolRouter;

  private McpSyncClient client;

  @BeforeEach
  void connect() {
    var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
        .endpoint("/mcp").build();
    client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(5)).build();
    client.initialize();
  }

  @AfterEach
  void disconnect() {
    if (client != null) {
      client.closeGracefully();
    }
  }

  @Test
  void discoversAllFourToolsWithRequiredInputs() {
    var tools = client.listTools().tools();
    assertEquals(Set.of("get_build_status", "get_failed_runs", "get_failure_logs", "summarize_failure"),
        tools.stream().map(McpSchema.Tool::name).collect(Collectors.toSet()));
    var summaryTool = tools.stream().filter(tool -> tool.name().equals("summarize_failure"))
        .findFirst().orElseThrow();
    assertTrue(((List<?>) summaryTool.inputSchema().get("required"))
        .containsAll(List.of("owner", "repo", "runId")));
  }

  @Test
  void invokesBuildStatusThroughMcp() {
    when(github.getLatestRun("owner", "repo")).thenReturn(Optional.of(
        new GitHubRun(42L, "completed", "success", "main", null, null, "Build")));

    var result = client.callTool(new McpSchema.CallToolRequest("get_build_status",
        Map.of("owner", "owner", "repo", "repo")));

    assertFalse(Boolean.TRUE.equals(result.isError()));
    assertEquals("The latest build on owner/repo passed on branch main.", text(result));
    verify(github).getLatestRun("owner", "repo");
    verifyNoInteractions(summarizer);
  }

  @Test
  void invokesFailedRunsWithOmittedOptionalLimit() {
    when(github.getFailedRuns("owner", "repo", 5)).thenReturn(List.of());

    var result = client.callTool(new McpSchema.CallToolRequest("get_failed_runs",
        Map.of("owner", "owner", "repo", "repo")));

    assertFalse(Boolean.TRUE.equals(result.isError()));
    assertEquals("No failed builds found for owner/repo.", text(result));
    verify(github).getFailedRuns("owner", "repo", 5);
  }

  @Test
  void invokesFailureLogsThroughMcp() {
    when(github.getJobs("owner", "repo", 42)).thenReturn(List.of(
        new GitHubJob(123L, "Tests", "completed", "failure", List.of())));
    when(github.getJobLogs("owner", "repo", 123, 1500)).thenReturn("PaymentServiceTest failed.");

    var result = client.callTool(new McpSchema.CallToolRequest("get_failure_logs",
        Map.of("owner", "owner", "repo", "repo", "runId", 42)));

    assertFalse(Boolean.TRUE.equals(result.isError()));
    assertTrue(text(result).contains("Job: Tests (failure)"));
    assertTrue(text(result).contains("PaymentServiceTest failed."));
    verifyNoInteractions(summarizer);
  }

  @Test
  void invokesSummaryThroughMcpWithoutCallingExternalServices() {
    when(github.getJobs("owner", "repo", 42)).thenReturn(List.of(
        new GitHubJob(123L, "Tests", "completed", "failure", List.of())));
    when(github.getJobLogs("owner", "repo", 123, 1500)).thenReturn("Expected 100, actual 90.");
    when(summarizer.summarize(eq("owner/repo"), contains("Expected 100, actual 90.")))
        .thenReturn("The payment test expected 100 but received 90.");

    var result = client.callTool(new McpSchema.CallToolRequest("summarize_failure",
        Map.of("owner", "owner", "repo", "repo", "runId", 42)));

    assertFalse(Boolean.TRUE.equals(result.isError()));
    assertEquals("The payment test expected 100 but received 90.", text(result));
    verify(summarizer).summarize(eq("owner/repo"), contains("Expected 100, actual 90."));
  }

  private String text(McpSchema.CallToolResult result) {
    return result.content().stream().filter(McpSchema.TextContent.class::isInstance)
        .map(McpSchema.TextContent.class::cast).map(McpSchema.TextContent::text)
        .collect(Collectors.joining(" "));
  }
}
