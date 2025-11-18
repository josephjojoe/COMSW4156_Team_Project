package dev.coms4156.project.server;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

  /**
   * Tests the complete lifecycle: create queue, enqueue task, dequeue,
   * submit result, and retrieve it.
   */
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

  /** Verifies that dequeuing from an empty queue returns 204 No Content. */
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

  // --- API invalid/atypical tests per endpoint ---

  /** Checks that creating a queue with a blank name returns 400 Bad Request. */
  @Test
  void createQueue_blankName_returnsBadRequest() throws Exception {
    String payload = "{\"name\":\"   \"}";
    mockMvc
          .perform(post("/queue").contentType(MediaType.APPLICATION_JSON).content(payload))
          .andExpect(status().isBadRequest());
  }

  /** Confirms that enqueuing to a nonexistent queue returns 404 Not Found. */
  @Test
  void enqueueTask_nonexistentQueue_returnsNotFound() throws Exception {
    UUID randomId = UUID.randomUUID();
    String payload = "{\"params\":\"p\",\"priority\":1}";
    mockMvc
          .perform(
                post("/queue/" + randomId + "/task")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(payload))
          .andExpect(status().isNotFound());
  }

  /** Tests that a malformed UUID in the enqueue path returns 400 Bad Request. */
  @Test
  void enqueueTask_malformedUuid_returnsBadRequest() throws Exception {
    String payload = "{\"params\":\"p\",\"priority\":1}";
    mockMvc
          .perform(post("/queue/not-a-uuid/task")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
          .andExpect(status().isBadRequest());
  }

  /** Verifies that dequeuing from a nonexistent queue returns 404 Not Found. */
  @Test
  void dequeueTask_nonexistentQueue_returnsNotFound() throws Exception {
    UUID randomId = UUID.randomUUID();
    mockMvc.perform(get("/queue/" + randomId + "/task")).andExpect(status().isNotFound());
  }

  /** Checks that a malformed UUID in the dequeue path returns 400 Bad Request. */
  @Test
  void dequeueTask_malformedUuid_returnsBadRequest() throws Exception {
    mockMvc.perform(get("/queue/not-a-uuid/task")).andExpect(status().isBadRequest());
  }

  /** Confirms that submitting a result to a nonexistent queue returns 404 Not Found. */
  @Test
  void submitResult_nonexistentQueue_returnsNotFound() throws Exception {
    UUID taskId = UUID.randomUUID();
    String payload =
          String.format("{\"taskId\":\"%s\",\"output\":\"ok\",\"status\":\"SUCCESS\"}", taskId);
    mockMvc
          .perform(
                post("/queue/" + UUID.randomUUID() + "/result")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(payload))
          .andExpect(status().isNotFound());
  }

  /** Tests that an invalid status enum value returns 400 Bad Request. */
  @Test
  void submitResult_invalidEnum_returnsBadRequest() throws Exception {
    // First create a queue to avoid 404 masking 400
    String createQueuePayload = "{\"name\":\"Q3\"}";
    MvcResult createRes =
          mockMvc
                .perform(post("/queue").contentType(MediaType.APPLICATION_JSON)
                      .content(createQueuePayload))
                .andExpect(status().isCreated())
                .andReturn();
    UUID queueId =
          UUID.fromString(
                objectMapper.readTree(createRes.getResponse()
                      .getContentAsString()).get("id").asText());

    String payload = String.format("{\"taskId\":\"%s\",\"output\":\"ok\",\"status\":\"BOGUS\"}",
          UUID.randomUUID());
    mockMvc
          .perform(post("/queue/" + queueId + "/result")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
          .andExpect(status().isBadRequest());
  }

  /** Verifies that fetching a result from a nonexistent queue returns 404 Not Found. */
  @Test
  void getResult_nonexistentQueue_returnsNotFound() throws Exception {
    mockMvc
          .perform(get("/queue/" + UUID.randomUUID() + "/result/" + UUID.randomUUID()))
          .andExpect(status().isNotFound());
  }

  /** Checks that malformed UUIDs in the get result path return 400 Bad Request. */
  @Test
  void getResult_malformedUuid_returnsBadRequest() throws Exception {
    mockMvc.perform(get("/queue/not-a-uuid/result/also-bad"))
          .andExpect(status().isBadRequest());
  }

  /** Ensures that each endpoint logs an INFO-level message when called. */
  @Test
  void logging_each_endpoint_emits_info_log() throws Exception {
    Logger controllerLogger = (Logger) LoggerFactory
          .getLogger("dev.coms4156.project.server.controller.QueueController");
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    controllerLogger.addAppender(listAppender);

    // create
    mockMvc.perform(post("/queue").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"LQ\"}"))
          .andExpect(status().isCreated());
    final boolean hasCreate = listAppender.list.stream().anyMatch(e
          -> e.getLevel() == Level.INFO
          && e.getFormattedMessage().startsWith("createQueue"));

    // enqueue (need a queue)
    MvcResult createRes = mockMvc.perform(post("/queue")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"LQ2\"}"))
          .andExpect(status().isCreated()).andReturn();
    UUID qid = UUID.fromString(objectMapper.readTree(createRes.getResponse()
          .getContentAsString()).get("id").asText());
    mockMvc.perform(post("/queue/" + qid + "/task")
                .contentType(MediaType.APPLICATION_JSON).content(
                      "{\"params\":\"p\",\"priority\":1}"))
          .andExpect(status().isCreated());
    final boolean hasEnqueue = listAppender.list.stream().anyMatch(e
          -> e.getLevel() == Level.INFO
          && e.getFormattedMessage().startsWith("enqueueTask"));

    // dequeue
    MvcResult dequeueRes = mockMvc.perform(get("/queue/" + qid + "/task"))
          .andExpect(status().isOk()).andReturn();
    UUID taskId = UUID.fromString(objectMapper.readTree(dequeueRes.getResponse()
          .getContentAsString()).get("id").asText());
    boolean hasDequeue = listAppender.list.stream().anyMatch(e
          -> e.getLevel() == Level.INFO
          && e.getFormattedMessage().startsWith("dequeueTask"));

    // submit result
    mockMvc.perform(post("/queue/" + qid + "/result").contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                      "{\"taskId\":\"%s\",\"output\":\"ok\",\"status\":\"SUCCESS\"}", taskId)))
          .andExpect(status().isCreated());
    boolean hasSubmit = listAppender.list.stream().anyMatch(e
          -> e.getLevel() == Level.INFO
          && e.getFormattedMessage().startsWith("submitResult"));

    // get result (will likely 404 but should still log)
    mockMvc.perform(get("/queue/" + qid + "/result/" + taskId))
          .andExpect(status().isOk());
    boolean hasGet = listAppender.list.stream().anyMatch(e
          -> e.getLevel() == Level.INFO
          && e.getFormattedMessage().startsWith("getResult"));

    assertTrue(hasCreate && hasEnqueue && hasDequeue && hasSubmit && hasGet);
  }

  /** Tests that multiple queues remain isolated when operations are interleaved. */
  @Test
  void multiClientIsolation_interleavedCalls_doNotInterfere() throws Exception {
    // Create two queues
    UUID q1 = UUID.fromString(
          objectMapper.readTree(
                      mockMvc.perform(post("/queue").contentType(MediaType.APPLICATION_JSON)
                                  .content("{\"name\":\"A\"}"))
                            .andExpect(status().isCreated()).andReturn().getResponse()
                            .getContentAsString())
                .get("id").asText());

    UUID q2 = UUID.fromString(
          objectMapper.readTree(
                      mockMvc.perform(post("/queue").contentType(MediaType.APPLICATION_JSON)
                                  .content("{\"name\":\"B\"}"))
                            .andExpect(status().isCreated()).andReturn().getResponse()
                            .getContentAsString())
                .get("id").asText());

    // Enqueue tasks into both queues
    UUID t1 = UUID.fromString(objectMapper.readTree(
          mockMvc.perform(post("/queue/" + q1 + "/task").contentType(MediaType.APPLICATION_JSON)
                      .content("{\"params\":\"p1\",\"priority\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).get("id").asText());

    UUID t2 = UUID.fromString(objectMapper.readTree(
          mockMvc.perform(post("/queue/" + q2 + "/task").contentType(MediaType.APPLICATION_JSON)
                      .content("{\"params\":\"p2\",\"priority\":0}"))
                .andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).get("id").asText());

    // Interleaved dequeue: q2 first, then q1
    mockMvc.perform(get("/queue/" + q2 + "/task")).andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(t2.toString()));
    mockMvc.perform(get("/queue/" + q1 + "/task")).andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(t1.toString()));

    // Submit results to their respective queues and ensure retrievability
    String payload1 = objectMapper.createObjectNode()
          .put("taskId", t1.toString())
          .put("output", "ok1")
          .put("status", "SUCCESS")
          .toString();
    mockMvc.perform(post("/queue/" + q1 + "/result").contentType(MediaType.APPLICATION_JSON)
                .content(payload1))
          .andExpect(status().isCreated());
    String payload2 = objectMapper.createObjectNode()
          .put("taskId", t2.toString())
          .put("output", "ok2")
          .put("status", "SUCCESS")
          .toString();
    mockMvc.perform(post("/queue/" + q2 + "/result").contentType(MediaType.APPLICATION_JSON)
                .content(payload2))
          .andExpect(status().isCreated());

    mockMvc.perform(get("/queue/" + q1 + "/result/" + t1)).andExpect(status().isOk())
          .andExpect(jsonPath("$.taskId").value(t1.toString()));
    mockMvc.perform(get("/queue/" + q2 + "/result/" + t2)).andExpect(status().isOk())
          .andExpect(jsonPath("$.taskId").value(t2.toString()));

    // Cross-check: ensure no leakage across queues
    mockMvc.perform(get("/queue/" + q1 + "/result/" + t2)).andExpect(status().isNotFound());
    mockMvc.perform(get("/queue/" + q2 + "/result/" + t1)).andExpect(status().isNotFound());
  }
}