package dev.coms4156.project.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.server.model.Queue;
import dev.coms4156.project.server.model.QueueStore;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This class contains the unit tests for the QueueStore class.
 */
public class QueueStoreUnitTests {
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
}