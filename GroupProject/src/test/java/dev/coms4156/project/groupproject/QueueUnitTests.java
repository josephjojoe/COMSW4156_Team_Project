package dev.coms4156.project.groupproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.groupproject.model.Queue;
import dev.coms4156.project.groupproject.model.Result;
import dev.coms4156.project.groupproject.model.Task;
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
    assertEquals(false, queue.enqueue(null));
  }

  /**
  * Verifies that a valid Task can be enqueued into the Queue successfully.
  * Ensures the task count increases and that the queue correctly reports having pending tasks.
  */
  @Test
  void testEnqueueTask() {
    Task task = new Task("Print 'Hello'", 1);
    assertEquals(true, queue.enqueue(task));
    assertEquals(1, queue.getTaskCount());
    assertFalse(queue.hasPendingTasks() == false); // sanity check
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


}