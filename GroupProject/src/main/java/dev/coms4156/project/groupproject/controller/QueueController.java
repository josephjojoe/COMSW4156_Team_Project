package dev.coms4156.project.groupproject.controller;

import dev.coms4156.project.groupproject.model.Queue;
import dev.coms4156.project.groupproject.model.Result;
import dev.coms4156.project.groupproject.model.Task;
import dev.coms4156.project.groupproject.service.QueueService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing task queues.
 * Provides endpoints for creating queues, enqueuing tasks, dequeuing tasks,
 * and submitting/retrieving results.
 */
@RestController
@RequestMapping("/queue")
public class QueueController {

  private final QueueService queueService;

  /**
   * Constructs a new QueueController with the specified QueueService.
   *
   * @param queueService the queue service to use
   */
  public QueueController(QueueService queueService) {
    this.queueService = queueService;
  }

  /**
   * Creates a new queue with the given name.
   *
   * @param request the request containing the queue name
   * @return the created queue
   */
  @PostMapping
  public ResponseEntity<Queue> createQueue(@RequestBody CreateQueueRequest request) {
    Queue queue = queueService.createQueue(request.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(queue);
  }

  /**
   * Enqueues a new task to the specified queue.
   *
   * @param queueId the ID of the queue
   * @param request the request containing task parameters and priority
   * @return the created task
   */
  @PostMapping("/{queueId}/task")
  public ResponseEntity<Task> enqueueTask(
      @PathVariable("queueId") UUID queueId,
      @RequestBody EnqueueTaskRequest request) {
    Task task = new Task(request.getParams(), request.getPriority());
    queueService.enqueueTask(queueId, task);
    return ResponseEntity.status(HttpStatus.CREATED).body(task);
  }

  /**
   * Dequeues the highest priority task from the specified queue.
   *
   * @param queueId the ID of the queue
   * @return the dequeued task, or NO_CONTENT if queue is empty
   */
  @GetMapping("/{queueId}/task")
  public ResponseEntity<Task> dequeueTask(@PathVariable("queueId") UUID queueId) {
    Task task = queueService.dequeueTask(queueId);
    if (task == null) {
      return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    return ResponseEntity.ok(task);
  }

  /**
   * Submits a result for a completed task.
   *
   * @param queueId the ID of the queue
   * @param request the request containing task ID, output, and status
   * @return the submitted result
   */
  @PostMapping("/{queueId}/result")
  public ResponseEntity<Result> submitResult(
      @PathVariable("queueId") UUID queueId,
      @RequestBody SubmitResultRequest request) {
    Result result = new Result(request.getTaskId(), request.getOutput(), request.getStatus());
    queueService.submitResult(queueId, result);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  /**
   * Retrieves the result for a specific task.
   *
   * @param queueId the ID of the queue
   * @param taskId the ID of the task
   * @return the result, or NOT_FOUND if no result exists
   */
  @GetMapping("/{queueId}/result/{taskId}")
  public ResponseEntity<Result> getResult(
      @PathVariable("queueId") UUID queueId,
      @PathVariable("taskId") UUID taskId) {
    Result result = queueService.getResult(queueId, taskId);
    if (result == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    return ResponseEntity.ok(result);
  }

  /**
   * Handles IllegalArgumentException by returning a BAD_REQUEST response.
   *
   * @param ex the exception to handle
   * @return a BAD_REQUEST response with the exception message
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }

  /**
   * Handles IllegalStateException by returning a NOT_FOUND response.
   *
   * @param ex the exception to handle
   * @return a NOT_FOUND response with the exception message
   */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<String> handleNotFound(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
  }

  /**
   * Request DTO for creating a new queue.
   */
  public static class CreateQueueRequest {
    private String name;

    /**
     * Default constructor.
     */
    public CreateQueueRequest() {}

    /**
     * Gets the queue name.
     *
     * @return the queue name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the queue name.
     *
     * @param name the queue name
     */
    public void setName(String name) {
      this.name = name;
    }
  }

  /**
   * Request DTO for enqueuing a task.
   */
  public static class EnqueueTaskRequest {
    private String params;
    private int priority;

    /**
     * Default constructor.
     */
    public EnqueueTaskRequest() {}

    /**
     * Gets the task parameters.
     *
     * @return the task parameters
     */
    public String getParams() {
      return params;
    }

    /**
     * Sets the task parameters.
     *
     * @param params the task parameters
     */
    public void setParams(String params) {
      this.params = params;
    }

    /**
     * Gets the task priority.
     *
     * @return the task priority
     */
    public int getPriority() {
      return priority;
    }

    /**
     * Sets the task priority.
     *
     * @param priority the task priority
     */
    public void setPriority(int priority) {
      this.priority = priority;
    }
  }

  /**
   * Request DTO for submitting a task result.
   */
  public static class SubmitResultRequest {
    private UUID taskId;
    private String output;
    private Result.ResultStatus status;

    /**
     * Default constructor.
     */
    public SubmitResultRequest() {}

    /**
     * Gets the task ID.
     *
     * @return the task ID
     */
    public UUID getTaskId() {
      return taskId;
    }

    /**
     * Sets the task ID.
     *
     * @param taskId the task ID
     */
    public void setTaskId(UUID taskId) {
      this.taskId = taskId;
    }

    /**
     * Gets the task output.
     *
     * @return the task output
     */
    public String getOutput() {
      return output;
    }

    /**
     * Sets the task output.
     *
     * @param output the task output
     */
    public void setOutput(String output) {
      this.output = output;
    }

    /**
     * Gets the result status.
     *
     * @return the result status
     */
    public Result.ResultStatus getStatus() {
      return status;
    }

    /**
     * Sets the result status.
     *
     * @param status the result status
     */
    public void setStatus(Result.ResultStatus status) {
      this.status = status;
    }
  }
}


