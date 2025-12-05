/**
 * Integration tests focusing only on Queue <-> Task interactions.
 * These tests exercise the integration boundary where Queue manages, orders,
 * and returns Task instances through enqueue and dequeue operations.
 * Note: AI assistance was used to review test coverage and suggest additional edge cases.
 *
 * <p>EQUIVALENCE PARTITIONS:
 *
 * <p>Queue.enqueue(Task) + Queue.dequeue():
 * - Valid: tasks with different priorities -> tasksAreDequeuedInCompareToOrder
 * - Boundary: tasks with same priority -> tasksWithSamePriorityAreAllDequeuedButOrderIsNotGuaranteed
 * - Valid: same task reference -> dequeueReturnsSameTaskReference
 */

package dev.coms4156.project.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.server.model.Queue;
import dev.coms4156.project.server.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * Integration tests focusing only on Queue <-> Task interactions.
 * These tests exercise the integration boundary where Queue manages, orders,
 * and returns Task instances through enqueue and dequeue operations.
 */
public class QueueAndTaskIntegrationTests {

  private Queue queue;

  @BeforeEach
  void setup() {
    queue = new Queue("QueueTaskIntegration");
  }

  /**
    * Verifies that the Queue dequeues tasks according to the ordering
    * defined by Task.compareTo().
    */
  @Test
  void tasksAreDequeuedInCompareToOrder() {
    Task t1 = new Task("A", 5);
    Task t2 = new Task("B", 1);
    Task t3 = new Task("C", 3);
    queue.enqueue(t1);
    queue.enqueue(t2);
    queue.enqueue(t3);
    Task first = queue.dequeue();
    Task second = queue.dequeue();
    Task third = queue.dequeue();

    assertTrue(first.compareTo(second) <= 0, "first <= second");
    assertTrue(second.compareTo(third) <= 0, "second <= third");
  }

  /**
    * Verifies that when multiple tasks share the same priority,
    * the Queue returns all of them but does not guarantee the order
    * of retrieval.
    */
  @Test
  void tasksWithSamePriorityAreAllDequeuedButOrderIsNotGuaranteed() {
    Task t1 = new Task("A", 5);
    Task t2 = new Task("B", 5);
    Task t3 = new Task("C", 5);
    queue.enqueue(t1);
    queue.enqueue(t2);
    queue.enqueue(t3);
    Task d1 = queue.dequeue();
    Task d2 = queue.dequeue();
    Task d3 = queue.dequeue();
    assertNotNull(d1);
    assertNotNull(d2);
    assertNotNull(d3);

    // Ensure all 3 tasks were returned
    assertNotEquals(d1.getId(), d2.getId());
    assertNotEquals(d2.getId(), d3.getId());
    assertNotEquals(d1.getId(), d3.getId());
  }

  /**
    * Verifies that dequeue() returns the same Task instance
    * that was added to the queue.
    */
  @Test
  void dequeueReturnsSameTaskReference() {
    Task t = new Task("test", 2);

    queue.enqueue(t);
    Task dequeued = queue.dequeue();

    assertSame(t, dequeued);
  }

  /**
    * Verifies that dequeue() returns the task without modifying its status.
    */
  @Test
  void dequeueDoesNotChangeTaskStatus() {
    Task t = new Task("check", 1);

    assertEquals(Task.TaskStatus.PENDING, t.getStatus());
    queue.enqueue(t);

    Task out = queue.dequeue();
    assertEquals(Task.TaskStatus.PENDING, out.getStatus());
  }

  /**
    * Verifies basic queue consistency where enqueues and dequeues behave correctly.
    */
  @Test
  void queueConsistentAcrossMultipleOperations() {
    Task t1 = new Task("A", 2);
    Task t2 = new Task("B", 1);
    queue.enqueue(t1);
    queue.enqueue(t2);
    Task d1 = queue.dequeue();
    Task d2 = queue.dequeue();

    assertNotNull(d1);
    assertNotNull(d2);

    assertEquals(0, queue.getTaskCount());
    assertFalse(queue.hasPendingTasks());
  }
}
