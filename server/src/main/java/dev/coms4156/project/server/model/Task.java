package dev.coms4156.project.server.model;

import java.util.UUID;

/**
 * This class represents a single task in a queue that can be
 * processed by workers. Tasks are prioritized and track their
 * execution status throughout their lifecycle.
 */
public class Task implements Comparable<Task> {

  /**
   * Enum representing the various states a task can be in.
   */
  public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
  }

  private final UUID id;
  private final String params;
  private final int priority;
  private TaskStatus status;


  /**
   * Constructs a new {@code Task} with the specified parameters and priority.
   * The task is initialized with a unique ID and PENDING status.
   *
   * @param params the task parameters as a JSON string or plain text
   * @param priority the priority level (lower values = higher priority)
   */
  public Task(String params, int priority) {
    this.id = UUID.randomUUID();
    this.params = params;
    this.priority = priority;
    this.status = TaskStatus.PENDING;
  }

  /**
   * Constructs a {@code Task} with a specific ID and status.
   * Used for restoring tasks from persistence snapshots.
   *
   * @param id the task's UUID (must not be null)
   * @param params the task parameters as a JSON string or plain text
   * @param priority the priority level (lower values = higher priority)
   * @param status the task status
   */
  public Task(UUID id, String params, int priority, TaskStatus status) {
    this.id = id;
    this.params = params;
    this.priority = priority;
    this.status = status;
  }


  /**
   * Returns the unique task identifier.
   *
   * @return the task's UUID
   */
  public UUID getId() {
    return id;
  }


  /**
   * Returns the task parameters as a JSON-formatted string.
   * 
   * <p>The params field contains job-specific data needed by workers
   * to execute the task. The format is a JSON string that can be
   * parsed by workers according to their specific task type.
   * 
   * <p><b>Format:</b> Valid JSON string representation
   * 
   * <p><b>Example for PDF processing:</b>
   * <pre>
   * {@code
   * {
   *   "pageNumber": 5,
   *   "pdfPath": "/uploads/textbook.pdf",
   *   "outputFormat": "png",
   *   "resolution": 300
   * }
   * }
   * </pre>
   * 
   * <p><b>Example for video processing:</b>
   * <pre>
   * {@code
   * {
   *   "videoClip": "clip3.mp4",
   *   "aspectRatio": "16:9",
   *   "addCaptions": true,
   *   "targetFormat": "mp4"
   * }
   * }
   * </pre>
   * 
   * <p><b>Example for ML model training:</b>
   * <pre>
   * {@code
   * {
   *   "modelType": "CNN",
   *   "epochs": 50,
   *   "learningRate": 0.001,
   *   "batchSize": 32,
   *   "dataset": "imagenet"
   * }
   * }
   * </pre>
   * 
   * <p><b>Note:</b> The task queue service does not validate or parse
   * these parameters. It is the responsibility of workers to parse the
   * JSON and extract the fields relevant to their task type.
   *
   * @return the parameters as a JSON-formatted string
   */
  public String getParams() {
    return params;
  }


  /**
   * Returns the task priority level.
   *
   * @return the priority value
   */
  public int getPriority() {
    return priority;
  }


  /**
   * Returns the current status of the task.
   *
   * @return the task status
   */
  public TaskStatus getStatus() {
    return status;
  }


  /**
   * Updates the task status.
   *
   * @param status the new status to set
   */
  public synchronized void setStatus(TaskStatus status) {
    this.status = status;
  }


  /**
   * Compares this task to another based on priority.
   * Lower priority values are considered higher priority
   * and will be processed first.
   *
   * @param other the task to compare to
   * @return negative if this task has higher priority,
   *         positive if lower priority, 0 if equal
   */
  @Override
  public int compareTo(Task other) {
    return Integer.compare(this.priority, other.priority);
  }

  /**
   * Two tasks are equal if and only if they have the same unique ID.
   * Note: This means equals() is not consistent with compareTo(), which is
   * acceptable since PriorityBlockingQueue does not require consistency
   * between these methods.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Task)) {
      return false;
    }
    Task other = (Task) obj;
    return this.id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }


  /**
   * Returns a string representation of this task.
   *
   * @return a string containing the task's key attributes
   */
  @Override
  public String toString() {
    return "Task{"
            + "id=" + id
            + ", priority=" + priority
            + ", status=" + status
            + ", params='" + params + '\''
            + '}';
  }
}