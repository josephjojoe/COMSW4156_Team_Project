//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package dev.coms4156.project.groupproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.groupproject.model.Queue;
import dev.coms4156.project.groupproject.model.Result;
import dev.coms4156.project.groupproject.model.Task;
import dev.coms4156.project.groupproject.model.Result.ResultStatus;
import dev.coms4156.project.groupproject.service.QueueService;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

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
    Queue queue2 = this.queueService.getQueue(queue.getId());
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
    Assertions.assertTrue(this.queueService.queueExists(queue.getId()));
  }

  @Test
  void testQueueExistsReturnsFalse() {
    Assertions.assertFalse(this.queueService.queueExists(UUID.randomUUID()));
  }

  @Test
  void testGetResult() {
    Queue queue = this.queueService.createQueue("Queue1");
    Task task = new Task("Test task", 1);
    this.queueService.enqueueTask(queue.getId(), task);
    Result result = new Result(task.getId(), "Completed", ResultStatus.SUCCESS);
    this.queueService.submitResult(queue.getId(), result);
    Result retrieved = this.queueService.getResult(queue.getId(), task.getId());
    Assertions.assertNotNull(retrieved);
    Assertions.assertEquals(task.getId(), retrieved.getTaskId());
  }

  @Test
  void testGetResultNotFound() {
    Queue queue = this.queueService.createQueue("Queue1");
    UUID uuid = UUID.randomUUID();
    Result result = this.queueService.getResult(queue.getId(), uuid);
    Assertions.assertNull(result);
  }

  @Test
  void testSubmitResult() {
    Queue queue = this.queueService.createQueue("Queue1");
    Task task = new Task("Test task", 1);
    this.queueService.enqueueTask(queue.getId(), task);
    Result result = new Result(task.getId(), "Success", ResultStatus.SUCCESS);
    this.queueService.submitResult(queue.getId(), result);
    Assertions.assertEquals(1, queue.getResultCount());
  }

  @Test
  void testCreateQueueWithNullNameThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> this.queueService.createQueue((String)null));
  }

  @Test
  void testCreateQueueWithBlankNameThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> this.queueService.createQueue("  \t\n"));
  }

  @Test
  void testGetQueueNullIdThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> this.queueService.getQueue((UUID)null));
  }

  @Test
  void testQueueExistsNullIdThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> this.queueService.queueExists((UUID)null));
  }

  @Test
  void testEnqueueTaskNullQueueIdThrows() {
    Task task = new Task("p", 1);
    Assertions.assertThrows(IllegalArgumentException.class, () -> this.queueService.enqueueTask((UUID)null, task));
  }

  @Test
  void testEnqueueTaskNullTaskThrows() {
    UUID randomId = UUID.randomUUID();
    Assertions.assertThrows(IllegalArgumentException.class, () -> this.queueService.enqueueTask(randomId, (Task)null));
  }

  @Test
  void testEnqueueTaskNonexistentQueueThrows() {
    UUID randomId = UUID.randomUUID();
    Task task = new Task("p", 1);
    Assertions.assertThrows(IllegalStateException.class, () -> this.queueService.enqueueTask(randomId, task));
  }

  @Test
  void testDequeueTaskNullQueueIdThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> this.queueService.dequeueTask((UUID)null));
  }

  @Test
  void testDequeueTaskNonexistentQueueThrows() {
    UUID randomId = UUID.randomUUID();
    Assertions.assertThrows(IllegalStateException.class, () -> this.queueService.dequeueTask(randomId));
  }

  @Test
  void testSubmitResultNullQueueIdThrows() {
    Result result = new Result(UUID.randomUUID(), "ok", ResultStatus.SUCCESS);
    Assertions.assertThrows(IllegalArgumentException.class, () -> this.queueService.submitResult((UUID)null, result));
  }

  @Test
  void testSubmitResultNullResultThrows() {
    UUID randomId = UUID.randomUUID();
    Assertions.assertThrows(IllegalArgumentException.class, () -> this.queueService.submitResult(randomId, (Result)null));
  }

  @Test
  void testSubmitResultNonexistentQueueThrows() {
    UUID randomId = UUID.randomUUID();
    Result result = new Result(UUID.randomUUID(), "ok", ResultStatus.SUCCESS);
    Assertions.assertThrows(IllegalStateException.class, () -> this.queueService.submitResult(randomId, result));
  }

  @Test
  void testGetResultNullQueueIdThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> this.queueService.getResult((UUID)null, UUID.randomUUID()));
  }

  @Test
  void testGetResultNullTaskIdThrows() {
    UUID randomId = UUID.randomUUID();
    Assertions.assertThrows(IllegalArgumentException.class, () -> this.queueService.getResult(randomId, (UUID)null));
  }

  @Test
  void testGetResultNonexistentQueueThrows() {
    UUID randomId = UUID.randomUUID();
    Assertions.assertThrows(IllegalStateException.class, () -> this.queueService.getResult(randomId, UUID.randomUUID()));
  }

  @Test
  public void testEnqueueTaskWithValidInput() {
    Queue queue = queueService.createQueue("test-queue");
    UUID queueId = queue.getId();
    Task task = new Task("{\"page\": 1}", 1);
    queueService.enqueueTask(queueId, task);
    assertTrue(queue.hasPendingTasks());
    assertEquals(1, queue.getTaskCount());
  }

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
}
