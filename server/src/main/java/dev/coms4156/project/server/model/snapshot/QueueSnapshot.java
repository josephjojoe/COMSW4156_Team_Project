package dev.coms4156.project.server.model.snapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot representation of a Queue for serialization.
 * Contains queue metadata and all its tasks and results.
 */
public class QueueSnapshot {
  private String id;
  private String name;
  private List<TaskSnapshot> tasks;
  private List<ResultSnapshot> results;

  /**
   * Default constructor.
   */
  public QueueSnapshot() {
    this.tasks = new ArrayList<>();
    this.results = new ArrayList<>();
  }

  /**
   * Constructor with parameters.
   *
   * @param id      queue ID
   * @param name    queue name
   * @param tasks   list of tasks
   * @param results list of results
   */
  public QueueSnapshot(String id, String name, List<TaskSnapshot> tasks,
                       List<ResultSnapshot> results) {
    this.id = id;
    this.name = name;
    this.tasks = tasks;
    this.results = results;
  }

  /**
   * Gets the queue ID.
   *
   * @return queue ID as string
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the queue ID.
   *
   * @param id queue ID as string
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Gets the queue name.
   *
   * @return queue name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the queue name.
   *
   * @param name queue name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the list of task snapshots.
   *
   * @return list of task snapshots
   */
  public List<TaskSnapshot> getTasks() {
    return tasks;
  }

  /**
   * Sets the list of task snapshots.
   *
   * @param tasks list of task snapshots
   */
  public void setTasks(List<TaskSnapshot> tasks) {
    this.tasks = tasks;
  }

  /**
   * Gets the list of result snapshots.
   *
   * @return list of result snapshots
   */
  public List<ResultSnapshot> getResults() {
    return results;
  }

  /**
   * Sets the list of result snapshots.
   *
   * @param results list of result snapshots
   */
  public void setResults(List<ResultSnapshot> results) {
    this.results = results;
  }
}

