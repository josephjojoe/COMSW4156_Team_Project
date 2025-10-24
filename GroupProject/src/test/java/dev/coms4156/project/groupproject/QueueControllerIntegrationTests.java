package dev.coms4156.project.groupproject;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for the QueueController REST endpoints.
 * Tests the complete flow of queue operations through HTTP requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class QueueControllerIntegrationTests {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void fullFlow_create_enqueue_dequeue_submit_getResult() throws Exception {
    // Create queue
    String createQueuePayload = "{\"name\":\"Q1\"}";
    MvcResult createRes =
        mockMvc
            .perform(
                post("/queue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createQueuePayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();

    String createBody = createRes.getResponse().getContentAsString();
    UUID queueId = UUID.fromString(objectMapper.readTree(createBody).get("id").asText());

    // Enqueue task
    String enqueuePayload = "{\"params\":\"p\",\"priority\":1}";
    MvcResult enqueueRes =
        mockMvc
            .perform(
                post("/queue/" + queueId + "/task")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(enqueuePayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();

    UUID taskId =
        UUID.fromString(
            objectMapper.readTree(enqueueRes.getResponse().getContentAsString())
                .get("id").asText());

    // Dequeue task
    mockMvc
        .perform(get("/queue/" + queueId + "/task"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(taskId.toString()))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

    // Submit result
    String submitResultPayload =
        String.format(
            "{\"taskId\":\"%s\",\"output\":\"ok\",\"status\":\"SUCCESS\"}",
            taskId);
    mockMvc
        .perform(
            post("/queue/" + queueId + "/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitResultPayload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.taskId").value(taskId.toString()))
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    // Get result
    mockMvc
        .perform(get("/queue/" + queueId + "/result/" + taskId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taskId").value(taskId.toString()))
        .andExpect(jsonPath("$.status").value("SUCCESS"));
  }

  @Test
  void dequeue_emptyQueue_returnsNoContent() throws Exception {
    // Create queue
    String createQueuePayload = "{\"name\":\"Q2\"}";
    MvcResult createRes =
        mockMvc
            .perform(
                post("/queue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createQueuePayload))
            .andExpect(status().isCreated())
            .andReturn();
    UUID queueId =
        UUID.fromString(
            objectMapper.readTree(createRes.getResponse().getContentAsString())
                .get("id").asText());

    mockMvc.perform(get("/queue/" + queueId + "/task")).andExpect(status().isNoContent());
  }
}


