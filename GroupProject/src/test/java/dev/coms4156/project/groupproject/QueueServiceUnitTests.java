package dev.coms4156.project.groupproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.groupproject.model.Queue;
import dev.coms4156.project.groupproject.model.Result;
import dev.coms4156.project.groupproject.model.Task;
import dev.coms4156.project.groupproject.service.QueueService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * This class contains the unit tests for the QueueService class.
 */
@SpringBootTest
public class QueueServiceUnitTests {

  private QueueService queueService;

  /**
   * Sets up a fresh QueueService instance before each test case.
   * This ensures test isolation by providing a clean service instance
   * for each test method.
   */
  @BeforeEach
  void setUp() {
    queueService = new QueueService();
  }

  /**
   * Tests that createQueue successfully creates a queue with a valid name.
   * Verifies that the returned queue is not null and has the correct name.
   */
  @Test
  void testCreateQueueWithValidName() {
    Queue queue = queueService.createQueue("Queue1");
    assertNotNull(queue);
    assertEquals("Queue1", queue.getName());
  }

  /**
   * Tests that getQueue retrieves an existing queue by its ID.
   * Creates a queue and then retrieves it to verify that the same
   * queue instance is returned.
   */
  @Test
  void testGetQueueExists() {
    Queue queue = queueService.createQueue("Queue1");
    Queue queue2 = queueService.getQueue(queue.getId());
    assertNotNull(queue2);
    assertEquals(queue, queue2);
  }

  /**
   * Tests that getQueue returns null when attempting to retrieve a non-existent queue.
   * Uses a random UUID that has not been assigned to any queue to verify
   * proper handling of missing queues.
   */
  @Test
  void testGetQueueNotFound() {
    Queue queue = queueService.getQueue(UUID.randomUUID());
    assertNull(queue);
  }

  /**
   * Tests that queueExists returns true for an existing queue.
   * Creates a queue and verifies that the existence check returns true.
   */
  @Test
  void testQueueExistsReturnsTrue() {
    Queue queue = queueService.createQueue("Queue1");
    assertTrue(queueService.queueExists(queue.getId()));
  }

  /**
   * Tests that queueExists returns false for a non-existent queue.
   * Uses a random UUID to verify that the existence check properly
   * returns false when the queue does not exist.
   */
  @Test
  void testQueueExistsReturnsFalse() {
    assertFalse(queueService.queueExists(UUID.randomUUID()));
  }

  /**
   * Tests that getResult successfully retrieves a submitted result.
   * Creates a queue, enqueues a task, submits a result for that task,
   * and verifies that the result can be retrieved with the correct task ID.
   */
  @Test
  void testGetResult() {
    // Create queue and enqueue a task
    Queue queue = queueService.createQueue("Queue1");
    Task task = new Task("Test task", 1);
    queueService.enqueueTask(queue.getId(), task);

    // Submit a result for the task
    Result result = new Result(task.getId(), "Completed", Result.ResultStatus.SUCCESS);
    queueService.submitResult(queue.getId(), result);

    // Retrieve and verify the result
    Result retrieved = queueService.getResult(queue.getId(), task.getId());
    assertNotNull(retrieved);
    assertEquals(task.getId(), retrieved.getTaskId());
  }

  /**
   * Tests that getResult returns null when attempting to retrieve a non-existent result.
   * Creates a queue but does not submit any results, then attempts to retrieve
   * a result with a random task ID to verify null is returned.
   */
  @Test
  void testGetResultNotFound() {
    Queue queue = queueService.createQueue("Queue1");
    UUID uuid = UUID.randomUUID();
    Result result = queueService.getResult(queue.getId(), uuid);
    assertNull(result);
  }

  /**
   * Tests that submitResult successfully adds a result to the queue.
   * Creates a queue, enqueues a task, submits a result, and verifies
   * that the queue's result count increases to 1.
   */
  @Test
  void testSubmitResult() {
    Queue queue = queueService.createQueue("Queue1");
    Task task = new Task("Test task", 1);
    queueService.enqueueTask(queue.getId(), task);
    Result result = new Result(task.getId(), "Success", Result.ResultStatus.SUCCESS);
    queueService.submitResult(queue.getId(), result);
    assertEquals(1, queue.getResultCount());
  }

  /**
   * Invalid-path tests exercising QueueService exception branches
   * (null/blank inputs and nonexistent IDs).
   */
  @Test
  void testCreateQueueWithNullNameThrows() {
    assertThrows(IllegalArgumentException.class, () -> queueService.createQueue(null));
  }

  @Test
  void testCreateQueueWithBlankNameThrows() {
    assertThrows(IllegalArgumentException.class, () -> queueService.createQueue("  \t\n"));
  }

  @Test
  void testGetQueueNullIdThrows() {
    assertThrows(IllegalArgumentException.class, () -> queueService.getQueue(null));
  }

  @Test
  void testQueueExistsNullIdThrows() {
    assertThrows(IllegalArgumentException.class, () -> queueService.queueExists(null));
  }

  @Test
  void testEnqueueTaskNullQueueIdThrows() {
    Task task = new Task("p", 1);
    assertThrows(IllegalArgumentException.class, () -> queueService.enqueueTask(null, task));
  }

  @Test
  void testEnqueueTaskNullTaskThrows() {
    UUID randomId = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> queueService.enqueueTask(randomId, null));
  }

  @Test
  void testEnqueueTaskNonexistentQueueThrows() {
    UUID randomId = UUID.randomUUID();
    Task task = new Task("p", 1);
    assertThrows(IllegalStateException.class, () -> queueService.enqueueTask(randomId, task));
  }

  @Test
  void testDequeueTaskNullQueueIdThrows() {
    assertThrows(IllegalArgumentException.class, () -> queueService.dequeueTask(null));
  }

  @Test
  void testDequeueTaskNonexistentQueueThrows() {
    UUID randomId = UUID.randomUUID();
    assertThrows(IllegalStateException.class, () -> queueService.dequeueTask(randomId));
  }

  @Test
  void testSubmitResultNullQueueIdThrows() {
    Result result = new Result(UUID.randomUUID(), "ok", Result.ResultStatus.SUCCESS);
    assertThrows(IllegalArgumentException.class, () -> queueService.submitResult(null, result));
  }

  @Test
  void testSubmitResultNullResultThrows() {
    UUID randomId = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> queueService.submitResult(randomId, null));
  }

  @Test
  void testSubmitResultNonexistentQueueThrows() {
    UUID randomId = UUID.randomUUID();
    Result result = new Result(UUID.randomUUID(), "ok", Result.ResultStatus.SUCCESS);
    assertThrows(IllegalStateException.class, () -> queueService.submitResult(randomId, result));
  }

  @Test
  void testGetResultNullQueueIdThrows() {
    assertThrows(IllegalArgumentException.class, () ->
          queueService.getResult(null, UUID.randomUUID()));
  }

  @Test
  void testGetResultNullTaskIdThrows() {
    UUID randomId = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> queueService.getResult(randomId, null));
  }

  @Test
  void testGetResultNonexistentQueueThrows() {
    UUID randomId = UUID.randomUUID();
    assertThrows(IllegalStateException.class, () ->
          queueService.getResult(randomId, UUID.randomUUID()));
  }
}