/**
 * Unit tests for the Queue class.
 * Note: AI assistance was used to review test coverage and suggest additional edge cases.
 *
 * <p>EQUIVALENCE PARTITIONS:
 *
 * <p>Queue(String name):
 * - Valid: non-null, non-empty string -> testQueueEmptyInitialization
 * - Invalid: null name -> testQueueConstructorWithNullName
 * - Invalid: empty string -> testQueueConstructorWithEmptyName
 *
 * <p>Queue(String name, UUID id):
 * - Valid: non-null name and id -> testQueueConstructorWithUuid
 * - Invalid: null id -> testQueueConstructorWithNullUuid
 *
 * <p>enqueue(Task):
 * - Valid: non-null task -> testEnqueueTask
 * - Invalid: null task -> testEnqueueNullTask
 *
 * <p>dequeue():
 * - Valid: non-empty queue -> testDequeuePriorityOrder
 * - Boundary: empty queue -> testDequeueEmptyQueue
 *
 * <p>addResult(Result):
 * - Valid: result with valid taskId -> testAddResult
 * - Invalid: null result -> testAddNullResult
 * - Invalid: result with null taskId -> testAddResultWithNullTaskxId
 *
 * <p>getResult(UUID):
 * - Valid: existing taskId -> testAddResult
 * - Invalid: non-existent taskId -> testGetResultNonexistent
 *
 * <p>getAllTasks():
 * - Valid: queue with tasks -> testGetAllTasks
 * - Boundary: empty queue -> testGetAllTasksEmpty
 *
 * <p>getAllResults():
 * - Valid: queue with results -> testGetAllResults
 * - Boundary: empty queue -> (MISSING TEST)
 */

package dev.coms4156.project.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.server.model.Queue;
import dev.coms4156.project.server.model.Result;
import dev.coms4156.project.server.model.Task;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This class contains the unit tests for the Queue class.
 */
public class QueueUnitTests {

  private Queue queue;

  /**
   * Sets up a queue instance before each test case.
   */
  @BeforeEach
  void setUp() {
    queue = new Queue("Test Queue");
  }

  /**
  * Verifies that a new Queue is properly initialized.
  */
  @Test
  void testQueueEmptyInitialization() {
    assertNotNull(queue.getId());
    assertEquals("Test Queue", queue.getName());
    assertEquals(0, queue.getTaskCount());
    assertEquals(0, queue.getResultCount());
    assertFalse(queue.hasPendingTasks());
  }
  /**
  * Verifies that attempting to enqueue a null Task fails gracefully.
  */

  @Test
  void testEnqueueNullTask() {
    assertFalse(queue.enqueue(null));
  }

  /**
  * Verifies that attempting to dequeue to the an empty queue returns null.
  */
  @Test
  void testDequeueEmptyQueue() {
    assertNull(queue.dequeue(), "Dequeue should return null on an empty queue");
  }

  /**
  * Verifies that tasks are dequeued based on priority.
  */
  @Test
  void testDequeuePriorityOrder() {
    Task low = new Task("Low", 5);
    Task high = new Task("High", 1);
    queue.enqueue(low);
    queue.enqueue(high);

    Task first = queue.dequeue();
    assertEquals(high, first, "Highest priority task should be dequeued first");
  }


  /**
  * Verifies that a valid Task can be enqueued into the Queue successfully.
  * Ensures the task count increases and that the queue correctly reports having pending tasks.
  */
  @Test
  void testEnqueueTask() {
    Task task = new Task("Print 'Hello'", 1);
    assertTrue(queue.enqueue(task));
    assertEquals(1, queue.getTaskCount());
    assertTrue(queue.hasPendingTasks());
  }

  /**
  * Verifies that multiple tasks can be enqueued.
  */
  @Test
  void testEnqueueMultipleTasks() {
    queue.enqueue(new Task("A", 3));
    queue.enqueue(new Task("B", 2));
    queue.enqueue(new Task("C", 1));
    assertEquals(3, queue.getTaskCount());
  }

  /**
  * Checks that adding a null Result is handled safely.
  * Meaning the queue should not accept null results and
  * retrieving a result for a random ID should return null.
  */
  @Test
  void testAddNullResult() {
    UUID taskId = UUID.randomUUID();
    assertFalse(queue.addResult(null));
    assertNull(queue.getResult(taskId));
  }

  /**
  * Verifies that a valid Result can be added and retrieved from the Queue.
  */
  @Test
  void testAddResult() {
    UUID taskId = UUID.randomUUID();
    Result result = new Result(taskId, "Task completed successfully", Result.ResultStatus.SUCCESS);
    // Add result to queue and verify it was success
    assertTrue(queue.addResult(result));
    // Get result from queue and verify it matches the one added
    assertEquals(result, queue.getResult(taskId));
  }

  /**
  * Verifies that adding a result with an invalid null taskId returns false.
  */
  @Test
  void testAddResultWithNullTaskxId() {
    Result invalid = new Result(null, "output", Result.ResultStatus.SUCCESS);
    assertFalse(queue.addResult(invalid));
  }

  /**
  * Verifies that attempting to access a result from a non existent ID returns null.
  */
  @Test
  void testGetResultNonexistent() {
    assertNull(queue.getResult(UUID.randomUUID()));
  }

  /**
  * Verifies that there aren't any pending tassk after dequeueing all of them from the queue.
  */
  @Test
  void testHasPendingTasksAfterDequeue() {
    Task task = new Task("Test", 1);
    queue.enqueue(task);
    queue.dequeue();
    assertFalse(queue.hasPendingTasks());
  }

  /**
  * Verifies that tasks are being dequeued in the right prioirity order.
  */
  @Test
  void testMultipleDequeuesPriorityOrder() {
    Task t1 = new Task("T1", 5);
    Task t2 = new Task("T2", 2);
    Task t3 = new Task("T3", 1);

    queue.enqueue(t1);
    queue.enqueue(t2);
    queue.enqueue(t3);

    assertEquals(t3, queue.dequeue());
    assertEquals(t2, queue.dequeue()); 
    assertEquals(t1, queue.dequeue()); 
  }

  /**
  * Verifies that it successfully overrides a result with the same id.
  */
  @Test
  void testOverwriteResult() {
    UUID id = UUID.randomUUID();
    Result r1 = new Result(id, "First", Result.ResultStatus.SUCCESS);
    Result r2 = new Result(id, "Second", Result.ResultStatus.SUCCESS);

    queue.addResult(r1);
    queue.addResult(r2);

    assertEquals("Second", queue.getResult(id).getOutput());
  }

  /**
  * Verifies that tasks with the same priority can enqueued and dequeue returns both.
  */
  @Test
  void testEnqueueSamePriorityTasks() {
    Task t1 = new Task("Same1", 1);
    Task t2 = new Task("Same2", 1);

    queue.enqueue(t1);
    queue.enqueue(t2);

    Task first = queue.dequeue();
    Task second = queue.dequeue();

    assertNotNull(first);
    assertNotNull(second);
    assertTrue(first == t1 || first == t2);
    assertTrue(second == t1 || second == t2);
  }

  /**
   * Tests the Queue constructor when there's a null name.
   */
  @Test
  void testQueueConstructorWithNullName() {
    Queue q = new Queue(null);
    assertNotNull(q.getId());
    assertNull(q.getName());
  }

  /**
   * Tests the Queue constructor with an empty name.
   */
  @Test
  void testQueueConstructorWithEmptyName() {
    Queue q = new Queue("");
    assertNotNull(q.getId());
    assertEquals("", q.getName());
  }

  /**
   * Tests the Queue constructor with a UUID.
   */
  @Test
  void testQueueConstructorWithUuid() {
    UUID customId = UUID.randomUUID();
    Queue q = new Queue("Test", customId);
    assertEquals(customId, q.getId());
    assertEquals("Test", q.getName());
  }

  /**
   * Tests the Queue constructor with a null UUID.
   */
  @Test
  void testQueueConstructorWithNullUuid() {
    Queue q = new Queue("Test", null);
    assertNull(q.getId());
    assertEquals("Test", q.getName());
  }

  /**
   * Tests retrieving all tasks.
   */
  @Test
  void testGetAllTasks() {
    Task t1 = new Task("A", 1);
    Task t2 = new Task("B", 2);
    queue.enqueue(t1);
    queue.enqueue(t2);
    assertEquals(2, queue.getAllTasks().size());
  }

  /**
   * Tests retrieving all tasks when the queue is empty.
   */
  @Test
  void testGetAllTasksEmpty() {
    assertEquals(0, queue.getAllTasks().size());
  }

  /**
   * Tests retrieving all results.
   */
  @Test
  void testGetAllResults() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    queue.addResult(new Result(id1, "out1", Result.ResultStatus.SUCCESS));
    queue.addResult(new Result(id2, "out2", Result.ResultStatus.SUCCESS));
    assertEquals(2, queue.getAllResults().size());
  }

  /**
   * Tests retrieving all results when the queue is empty.
   */
  @Test
  void testGetAllResultsEmpty() {
    assertEquals(0, queue.getAllResults().size());
  }
}