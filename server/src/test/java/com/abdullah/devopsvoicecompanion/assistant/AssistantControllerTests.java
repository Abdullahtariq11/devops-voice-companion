package com.abdullah.devopsvoicecompanion.assistant;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.amazon.awssdk.core.exception.SdkClientException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises JSON binding and sanitized API failures without calling AWS. */
class AssistantControllerTests {
  private BedrockToolRouter router;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    router = mock(BedrockToolRouter.class);
    mvc = MockMvcBuilders.standaloneSetup(new AssistantController(router)).build();
  }

  @Test
  void acceptsQuestionAndReturnsToolPlan() throws Exception {
    when(router.route(any())).thenReturn(new AssistantResponse("", List.of(
        new AssistantToolCall("get_build_status", Map.of("owner", "owner", "repo", "repo")))));
    mvc.perform(post("/api/assistant/route").contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"Is it green?\",\"repository\":\"owner/repo\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.toolCalls[0].name").value("get_build_status"));
    verify(router).route(new AssistantRequest("Is it green?", "owner/repo", null, null));
  }

  @Test
  void handlesValidationAndSanitizesProviderAndModelErrors() throws Exception {
    doThrow(new IllegalArgumentException("Invalid repository")).when(router).route(any());
    mvc.perform(post("/api/assistant/route").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid repository"));
    doThrow(SdkClientException.create("SECRET_PROVIDER_DETAILS")).when(router).route(any());
    mvc.perform(post("/api/assistant/route").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET_PROVIDER_DETAILS"))));
    doThrow(new IllegalStateException("Unsafe plan")).when(router).route(any());
    mvc.perform(post("/api/assistant/route").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadGateway());
  }

  @Test
  void rejectsMalformedJsonWithoutInvokingModel() throws Exception {
    mvc.perform(post("/api/assistant/route").contentType(MediaType.APPLICATION_JSON).content("not-json"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(router);
  }
}
