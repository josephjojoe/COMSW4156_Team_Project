package dev.coms4156.project.groupproject.controller;

import dev.coms4156.project.groupproject.model.Queue;
import dev.coms4156.project.groupproject.model.Result;
import dev.coms4156.project.groupproject.model.Task;
import dev.coms4156.project.groupproject.service.QueueService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private static final Logger log = LoggerFactory.getLogger(QueueController.class);

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
    log.info("createQueue name={}", request.getName());
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
    log.info("enqueueTask queueId={} priority={}", queueId, request.getPriority());
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
    log.info("dequeueTask queueId={}", queueId);
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
    log.info("submitResult queueId={} taskId={} status={}",
          queueId, request.getTaskId(), request.getStatus());
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
    log.info("getResult queueId={} taskId={}", queueId, taskId);
    Result result = queueService.getResult(queueId, taskId);
    if (result == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    return ResponseEntity.ok(result);
  }

  /**
   * Gets the status of a queue including task and result counts.
   * This endpoint is used by aggregators to poll for completion status.
   *
   * @param queueId the ID of the queue
   * @return the queue status with task counts and completion information
   */
  @GetMapping("/{queueId}/status")
  public ResponseEntity<QueueStatusResponse> getQueueStatus(
      @PathVariable("queueId") UUID queueId) {
    log.info("getQueueStatus queueId={}", queueId);
    Queue queue = queueService.getQueue(queueId);
    if (queue == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    
    QueueStatusResponse status = new QueueStatusResponse(
        queue.getId(),
        queue.getName(),
        queue.getTaskCount(),
        queue.getResultCount(),
        queue.hasPendingTasks()
    );
    
    return ResponseEntity.ok(status);
  }

  /**
   * Handles IllegalArgumentException by returning a BAD_REQUEST response.
   *
   * @param ex the exception to handle
   * @return a BAD_REQUEST response with the exception message
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
    log.warn("badRequest error={}", ex.getMessage());
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
    log.warn("notFound error={}", ex.getMessage());
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

  /**
   * Response DTO for queue status information.
   * Used by aggregators to poll for queue completion status.
   */
  public static class QueueStatusResponse {
    private UUID id;
    private String name;
    private int pendingTaskCount;
    private int completedResultCount;
    private boolean hasPendingTasks;

    /**
     * Default constructor.
     */
    public QueueStatusResponse() {}

    /**
     * Constructor with all fields.
     *
     * @param id the queue ID
     * @param name the queue name
     * @param pendingTaskCount the number of pending tasks in the queue
     * @param completedResultCount the number of completed results
     * @param hasPendingTasks whether the queue has any pending tasks
     */
    public QueueStatusResponse(UUID id, String name, int pendingTaskCount,
                               int completedResultCount, boolean hasPendingTasks) {
      this.id = id;
      this.name = name;
      this.pendingTaskCount = pendingTaskCount;
      this.completedResultCount = completedResultCount;
      this.hasPendingTasks = hasPendingTasks;
    }

    /**
     * Gets the queue ID.
     *
     * @return the queue ID
     */
    public UUID getId() {
      return id;
    }

    /**
     * Sets the queue ID.
     *
     * @param id the queue ID
     */
    public void setId(UUID id) {
      this.id = id;
    }

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

    /**
     * Gets the number of pending tasks.
     *
     * @return the pending task count
     */
    public int getPendingTaskCount() {
      return pendingTaskCount;
    }

    /**
     * Sets the number of pending tasks.
     *
     * @param pendingTaskCount the pending task count
     */
    public void setPendingTaskCount(int pendingTaskCount) {
      this.pendingTaskCount = pendingTaskCount;
    }

    /**
     * Gets the number of completed results.
     *
     * @return the completed result count
     */
    public int getCompletedResultCount() {
      return completedResultCount;
    }

    /**
     * Sets the number of completed results.
     *
     * @param completedResultCount the completed result count
     */
    public void setCompletedResultCount(int completedResultCount) {
      this.completedResultCount = completedResultCount;
    }

    /**
     * Gets whether the queue has pending tasks.
     *
     * @return true if there are pending tasks, false otherwise
     */
    public boolean isHasPendingTasks() {
      return hasPendingTasks;
    }

    /**
     * Sets whether the queue has pending tasks.
     *
     * @param hasPendingTasks true if there are pending tasks, false otherwise
     */
    public void setHasPendingTasks(boolean hasPendingTasks) {
      this.hasPendingTasks = hasPendingTasks;
    }
  }
}


