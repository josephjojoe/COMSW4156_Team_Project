package dev.coms4156.project.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.server.model.Queue;
import dev.coms4156.project.server.model.QueueStore;
import dev.coms4156.project.server.model.Result;
import dev.coms4156.project.server.model.Task;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This class contains the unit tests for the QueueStore class.
 */
public class QueueStoreUnitTests {
  private static final String SNAPSHOT_FILE = "queue_snapshot.json";
  private static final String SNAPSHOT_TEMP_FILE = "queue_snapshot.tmp";

  private QueueStore store;

  /**
  * Before each test it resets the QueueStore state
  * to nsures each test executes in a clean and isolated environment.
  */
  @BeforeEach
  void setUp() {
    store = QueueStore.getInstance();
    store.clearAll();
  }

  /**
   * Clean up snapshot files after each test to ensure test isolation.
   */
  @AfterEach
  void tearDown() {
    store.clearAll();
    // Clean up any snapshot files created during tests
    new File(SNAPSHOT_FILE).delete();
    new File(SNAPSHOT_TEMP_FILE).delete();
  }

  /**
  * Verifies that multiple calls to getInstance() return the same object reference.
  */
  @Test
  public void testSingletonInstance() {
    QueueStore store1 = QueueStore.getInstance();
    QueueStore store2 = QueueStore.getInstance();
    assertSame(store1, store2, "QueueStore return the same instance");
  }

  /**
  * Verifies that a new Queue can be created and retrieved successfully.
  */
  @Test
  public void testCreateQueue() {
    Queue q1 = store.createQueue("Queue 1");
    assertNotNull(q1);
    assertEquals("Queue 1", q1.getName());
    assertNotNull(store.getQueue(q1.getId()));
  }

  /**
   * Verifies that an existing queue can be removed successfully.
   */
  @Test
  public void testRemoveQueueSuccess() {
    Queue q = store.createQueue("Remove");
    assertTrue(store.removeQueue(q.getId()));
    assertNull(store.getQueue(q.getId()));
  }

  /**
   * Verifies that removing a non-existent queue fails gracefully.
   */
  @Test
  public void testRemoveQueueFailure() {
    assertFalse(store.removeQueue(UUID.randomUUID()));
  }

  // ========================
  // Snapshot Tests
  // ========================

  /**
   * Verifies that saveSnapshot creates a snapshot file.
   */
  @Test
  public void testSaveSnapshotCreatesFile() {
    store.createQueue("TestQueue");
    store.saveSnapshot();

    File snapshotFile = new File(SNAPSHOT_FILE);
    assertTrue(snapshotFile.exists(), "Snapshot file should be created");
  }

  /**
   * Verifies that saveSnapshot correctly serializes queue data.
   */
  @Test
  public void testSaveSnapshotContainsQueueData() throws IOException {
    Queue queue = store.createQueue("TestQueue");
    Task task = new Task("test params", 1);
    queue.enqueue(task);
    Result result = new Result(task.getId(), "test output", Result.ResultStatus.SUCCESS);
    queue.addResult(result);

    store.saveSnapshot();

    String content = Files.readString(new File(SNAPSHOT_FILE).toPath(), StandardCharsets.UTF_8);
    assertTrue(content.contains("TestQueue"), "Snapshot should contain queue name");
    assertTrue(content.contains("test params"), "Snapshot should contain task params");
    assertTrue(content.contains("test output"), "Snapshot should contain result output");
    assertTrue(content.contains("SUCCESS"), "Snapshot should contain result status");
  }

  /**
   * Verifies that save and load snapshot round-trip preserves data.
   */
  @Test
  public void testSaveAndLoadSnapshotRoundTrip() throws Exception {
    // Create queues with tasks and results
    Queue queue1 = store.createQueue("Queue1");
    Task task1 = new Task("params1", 1);
    queue1.enqueue(task1);
    Result result1 = new Result(task1.getId(), "output1", Result.ResultStatus.SUCCESS);
    queue1.addResult(result1);

    Queue queue2 = store.createQueue("Queue2");
    Task task2 = new Task("params2", 2);
    queue2.enqueue(task2);

    final UUID queue1Id = queue1.getId();
    final UUID queue2Id = queue2.getId();
    final UUID task1Id = task1.getId();

    // Save snapshot
    store.saveSnapshot();

    // Clear all queues
    store.clearAll();
    assertNull(store.getQueue(queue1Id), "Queue should be cleared");

    // Load snapshot using reflection (since loadSnapshot is private)
    Method loadSnapshotMethod = QueueStore.class.getDeclaredMethod("loadSnapshot");
    loadSnapshotMethod.setAccessible(true);
    loadSnapshotMethod.invoke(store);

    // Verify queues were restored
    Queue restoredQueue1 = store.getQueue(queue1Id);
    Queue restoredQueue2 = store.getQueue(queue2Id);
    assertNotNull(restoredQueue1, "Queue1 should be restored");
    assertNotNull(restoredQueue2, "Queue2 should be restored");
    assertEquals("Queue1", restoredQueue1.getName());
    assertEquals("Queue2", restoredQueue2.getName());

    // Verify tasks were restored
    assertEquals(1, restoredQueue1.getTaskCount(), "Queue1 should have 1 task");
    assertEquals(1, restoredQueue2.getTaskCount(), "Queue2 should have 1 task");

    // Verify results were restored
    Result restoredResult = restoredQueue1.getResult(task1Id);
    assertNotNull(restoredResult, "Result should be restored");
    assertEquals("output1", restoredResult.getOutput());
    assertEquals(Result.ResultStatus.SUCCESS, restoredResult.getStatus());
  }

  /**
   * Verifies that loadSnapshot handles invalid task status gracefully
   * by skipping the invalid task and continuing.
   */
  @Test
  public void testLoadSnapshotWithInvalidTaskStatus() throws Exception {
    // Write a snapshot file with an invalid task status
    String invalidSnapshot = """
        {
          "queues": [
            {
              "id": "550e8400-e29b-41d4-a716-446655440000",
              "name": "TestQueue",
              "tasks": [
                {
                  "id": "550e8400-e29b-41d4-a716-446655440001",
                  "params": "valid task",
                  "priority": 1,
                  "status": "PENDING"
                },
                {
                  "id": "550e8400-e29b-41d4-a716-446655440002",
                  "params": "invalid task",
                  "priority": 2,
                  "status": "INVALID_STATUS"
                },
                {
                  "id": "550e8400-e29b-41d4-a716-446655440003",
                  "params": "another valid task",
                  "priority": 3,
                  "status": "IN_PROGRESS"
                }
              ],
              "results": []
            }
          ],
          "timestamp": 1234567890,
          "version": "1.0"
        }
        """;
    Files.writeString(new File(SNAPSHOT_FILE).toPath(), invalidSnapshot, StandardCharsets.UTF_8);

    // Load snapshot - should not throw, should skip the invalid task
    Method loadSnapshotMethod = QueueStore.class.getDeclaredMethod("loadSnapshot");
    loadSnapshotMethod.setAccessible(true);
    loadSnapshotMethod.invoke(store);

    // Verify the queue was created with only valid tasks
    UUID queueId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    Queue queue = store.getQueue(queueId);
    assertNotNull(queue, "Queue should be restored");
    assertEquals("TestQueue", queue.getName());
    // Should have 2 tasks (the invalid one was skipped)
    assertEquals(2, queue.getTaskCount(), "Should have 2 valid tasks (invalid one skipped)");
  }

  /**
   * Verifies that loadSnapshot handles invalid result status gracefully
   * by skipping the invalid result and continuing.
   */
  @Test
  public void testLoadSnapshotWithInvalidResultStatus() throws Exception {
    // Write a snapshot file with an invalid result status
    String invalidSnapshot = """
        {
          "queues": [
            {
              "id": "550e8400-e29b-41d4-a716-446655440000",
              "name": "TestQueue",
              "tasks": [],
              "results": [
                {
                  "taskId": "550e8400-e29b-41d4-a716-446655440001",
                  "output": "valid result",
                  "status": "SUCCESS",
                  "timestamp": "2024-01-01T12:00:00"
                },
                {
                  "taskId": "550e8400-e29b-41d4-a716-446655440002",
                  "output": "invalid result",
                  "status": "INVALID_RESULT_STATUS",
                  "timestamp": "2024-01-01T12:00:00"
                },
                {
                  "taskId": "550e8400-e29b-41d4-a716-446655440003",
                  "output": "another valid result",
                  "status": "FAILURE",
                  "timestamp": "2024-01-01T12:00:00"
                }
              ]
            }
          ],
          "timestamp": 1234567890,
          "version": "1.0"
        }
        """;
    Files.writeString(new File(SNAPSHOT_FILE).toPath(), invalidSnapshot, StandardCharsets.UTF_8);

    // Load snapshot - should not throw, should skip the invalid result
    Method loadSnapshotMethod = QueueStore.class.getDeclaredMethod("loadSnapshot");
    loadSnapshotMethod.setAccessible(true);
    loadSnapshotMethod.invoke(store);

    // Verify the queue was created with only valid results
    UUID queueId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    Queue queue = store.getQueue(queueId);
    assertNotNull(queue, "Queue should be restored");
    // Should have 2 results (the invalid one was skipped)
    assertEquals(2, queue.getResultCount(), "Should have 2 valid results (invalid one skipped)");

    // Verify the valid results exist
    UUID taskId1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    UUID taskId3 = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
    assertNotNull(queue.getResult(taskId1), "First valid result should exist");
    assertNotNull(queue.getResult(taskId3), "Third valid result should exist");
  }

  /**
   * Verifies that loadSnapshot handles invalid UUID gracefully
   * by skipping the entry and continuing.
   */
  @Test
  public void testLoadSnapshotWithInvalidTaskUuid() throws Exception {
    // Write a snapshot file with an invalid task UUID
    String invalidSnapshot = """
        {
          "queues": [
            {
              "id": "550e8400-e29b-41d4-a716-446655440000",
              "name": "TestQueue",
              "tasks": [
                {
                  "id": "valid-uuid-format-here-000000000001",
                  "params": "task with bad uuid",
                  "priority": 1,
                  "status": "PENDING"
                },
                {
                  "id": "550e8400-e29b-41d4-a716-446655440002",
                  "params": "valid task",
                  "priority": 2,
                  "status": "PENDING"
                }
              ],
              "results": []
            }
          ],
          "timestamp": 1234567890,
          "version": "1.0"
        }
        """;
    Files.writeString(new File(SNAPSHOT_FILE).toPath(), invalidSnapshot, StandardCharsets.UTF_8);

    // Load snapshot - should not throw, should skip the task with invalid UUID
    Method loadSnapshotMethod = QueueStore.class.getDeclaredMethod("loadSnapshot");
    loadSnapshotMethod.setAccessible(true);
    loadSnapshotMethod.invoke(store);

    // Verify the queue was created with only the valid task
    UUID queueId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    Queue queue = store.getQueue(queueId);
    assertNotNull(queue, "Queue should be restored");
    // Should have 1 task (the one with invalid UUID was skipped)
    assertEquals(1, queue.getTaskCount(), "Should have 1 valid task (invalid UUID skipped)");
  }

  /**
   * Verifies that loadSnapshot handles empty snapshot file gracefully.
   */
  @Test
  public void testLoadSnapshotWithEmptyFile() throws Exception {
    // Write an empty file
    Files.writeString(new File(SNAPSHOT_FILE).toPath(), "", StandardCharsets.UTF_8);

    // Load snapshot - should not throw
    Method loadSnapshotMethod = QueueStore.class.getDeclaredMethod("loadSnapshot");
    loadSnapshotMethod.setAccessible(true);
    loadSnapshotMethod.invoke(store);

    // Verify no queues were created
    assertTrue(store.getAllQueues().isEmpty(), "No queues should be created from empty file");
  }

  /**
   * Verifies that loadSnapshot handles missing snapshot file gracefully.
   */
  @Test
  public void testLoadSnapshotWithMissingFile() throws Exception {
    // Ensure the file doesn't exist
    new File(SNAPSHOT_FILE).delete();

    // Load snapshot - should not throw
    Method loadSnapshotMethod = QueueStore.class.getDeclaredMethod("loadSnapshot");
    loadSnapshotMethod.setAccessible(true);
    loadSnapshotMethod.invoke(store);

    // Verify no queues were created
    assertTrue(store.getAllQueues().isEmpty(), "No queues should be created when file is missing");
  }

  /**
   * Verifies that loadSnapshot handles malformed JSON gracefully.
   */
  @Test
  public void testLoadSnapshotWithMalformedJson() throws Exception {
    // Write malformed JSON
    Files.writeString(new File(SNAPSHOT_FILE).toPath(), "{ invalid json }", StandardCharsets.UTF_8);

    // Load snapshot - should not throw
    Method loadSnapshotMethod = QueueStore.class.getDeclaredMethod("loadSnapshot");
    loadSnapshotMethod.setAccessible(true);
    loadSnapshotMethod.invoke(store);

    // Verify no queues were created
    assertTrue(store.getAllQueues().isEmpty(), "No queues should be created from malformed JSON");
  }

  /**
   * Verifies that saveSnapshot handles queues with multiple tasks at different priorities.
   */
  @Test
  public void testSaveSnapshotWithMultipleTasks() throws Exception {
    Queue queue = store.createQueue("PriorityQueue");

    // Add tasks with different priorities and statuses
    Task highPriority = new Task("high priority task", 1);
    Task mediumPriority = new Task("medium priority task", 5);
    Task lowPriority = new Task("low priority task", 10);
    lowPriority.setStatus(Task.TaskStatus.IN_PROGRESS);

    queue.enqueue(highPriority);
    queue.enqueue(mediumPriority);
    queue.enqueue(lowPriority);

    final UUID queueId = queue.getId();

    store.saveSnapshot();

    // Clear and reload
    store.clearAll();
    Method loadSnapshotMethod = QueueStore.class.getDeclaredMethod("loadSnapshot");
    loadSnapshotMethod.setAccessible(true);
    loadSnapshotMethod.invoke(store);

    // Verify all tasks were restored
    Queue restoredQueue = store.getQueue(queueId);
    assertNotNull(restoredQueue);
    assertEquals(3, restoredQueue.getTaskCount(), "All 3 tasks should be restored");
  }
}