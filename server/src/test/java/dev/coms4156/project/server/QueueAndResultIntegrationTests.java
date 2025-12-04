package dev.coms4156.project.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.server.model.Queue;
import dev.coms4156.project.server.model.Result;
import dev.coms4156.project.server.model.Result.ResultStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * Internal integration tests verifying the interaction between Queue and Result.
 * These tests ensure that Queue’s internal storage and lookup logic
 * correctly handles Result objects without mocking either class.
 */
public class QueueAndResultIntegrationTests {

  private Queue queue;

  @BeforeEach
  void setup() {
    queue = new Queue("QueueResultIntegration");
  }

  /**
    * Verifies that Queue.addResult stores a Result and that Queue.getResult
    * returns the same instance.
    */
  @Test
  void addAndRetrieveResultSuccessfully() {
    UUID taskId = UUID.randomUUID();
    Result r = new Result(taskId, "done", ResultStatus.SUCCESS);
    assertTrue(queue.addResult(r));
    Result retrieved = queue.getResult(taskId);

    assertNotNull(retrieved);
    assertEquals("done", retrieved.getOutput());
    assertEquals(ResultStatus.SUCCESS, retrieved.getStatus());
    assertSame(r, retrieved);
  }

  /**
    * Ensures Queue returns null when a Result does not exist.
    */
  @Test
  void getResultReturnsNullWhenNoResultExists() {
    UUID randomTaskId = UUID.randomUUID();
    assertNull(queue.getResult(randomTaskId));
  }

  /**
    * Testing that adding a duplicate result
    * always overwrites the previous one and confirms that the
    * latest result replaces any earlier value.
    */
  @Test
  void addingDuplicateResultOverwritesExistingResult() {
    UUID taskId = UUID.randomUUID();
    Result r1 = new Result(taskId, "first", ResultStatus.SUCCESS);
    Result r2 = new Result(taskId, "second", ResultStatus.SUCCESS);

    queue.addResult(r1);
    queue.addResult(r2);

    Result retrieved = queue.getResult(taskId);

    assertNotNull(retrieved);
    assertEquals("second", retrieved.getOutput(), 
        "Queue should overwrite old results with the most recent one.");
  }


  /**
    * Ensures the Queue result count increases correctly.
    */
  @Test
  void resultCountTracksInsertedResults() {
    UUID t1 = UUID.randomUUID();
    UUID t2 = UUID.randomUUID();

    queue.addResult(new Result(t1, "A", ResultStatus.SUCCESS));
    queue.addResult(new Result(t2, "B", ResultStatus.FAILURE));

    assertEquals(2, queue.getResultCount());
  }

  /**
    * Ensures results remain retrievable even after many Queue operations
    * that do not involve results (enqueue/dequeue are not used here since we
    * only want to test Queue <-> Result).
    */
  @Test
  void resultsPersistInQueueInternalState() {
    UUID taskId = UUID.randomUUID();
    Result r = new Result(taskId, "output", ResultStatus.SUCCESS);

    queue.addResult(r);

    assertNotNull(queue.getResult(taskId));
    assertEquals("output", queue.getResult(taskId).getOutput());
  }

  /**
    * Ensures a null Result is rejected.
    */
  @Test
  void addingNullResultFails() {
    assertFalse(queue.addResult(null));
  }
}
