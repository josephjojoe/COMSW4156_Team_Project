package dev.coms4156.project.server.model.snapshot;

/**
 * Snapshot representation of a Result for serialization.
 */
public class ResultSnapshot {
  private String taskId;
  private String output;
  private String status;
  private String timestamp;

  /**
   * Default constructor.
   */
  public ResultSnapshot() {
  }

  /**
   * Constructor with parameters.
   *
   * @param taskId    task ID
   * @param output    result output
   * @param status    result status
   * @param timestamp timestamp as ISO string
   */
  public ResultSnapshot(String taskId, String output, String status, String timestamp) {
    this.taskId = taskId;
    this.output = output;
    this.status = status;
    this.timestamp = timestamp;
  }

  /**
   * Gets the task ID.
   *
   * @return task ID as string
   */
  public String getTaskId() {
    return taskId;
  }

  /**
   * Sets the task ID.
   *
   * @param taskId task ID as string
   */
  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  /**
   * Gets the result output.
   *
   * @return result output
   */
  public String getOutput() {
    return output;
  }

  /**
   * Sets the result output.
   *
   * @param output result output
   */
  public void setOutput(String output) {
    this.output = output;
  }

  /**
   * Gets the result status.
   *
   * @return result status as string
   */
  public String getStatus() {
    return status;
  }

  /**
   * Sets the result status.
   *
   * @param status result status as string
   */
  public void setStatus(String status) {
    this.status = status;
  }

  /**
   * Gets the timestamp.
   *
   * @return timestamp as ISO string
   */
  public String getTimestamp() {
    return timestamp;
  }

  /**
   * Sets the timestamp.
   *
   * @param timestamp timestamp as ISO string
   */
  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }
}

