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
   * Sets up a queue before each test case.
   */
  @BeforeEach
  void setUp() {
    queue = new Queue("Test Queue");
  }

  @Test
  void testQueueEmptyInitialization() {
    assertNotNull(queue.getId());
    assertEquals("Test Queue", queue.getName());
    assertEquals(0, queue.getTaskCount());
    assertEquals(0, queue.getResultCount());
    assertFalse(queue.hasPendingTasks());
  }

  @Test
  void testEnqueueNullTask() {
    assertEquals(false, queue.enqueue(null));
  }

  @Test
  void testEnqueueTask() {
    Task task = new Task("Print 'Hello'", 1);
    assertEquals(true, queue.enqueue(task));
    assertEquals(1, queue.getTaskCount());
    assertFalse(queue.hasPendingTasks() == false); // sanity check
  }

  @Test
  void testAddNullResult() {
    UUID taskId = UUID.randomUUID();
    assertFalse(queue.addResult(null));
    assertNull(queue.getResult(taskId));
  }

  @Test
  void testAddResult() {
    UUID taskId = UUID.randomUUID();
    Result result = new Result(taskId, "Task completed successfully", Result.ResultStatus.SUCCESS);
    assertTrue(queue.addResult(result));
    assertEquals(result, queue.getResult(taskId));
  }


}