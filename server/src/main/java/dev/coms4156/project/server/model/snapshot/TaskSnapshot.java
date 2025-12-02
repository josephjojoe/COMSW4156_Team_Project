package dev.coms4156.project.server.model.snapshot;

/**
 * Snapshot representation of a Task for serialization.
 */
public class TaskSnapshot {
  private String id;
  private String params;
  private int priority;
  private String status;

  /**
   * Default constructor.
   */
  public TaskSnapshot() {
  }

  /**
   * Constructor with parameters.
   *
   * @param id       task ID
   * @param params   task parameters
   * @param priority task priority
   * @param status   task status
   */
  public TaskSnapshot(String id, String params, int priority, String status) {
    this.id = id;
    this.params = params;
    this.priority = priority;
    this.status = status;
  }

  /**
   * Gets the task ID.
   *
   * @return task ID as string
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the task ID.
   *
   * @param id task ID as string
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Gets the task parameters.
   *
   * @return task parameters
   */
  public String getParams() {
    return params;
  }

  /**
   * Sets the task parameters.
   *
   * @param params task parameters
   */
  public void setParams(String params) {
    this.params = params;
  }

  /**
   * Gets the task priority.
   *
   * @return task priority
   */
  public int getPriority() {
    return priority;
  }

  /**
   * Sets the task priority.
   *
   * @param priority task priority
   */
  public void setPriority(int priority) {
    this.priority = priority;
  }

  /**
   * Gets the task status.
   *
   * @return task status as string
   */
  public String getStatus() {
    return status;
  }

  /**
   * Sets the task status.
   *
   * @param status task status as string
   */
  public void setStatus(String status) {
    this.status = status;
  }
}

