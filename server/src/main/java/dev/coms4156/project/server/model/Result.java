package dev.coms4156.project.server.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * This class represents the result of a completed task.
 * Results are stored and can be retrieved by clients
 * using the original task ID.
 */
public class Result {

  /**
    * Enum representing the outcome status of task execution.
    */
  public enum ResultStatus {
    SUCCESS,
    FAILURE
  }

  private final UUID taskId;
  private final String output;
  private final ResultStatus status;
  private final LocalDateTime timestamp;


  /**
   * Constructs a new {@code Result} for a completed task.
   *
   * @param taskId the UUID of the task that produced this result
   * @param output the result data or output from task processing
   * @param status the execution status (SUCCESS or FAILURE)
   */
  public Result(UUID taskId, String output, ResultStatus status) {
    this.taskId = taskId;
    this.output = output;
    this.status = status;
    this.timestamp = LocalDateTime.now();
  }


  /**
   * Returns the task ID associated with this result.
   * This method is required by the Queue class to map results to tasks.
   *
   * @return the task's UUID
   */
  public UUID getTaskId() {
    return taskId;
  }


  /**
   * Returns the output data from the task execution.
   *
   * @return the result output as a string
   */
  public String getOutput() {
    return output;
  }


  /**
   * Returns the execution status of the task.
   *
   * @return SUCCESS or FAILURE status
   */
  public ResultStatus getStatus() {
    return status;
  }


  /**
   * Returns the timestamp when this result was created.
   *
   * @return the creation timestamp
   */
  public LocalDateTime getTimestamp() {
    return timestamp;
  }


  /**
   * Returns a string representation of this result.
   *
   * @return a string containing the result's key attributes
   */
  @Override
  public String toString() {
    return "Result{"
            + "taskId=" + taskId
            + ", status=" + status
            + ", timestamp=" + timestamp
            + ", output='" + output + '\''
            + '}';
  }
}
