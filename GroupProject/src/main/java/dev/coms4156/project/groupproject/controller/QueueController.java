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

@RestController
@RequestMapping("/queue")
public class QueueController {

  private final QueueService queueService;

  public QueueController(QueueService queueService) {
    this.queueService = queueService;
  }

  @PostMapping
  public ResponseEntity<Queue> createQueue(@RequestBody CreateQueueRequest request) {
    Queue queue = queueService.createQueue(request.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(queue);
  }

  @PostMapping("/{queueId}/task")
  public ResponseEntity<Task> enqueueTask(
      @PathVariable("queueId") UUID queueId,
      @RequestBody EnqueueTaskRequest request) {
    Task task = new Task(request.getParams(), request.getPriority());
    queueService.enqueueTask(queueId, task);
    return ResponseEntity.status(HttpStatus.CREATED).body(task);
  }

  @GetMapping("/{queueId}/task")
  public ResponseEntity<Task> dequeueTask(@PathVariable("queueId") UUID queueId) {
    Task task = queueService.dequeueTask(queueId);
    if (task == null) {
      return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    return ResponseEntity.ok(task);
  }

  @PostMapping("/{queueId}/result")
  public ResponseEntity<Result> submitResult(
      @PathVariable("queueId") UUID queueId,
      @RequestBody SubmitResultRequest request) {
    Result result = new Result(request.getTaskId(), request.getOutput(), request.getStatus());
    queueService.submitResult(queueId, result);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

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

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<String> handleNotFound(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
  }

  public static class CreateQueueRequest {
    private String name;

    public CreateQueueRequest() {}

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  public static class EnqueueTaskRequest {
    private String params;
    private int priority;

    public EnqueueTaskRequest() {}

    public String getParams() {
      return params;
    }

    public void setParams(String params) {
      this.params = params;
    }

    public int getPriority() {
      return priority;
    }

    public void setPriority(int priority) {
      this.priority = priority;
    }
  }

  public static class SubmitResultRequest {
    private UUID taskId;
    private String output;
    private Result.ResultStatus status;

    public SubmitResultRequest() {}

    public UUID getTaskId() {
      return taskId;
    }

    public void setTaskId(UUID taskId) {
      this.taskId = taskId;
    }

    public String getOutput() {
      return output;
    }

    public void setOutput(String output) {
      this.output = output;
    }

    public Result.ResultStatus getStatus() {
      return status;
    }

    public void setStatus(Result.ResultStatus status) {
      this.status = status;
    }
  }
}


