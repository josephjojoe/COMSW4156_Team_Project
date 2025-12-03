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
  void fullFlowCreateEnqueueDequeueSubmitGetResult() throws Exception {
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
  void dequeueEmptyQueueReturnsNoContent() throws Exception {
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
  void createQueueBlankNameReturnsBadRequest() throws Exception {
    String payload = "{\"name\":\"   \"}";
    mockMvc
          .perform(post("/queue").contentType(MediaType.APPLICATION_JSON).content(payload))
          .andExpect(status().isBadRequest());
  }

  /** Confirms that enqueuing to a nonexistent queue returns 404 Not Found. */
  @Test
  void enqueueTaskNonexistentQueueReturnsNotFound() throws Exception {
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
  void enqueueTaskMalformedUuidReturnsBadRequest() throws Exception {
    String payload = "{\"params\":\"p\",\"priority\":1}";
    mockMvc
          .perform(post("/queue/not-a-uuid/task")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
          .andExpect(status().isBadRequest());
  }

  /** Verifies that dequeuing from a nonexistent queue returns 404 Not Found. */
  @Test
  void dequeueTaskNonexistentQueueReturnsNotFound() throws Exception {
    UUID randomId = UUID.randomUUID();
    mockMvc.perform(get("/queue/" + randomId + "/task")).andExpect(status().isNotFound());
  }

  /** Checks that a malformed UUID in the dequeue path returns 400 Bad Request. */
  @Test
  void dequeueTaskMalformedUuidReturnsBadRequest() throws Exception {
    mockMvc.perform(get("/queue/not-a-uuid/task")).andExpect(status().isBadRequest());
  }

  /** Confirms that submitting a result to a nonexistent queue returns 404 Not Found. */
  @Test
  void submitResultNonexistentQueueReturnsNotFound() throws Exception {
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
  void submitResultInvalidEnumReturnsBadRequest() throws Exception {
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
  void getResultNonexistentQueueReturnsNotFound() throws Exception {
    mockMvc
          .perform(get("/queue/" + UUID.randomUUID() + "/result/" + UUID.randomUUID()))
          .andExpect(status().isNotFound());
  }

  /** Checks that malformed UUIDs in the get result path return 400 Bad Request. */
  @Test
  void getResultMalformedUuidReturnsBadRequest() throws Exception {
    mockMvc.perform(get("/queue/not-a-uuid/result/also-bad"))
          .andExpect(status().isBadRequest());
  }

  /** Ensures that each endpoint logs an INFO-level message when called. */
  @Test
  void loggingEachEndpointEmitsInfoLog() throws Exception {
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
  void multiClientIsolationInterleavedCallsDoNotInterfere() throws Exception {
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

  // --- Queue Status Endpoint Tests ---

  /** Tests that status endpoint returns correct counts for an empty queue. */
  @Test
  void getQueueStatusEmptyQueueReturnsZeroCounts() throws Exception {
    // Create queue
    String createQueuePayload = "{\"name\":\"StatusTestQueue\"}";
    MvcResult createRes = mockMvc
          .perform(post("/queue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createQueuePayload))
          .andExpect(status().isCreated())
          .andReturn();
    
    UUID queueId = UUID.fromString(
          objectMapper.readTree(createRes.getResponse().getContentAsString())
                .get("id").asText());

    // Get status for empty queue
    mockMvc.perform(get("/queue/" + queueId + "/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(queueId.toString()))
          .andExpect(jsonPath("$.name").value("StatusTestQueue"))
          .andExpect(jsonPath("$.pendingTaskCount").value(0))
          .andExpect(jsonPath("$.completedResultCount").value(0))
          .andExpect(jsonPath("$.hasPendingTasks").value(false));
  }

  /** Tests that status endpoint returns 404 for non-existent queue. */
  @Test
  void getQueueStatusNonexistentQueueReturnsNotFound() throws Exception {
    UUID randomId = UUID.randomUUID();
    mockMvc.perform(get("/queue/" + randomId + "/status"))
          .andExpect(status().isNotFound());
  }

  /** Tests that status counts update correctly after enqueue operations. */
  @Test
  void getQueueStatusAfterEnqueueCountsUpdateCorrectly() throws Exception {
    // Create queue
    String createQueuePayload = "{\"name\":\"EnqueueTestQueue\"}";
    MvcResult createRes = mockMvc
          .perform(post("/queue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createQueuePayload))
          .andExpect(status().isCreated())
          .andReturn();
    
    UUID queueId = UUID.fromString(
          objectMapper.readTree(createRes.getResponse().getContentAsString())
                .get("id").asText());

    // Enqueue 3 tasks
    for (int i = 1; i <= 3; i++) {
      String enqueuePayload = String.format(
            "{\"params\":\"task%d\",\"priority\":%d}", i, i);
      mockMvc.perform(post("/queue/" + queueId + "/task")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enqueuePayload))
            .andExpect(status().isCreated());
    }

    // Check status shows 3 pending tasks
    mockMvc.perform(get("/queue/" + queueId + "/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.pendingTaskCount").value(3))
          .andExpect(jsonPath("$.completedResultCount").value(0))
          .andExpect(jsonPath("$.hasPendingTasks").value(true));
  }

  /** Tests that status counts update correctly after dequeue and submit operations. */
  @Test
  void getQueueStatusAfterDequeueAndSubmitCountsUpdateCorrectly() throws Exception {
    // Create queue
    String createQueuePayload = "{\"name\":\"WorkflowTestQueue\"}";
    MvcResult createRes = mockMvc
          .perform(post("/queue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createQueuePayload))
          .andExpect(status().isCreated())
          .andReturn();
    
    UUID queueId = UUID.fromString(
          objectMapper.readTree(createRes.getResponse().getContentAsString())
                .get("id").asText());

    // Enqueue 5 tasks
    for (int i = 1; i <= 5; i++) {
      String enqueuePayload = String.format(
            "{\"params\":\"task%d\",\"priority\":%d}", i, i);
      mockMvc.perform(post("/queue/" + queueId + "/task")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enqueuePayload))
            .andExpect(status().isCreated());
    }

    // Initial status: 5 pending, 0 completed
    mockMvc.perform(get("/queue/" + queueId + "/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.pendingTaskCount").value(5))
          .andExpect(jsonPath("$.completedResultCount").value(0))
          .andExpect(jsonPath("$.hasPendingTasks").value(true));

    // Dequeue and complete 2 tasks
    for (int i = 0; i < 2; i++) {
      MvcResult dequeueRes = mockMvc.perform(get("/queue/" + queueId + "/task"))
            .andExpect(status().isOk())
            .andReturn();
      
      UUID taskId = UUID.fromString(
            objectMapper.readTree(dequeueRes.getResponse().getContentAsString())
                  .get("id").asText());
      
      String submitPayload = String.format(
            "{\"taskId\":\"%s\",\"output\":\"result\",\"status\":\"SUCCESS\"}", taskId);
      mockMvc.perform(post("/queue/" + queueId + "/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitPayload))
            .andExpect(status().isCreated());
    }

    // Status after 2 completed: 3 pending, 2 completed
    mockMvc.perform(get("/queue/" + queueId + "/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.pendingTaskCount").value(3))
          .andExpect(jsonPath("$.completedResultCount").value(2))
          .andExpect(jsonPath("$.hasPendingTasks").value(true));

    // Complete remaining 3 tasks
    for (int i = 0; i < 3; i++) {
      MvcResult dequeueRes = mockMvc.perform(get("/queue/" + queueId + "/task"))
            .andExpect(status().isOk())
            .andReturn();
      
      UUID taskId = UUID.fromString(
            objectMapper.readTree(dequeueRes.getResponse().getContentAsString())
                  .get("id").asText());
      
      String submitPayload = String.format(
            "{\"taskId\":\"%s\",\"output\":\"result\",\"status\":\"SUCCESS\"}", taskId);
      mockMvc.perform(post("/queue/" + queueId + "/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitPayload))
            .andExpect(status().isCreated());
    }

    // Final status: 0 pending, 5 completed
    mockMvc.perform(get("/queue/" + queueId + "/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.pendingTaskCount").value(0))
          .andExpect(jsonPath("$.completedResultCount").value(5))
          .andExpect(jsonPath("$.hasPendingTasks").value(false));
  }

  /** Tests that status endpoint handles malformed UUID correctly. */
  @Test
  void getQueueStatusMalformedUuidReturnsBadRequest() throws Exception {
    mockMvc.perform(get("/queue/not-a-valid-uuid/status"))
          .andExpect(status().isBadRequest());
  }

  /**
   * Tests aggregator use case: polling status to detect completion.
   * This simulates the aggregator's workflow of checking status periodically.
   */
  @Test
  void getQueueStatusAggregatorUseCaseDetectsCompletion() throws Exception {
    // Create queue
    String createQueuePayload = "{\"name\":\"AggregatorTestQueue\"}";
    MvcResult createRes = mockMvc
          .perform(post("/queue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createQueuePayload))
          .andExpect(status().isCreated())
          .andReturn();
    
    UUID queueId = UUID.fromString(
          objectMapper.readTree(createRes.getResponse().getContentAsString())
                .get("id").asText());

    int totalTasks = 10;

    // Producer: Enqueue 10 tasks
    for (int i = 1; i <= totalTasks; i++) {
      String enqueuePayload = String.format(
            "{\"params\":\"page%d\",\"priority\":%d}", i, i);
      mockMvc.perform(post("/queue/" + queueId + "/task")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enqueuePayload))
            .andExpect(status().isCreated());
    }

    // Aggregator: Check initial status
    mockMvc.perform(get("/queue/" + queueId + "/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.pendingTaskCount").value(totalTasks))
          .andExpect(jsonPath("$.completedResultCount").value(0))
          .andExpect(jsonPath("$.hasPendingTasks").value(true));

    // Workers: Process all tasks
    for (int i = 0; i < totalTasks; i++) {
      MvcResult dequeueRes = mockMvc.perform(get("/queue/" + queueId + "/task"))
            .andExpect(status().isOk())
            .andReturn();
      
      UUID taskId = UUID.fromString(
            objectMapper.readTree(dequeueRes.getResponse().getContentAsString())
                  .get("id").asText());
      
      String submitPayload = String.format(
            "{\"taskId\":\"%s\",\"output\":\"quiz_data_%d\",\"status\":\"SUCCESS\"}", 
            taskId, i);
      mockMvc.perform(post("/queue/" + queueId + "/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitPayload))
            .andExpect(status().isCreated());
    }

    // Aggregator: Poll status and detect completion
    mockMvc.perform(get("/queue/" + queueId + "/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.pendingTaskCount").value(0))
          .andExpect(jsonPath("$.completedResultCount").value(totalTasks))
          .andExpect(jsonPath("$.hasPendingTasks").value(false));

    // Aggregator can now collect all results knowing processing is complete
  }
}