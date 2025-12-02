package dev.coms4156.project.server.model.snapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Root snapshot data structure containing all queues.
 * This class is serialized to JSON for persistent storage.
 */
public class SnapshotData {
  private List<QueueSnapshot> queues;
  private long timestamp;
  private String version;

  /**
   * Default constructor.
   */
  public SnapshotData() {
    this.queues = new ArrayList<>();
    this.timestamp = System.currentTimeMillis();
    this.version = "1.0";
  }

  /**
   * Gets the list of queue snapshots.
   *
   * @return list of queue snapshots
   */
  public List<QueueSnapshot> getQueues() {
    return queues;
  }

  /**
   * Sets the list of queue snapshots.
   *
   * @param queues list of queue snapshots
   */
  public void setQueues(List<QueueSnapshot> queues) {
    this.queues = queues;
  }

  /**
   * Gets the timestamp when this snapshot was created.
   *
   * @return timestamp in milliseconds
   */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * Sets the timestamp.
   *
   * @param timestamp timestamp in milliseconds
   */
  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  /**
   * Gets the snapshot format version.
   *
   * @return version string
   */
  public String getVersion() {
    return version;
  }

  /**
   * Sets the snapshot format version.
   *
   * @param version version string
   */
  public void setVersion(String version) {
    this.version = version;
  }
}

