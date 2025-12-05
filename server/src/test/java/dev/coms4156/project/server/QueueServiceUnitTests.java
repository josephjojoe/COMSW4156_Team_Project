/**
 * Unit tests for QueueService.
 * Note: AI assistance was used to review test coverage and suggest additional edge cases.
 *
 * <p>EQUIVALENCE PARTITIONS:
 *
 * <p>createQueue(String name):
 * - Valid: non-blank name -> testCreateQueueWithValidName
 * - Invalid: null name -> testCreateQueueWithNullNameThrows
 * - Invalid: blank name -> testCreateQueueWithBlankNameThrows
 *
 * <p>enqueueTask(UUID queueId, Task task):
 * - Valid: existing queue, valid task -> testEnqueueTaskWithValidInput
 * - Invalid: null queueId -> testEnqueueTaskNullQueueIdThrows
 * - Invalid: null task -> testEnqueueTaskNullTaskThrows
 * - Invalid: non-existent queue -> testEnqueueTaskNonexistentQueueThrows
 *
 * <p>dequeueTask(UUID queueId):
 * - Valid: existing queue with tasks -> testDequeueTaskWithValidInput
 * - Boundary: existing empty queue -> testDequeueTaskFromEmptyQueue
 * - Invalid: null queueId -> testDequeueTaskNullQueueIdThrows
 * - Invalid: non-existent queue -> testDequeueTaskNonexistentQueueThrows
 *
 * <p>submitResult(UUID queueId, Result result):
 * - Valid: existing queue, valid result -> testSubmitResult
 * - Invalid: null queueId -> testSubmitResultNullQueueIdThrows
 * - Invalid: null result -> testSubmitResultNullResultThrows
 * - Invalid: non-existent queue -> testSubmitResultNonexistentQueueThrows
 *
 * <p>getResult(UUID queueId, UUID taskId):
 * - Valid: existing result -> testGetResult
 * - Boundary: non-existent result -> testGetResultNotFound
 * - Invalid: null queueId -> testGetResultNullQueueIdThrows
 * - Invalid: null taskId -> testGetResultNullTaskIdThrows
 * - Invalid: non-existent queue -> testGetResultNonexistentQueueThrows
 *
 * <p>getQueue(UUID queueId):
 * - Valid: existing queue -> testGetQueueExists
 * - Boundary: non-existent queue -> testGetQueueNotFound
 * - Invalid: null queueId -> testGetQueueNullIdThrows
 *
 * <p>queueExists(UUID queueId):
 * - Valid: existing queue -> testQueueExistsReturnsTrue
 * - Boundary: non-existent queue -> testQueueExistsReturnsFalse
 * - Invalid: null queueId -> testQueueExistsNullIdThrows
 *
 * <p>clearAll():
 * - Valid: store with queues -> testClearAll
 * - Boundary: empty store -> testClearAllEmpty
 *
 * <p>getAllQueueCount():
 * - Valid: store with queues -> testGetAllQueueCount
 * - Boundary: empty store -> testGetAllQueueCountEmpty
 */

package dev.coms4156.project.server;

import dev.coms4156.project.server.model.Queue;
import dev.coms4156.project.server.model.Result;
import dev.coms4156.project.server.model.Result.ResultStatus;
import dev.coms4156.project.server.model.Task;
import dev.coms4156.project.server.service.QueueService;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueueService.
 */
@SpringBootTest
public class QueueServiceUnitTests {
  private QueueService queueService;

  @BeforeEach
  void setUp() {
    this.queueService = new QueueService();
  }

  @Test
  void testCreateQueueWithValidName() {
    Queue queue = this.queueService.createQueue("Queue1");
    Assertions.assertNotNull(queue);
    Assertions.assertEquals("Queue1", queue.getName());
  }

  @Test
  void testGetQueueExists() {
    Queue queue = this.queueService.createQueue("Queue1");
    UUID queueId = queue.getId();
    Queue queue2 = this.queueService.getQueue(queueId);
    Assertions.assertNotNull(queue2);
    Assertions.assertEquals(queue, queue2);
  }

  @Test
  void testGetQueueNotFound() {
    Queue queue = this.queueService.getQueue(UUID.randomUUID());
    Assertions.assertNull(queue);
  }

  @Test
  void testQueueExistsReturnsTrue() {
    Queue queue = this.queueService.createQueue("Queue1");
    UUID queueId = queue.getId();
    Assertions.assertTrue(this.queueService.queueExists(queueId));
  }

  @Test
  void testQueueExistsReturnsFalse() {
    Assertions.assertFalse(this.queueService.queueExists(UUID.randomUUID()));
  }

  @Test
  void testGetResult() {
    Queue queue = this.queueService.createQueue("Queue1");
    Task task = new Task("Test task", 1);
    UUID queueId = queue.getId();
    this.queueService.enqueueTask(queueId, task);
    Result result = new Result(task.getId(), "Completed", ResultStatus.SUCCESS);
    this.queueService.submitResult(queueId, result);
    UUID taskId = task.getId();
    Result retrieved = this.queueService.getResult(queueId, taskId);
    Assertions.assertNotNull(retrieved);
    Assertions.assertEquals(taskId, retrieved.getTaskId());
  }

  @Test
  void testGetResultNotFound() {
    Queue queue = this.queueService.createQueue("Queue1");
    UUID queueId = queue.getId();
    UUID uuid = UUID.randomUUID();
    Result result = this.queueService.getResult(queueId, uuid);
    Assertions.assertNull(result);
  }

  @Test
  void testSubmitResult() {
    Queue queue = this.queueService.createQueue("Queue1");
    Task task = new Task("Test task", 1);
    UUID queueId = queue.getId();
    this.queueService.enqueueTask(queueId, task);
    Result result = new Result(task.getId(), "Success", ResultStatus.SUCCESS);
    this.queueService.submitResult(queueId, result);
    Assertions.assertEquals(1, queue.getResultCount());
  }

  @Test
  void testCreateQueueWithNullNameThrows() {
    Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> this.queueService.createQueue(null)
    );
  }

  @Test
  void testCreateQueueWithBlankNameThrows() {
    Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> this.queueService.createQueue("  \t\n")
    );
  }

  @Test
  void testGetQueueNullIdThrows() {
    Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> this.queueService.getQueue( null)
    );
  }

  @Test
  void testQueueExistsNullIdThrows() {
    Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> this.queueService.queueExists( null)
    );
  }

  @Test
  void testEnqueueTaskNullQueueIdThrows() {
    Task task = new Task("p", 1);
    Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> this.queueService.enqueueTask( null, task)
    );
  }

  @Test
  void testEnqueueTaskNullTaskThrows() {
    UUID randomId = UUID.randomUUID();
    Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> this.queueService.enqueueTask(randomId,  null)
    );
  }

  @Test
  void testEnqueueTaskNonexistentQueueThrows() {
    UUID randomId = UUID.randomUUID();
    Task task = new Task("p", 1);
    Assertions.assertThrows(
          IllegalStateException.class,
          () -> this.queueService.enqueueTask(randomId, task)
    );
  }

  @Test
  void testDequeueTaskNullQueueIdThrows() {
    Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> this.queueService.dequeueTask( null)
    );
  }

  @Test
  void testDequeueTaskNonexistentQueueThrows() {
    UUID randomId = UUID.randomUUID();
    Assertions.assertThrows(
          IllegalStateException.class,
          () -> this.queueService.dequeueTask(randomId)
    );
  }

  @Test
  void testSubmitResultNullQueueIdThrows() {
    Result result = new Result(
          UUID.randomUUID(),
          "ok",
          ResultStatus.SUCCESS
    );
    Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> this.queueService.submitResult( null, result)
    );
  }

  @Test
  void testSubmitResultNullResultThrows() {
    UUID randomId = UUID.randomUUID();
    Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> this.queueService.submitResult(randomId,  null)
    );
  }

  /**
   * Tests submitting a reuslt with a nonexistent queue.
   */
  @Test
  void testSubmitResultNonexistentQueueThrows() {
    UUID randomId = UUID.randomUUID();
    Result result = new Result(
          UUID.randomUUID(),
          "ok",
          ResultStatus.SUCCESS
    );
    Assertions.assertThrows(
          IllegalStateException.class,
          () -> this.queueService.submitResult(randomId, result)
    );
  }

  /**
   * Tests getting a result with a null queue ID.
   */
  @Test
  void testGetResultNullQueueIdThrows() {
    UUID taskId = UUID.randomUUID();
    Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> this.queueService.getResult( null, taskId)
    );
  }

  /**
   * Tests getting a result with a null task ID.
   */
  @Test
  void testGetResultNullTaskIdThrows() {
    UUID randomId = UUID.randomUUID();
    Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> this.queueService.getResult(randomId,  null)
    );
  }

  /**
   * Tests getResult with a nonexistent queue.
   */
  @Test
  void testGetResultNonexistentQueueThrows() {
    UUID randomId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    Assertions.assertThrows(
          IllegalStateException.class,
          () -> this.queueService.getResult(randomId, taskId)
    );
  }

  /**
   * Tests enqueueing a task with a valid input.
   */
  @Test
  public void testEnqueueTaskWithValidInput() {
    Queue queue = queueService.createQueue("test-queue");
    UUID queueId = queue.getId();
    Task task = new Task("{\"page\": 1}", 1);
    queueService.enqueueTask(queueId, task);
    assertTrue(queue.hasPendingTasks());
    assertEquals(1, queue.getTaskCount());
  }

  /**
   * Tests dequeuing a task with a valid input.
   */
  @Test
  public void testDequeueTaskWithValidInput() {
    Queue queue = queueService.createQueue("test-queue");
    UUID queueId = queue.getId();
    Task task = new Task("{\"page\": 1}", 1);
    queueService.enqueueTask(queueId, task);
    Task dequeuedTask = queueService.dequeueTask(queueId);
    assertNotNull(dequeuedTask);
    assertEquals(task.getId(), dequeuedTask.getId());
    assertEquals("{\"page\": 1}", dequeuedTask.getParams());
    assertEquals(1, dequeuedTask.getPriority());
  }

  /**
   * Tests dequeue from an empty queue.
   */
  @Test
  void testDequeueTaskFromEmptyQueue() {
    Queue queue = queueService.createQueue("Empty");
    Task task = queueService.dequeueTask(queue.getId());
    assertNull(task);
  }

  /**
   * Tests clearing all on a nonempty queue.
   */
  @Test
  void testClearAll() {
    queueService.createQueue("Q1");
    queueService.createQueue("Q2");
    queueService.clearAll();
    assertEquals(0, queueService.getAllQueueCount());
  }

  /**
   * Tests clearing all on an empty queue.
   */
  @Test
  void testClearAllEmpty() {
    queueService.clearAll();
    assertEquals(0, queueService.getAllQueueCount());
  }

  /**
   * Tests the get all queue count functionality where there is a count.
   */
  @Test
  void testGetAllQueueCount() {
    queueService.clearAll();
    queueService.createQueue("Q1");
    queueService.createQueue("Q2");
    assertEquals(2, queueService.getAllQueueCount());
  }

  /**
   * Tests the getAll functionality when the queue count is empty.
   */
  @Test
  void testGetAllQueueCountEmpty() {
    queueService.clearAll();
    assertEquals(0, queueService.getAllQueueCount());
  }

  /** Tests that enqueueTask succeeds when queue accepts multiple tasks. */
  @Test
  void testEnqueueTaskFailsWhenQueueRejectTask() {
    Queue queue = queueService.createQueue("Q");
    queue.enqueue(new Task("t1", 1));
    Task duplicate = new Task("t2", 1);
    queueService.enqueueTask(queue.getId(), duplicate);
    assertEquals(2, queue.getTaskCount());
  }

  /** Tests that submitResult throws when result has null taskId. */
  @Test
  void testSubmitResultWithNullTaskIdInResult() {
    Queue queue = queueService.createQueue("Q");
    Result badResult = new Result(null, "output", Result.ResultStatus.SUCCESS);
    Assertions.assertThrows(IllegalStateException.class,
          () -> queueService.submitResult(queue.getId(), badResult));
  }
}