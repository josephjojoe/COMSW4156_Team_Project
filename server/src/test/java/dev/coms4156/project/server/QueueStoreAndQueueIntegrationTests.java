package dev.coms4156.project.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.server.model.Queue;
import dev.coms4156.project.server.model.QueueStore;
import dev.coms4156.project.server.model.Result;
import dev.coms4156.project.server.model.Result.ResultStatus;
import dev.coms4156.project.server.model.Task;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * Internal integration tests that validate interactions between: QueueStore <-> Queue
 *
 * <p>These tests verify that QueueStore correctly manages, persists, and returns
 * the same live Queue instances, and that all Queue state (including Task and
 * Result data) remains consistent across multiple operations and lookups.
 */
public class QueueStoreAndQueueIntegrationTests {

  private QueueStore store;

  @BeforeEach
  void setup() {
    store = QueueStore.getInstance();
    store.clearAll();
  }

  /**
    * Ensures QueueStore correctly stores and retrieves Queue instances,
    * and that modifications to stored Queue objects are reflected.
    */
  @Test
  void storedQueueReflectsMutations() {
    Queue q = store.createQueue("StoredQueueTest");
    UUID id = q.getId();

    Task t = new Task("params", 1);
    q.enqueue(t);  // mutate queue BEFORE re-fetching from store

    Queue retrieved = store.getQueue(id);
    assertNotNull(retrieved);
    assertEquals(1, retrieved.getTaskCount());
    assertTrue(retrieved.hasPendingTasks());
  }

  /**
    * Verifies that QueueStore maintains isolation between multiple queues
    * and does not mix task state across them.
    */
  @Test
  void queueStoreMaintainsIsolationAcrossMultipleQueues() {
    Queue q1 = store.createQueue("A");
    Queue q2 = store.createQueue("B");
    Task t1 = new Task("x", 5);
    Task t2 = new Task("y", 1);

    q1.enqueue(t1);
    q2.enqueue(t2);

    assertEquals(1, store.getQueue(q1.getId()).getTaskCount());
    assertEquals(1, store.getQueue(q2.getId()).getTaskCount());

    assertNotEquals(
            store.getQueue(q1.getId()).dequeue().getId(),
            store.getQueue(q2.getId()).dequeue().getId()
    );
  }

  /**
    * Ensures the QueueStore preserves mutable queue state across
    * multiple read operations.
    */
  @Test
  void queueStatePersistsAcrossRepeatedStoreReads() {
    Queue q = store.createQueue("Persistent");
    UUID id = q.getId();
    q.enqueue(new Task("hello", 3));
    q.enqueue(new Task("world", 1));
    Queue firstRead = store.getQueue(id);
    Queue secondRead = store.getQueue(id);

    // store returns same reference
    assertSame(firstRead, secondRead); 
    assertEquals(2, firstRead.getTaskCount());
    assertEquals(2, secondRead.getTaskCount());
  }

  /**
    * Confirms that removing a queue from QueueStore fully deletes it
    * and does not leave stale state.
    */
  @Test
  void removingQueueFullyDeletesState() {
    Queue q = store.createQueue("DeleteMe");
    UUID id = q.getId();
    q.enqueue(new Task("x", 1));
    assertEquals(1, q.getTaskCount());

    boolean removed = store.removeQueue(id);
    assertTrue(removed);
    assertNull(store.getQueue(id));
  }

  /**
    * Tests that results added to a Queue remain accessible after
    * retrieving that Queue from QueueStore.
    */
  @Test
  void resultsPersistInQueueStoredInQueueStore() {
    Queue q = store.createQueue("ResultTest");
    UUID qid = q.getId();
    Task t = new Task("x", 1);
    q.enqueue(t);
    q.dequeue();

    Result r = new Result(t.getId(), "done", ResultStatus.SUCCESS);
    q.addResult(r);

    Queue stored = store.getQueue(qid);

    Result retrieved = stored.getResult(t.getId());
    assertNotNull(retrieved);
    assertEquals("done", retrieved.getOutput());
  }

  /**
    * Checks consistent behavior when storing multiple operations and
    * repeated enqueue/dequeue cycles directly through Queue reference.
    */
  @Test
  void repeatedOperationsPersistAcrossQueueStore() {
    Queue q = store.createQueue("CycleTest");
    Task t1 = new Task("x", 3);
    Task t2 = new Task("y", 1);
    q.enqueue(t1);
    q.enqueue(t2);
    Task first = q.dequeue();
    Task second = q.dequeue();

    assertNotNull(first);
    assertNotNull(second);
    assertEquals(0, q.getTaskCount());
    UUID id = q.getId();

    Queue reread = store.getQueue(id);
    assertEquals(0, reread.getTaskCount());
    assertFalse(reread.hasPendingTasks());
  }

  /**
    * Verifies that QueueStore returns the SAME Queue object instance,
    * not a copy, and that external modifications persist.
    */
  @Test
  void queueStoreReturnsSameObjectReference() {
    Queue q = store.createQueue("SameReferenceTest");
    UUID id = q.getId();
    Queue ref1 = store.getQueue(id);
    Queue ref2 = store.getQueue(id);

    assertSame(ref1, ref2);
    ref1.enqueue(new Task("x", 1));
    assertEquals(1, ref2.getTaskCount()); 
  }
}
