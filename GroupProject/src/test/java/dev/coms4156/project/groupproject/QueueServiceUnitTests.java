package dev.coms4156.project.groupproject;

import dev.coms4156.project.groupproject.model.Queue;
import dev.coms4156.project.groupproject.model.Result;
import dev.coms4156.project.groupproject.model.Task;
import dev.coms4156.project.groupproject.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
/**
 * This class contains the unit tests for the QueueService class.
 */
@SpringBootTest
public class QueueServiceUnitTests {

  private QueueService queueService;

  /**
   * Sets up a queue service before each test case.
   */
  @BeforeEach
  void setUp() {
    queueService = new QueueService();
  }

  @Test
  void testCreateQueueWithValidName(){
    Queue queue = queueService.createQueue("Queue1");
    assertNotNull(queue);
    assertEquals("Queue1", queue.getName());
  }

  @Test
  void testGetQueueExists(){
    Queue queue = queueService.createQueue("Queue1");
    Queue queue2 = queueService.getQueue(queue.getId());
    assertNotNull(queue2);
    assertEquals(queue, queue2);
  }

  @Test
  void testGetQueueNotFound(){
    Queue queue = queueService.getQueue(UUID.randomUUID());
    assertNull(queue);
  }

  @Test
  void testQueueExistsReturnsTrue(){
    Queue queue = queueService.createQueue("Queue1");
    assertTrue(queueService.queueExists(queue.getId()));
  }

  @Test
  void testQueueExistsReturnsFalse(){
    assertFalse(queueService.queueExists(UUID.randomUUID()));
  }

  @Test
  void testGetResult(){
    Queue queue = queueService.createQueue("Queue1");
    Task task = new Task("Test task", 1);
    queueService.enqueueTask(queue.getId(), task);

    Result result = new Result(task.getId(), "Completed", Result.ResultStatus.SUCCESS);
    queueService.submitResult(queue.getId(), result);
    Result retrieved = queueService.getResult(queue.getId(), task.getId());
    assertNotNull(retrieved);
    assertEquals(task.getId(), retrieved.getTaskId());
  }

  @Test
  void testGetResultNotFound(){
    Queue queue = queueService.createQueue("Queue1");
    UUID uuid = UUID.randomUUID();
    Result result = queueService.getResult(queue.getId(), uuid);
    assertNull(result);
  }

  @Test
  void testSubmitResult(){
    Queue queue = queueService.createQueue("Queue1");
    Task task = new Task("Test task", 1);
    queueService.enqueueTask(queue.getId(), task);
    Result result = new Result(task.getId(), "Success", Result.ResultStatus.SUCCESS);
    queueService.submitResult(queue.getId(), result);
    assertEquals(1, queue.getResultCount());
  }
}