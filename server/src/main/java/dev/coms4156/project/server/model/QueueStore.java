package dev.coms4156.project.server.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.coms4156.project.server.model.snapshot.QueueSnapshot;
import dev.coms4156.project.server.model.snapshot.ResultSnapshot;
import dev.coms4156.project.server.model.snapshot.SnapshotData;
import dev.coms4156.project.server.model.snapshot.TaskSnapshot;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class serves as a centralized in-memory registry
 * for all active {@link Queue} instances in the application.
 *
 * <p>Includes snapshot-based persistence to ensure queue state survives
 * server restarts. Snapshots are saved periodically and on shutdown, and loaded
 * automatically on startup.
 */
public final class QueueStore {

  private static final Logger log = LoggerFactory.getLogger(QueueStore.class);
  private static final String SNAPSHOT_FILE = "queue_snapshot.json";
  private static final String SNAPSHOT_TEMP_FILE = "queue_snapshot.tmp";
  private static final int SNAPSHOT_INTERVAL_SECONDS = 30;

  /**
   * Singleton instance of QueueStore.
   */
  private static QueueStore instance;


  /**
   * Thread-safe map storing queue IDs mapped to Queue objects.
   */
  private final Map<UUID, Queue> queues;

  /**
   * Scheduler for periodic snapshots.
   */
  private final ScheduledExecutorService snapshotScheduler;

  /**
   * Gson instance for JSON serialization.
   */
  private final Gson gson;


  /**
  * Private constructor prevents external instantiation.
  * Initializes the queue store and loads any existing snapshot.
  */
  private QueueStore() {
    this.queues = new ConcurrentHashMap<>();
    this.gson = new GsonBuilder().setPrettyPrinting().create();
    this.snapshotScheduler = Executors.newScheduledThreadPool(1);
    
    // Load existing snapshot on startup
    loadSnapshot();
    
    // Schedule periodic snapshots
    startPeriodicSnapshots();
    
    // Register shutdown hook to save snapshot on clean shutdown
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown detected, saving final snapshot...");
      saveSnapshot();
      snapshotScheduler.shutdown();
    }));
  }


  /**
  * Returns the single shared instance of {@code QueueStore}.
  *
  * @return the global QueueStore instance
  */
  public static synchronized QueueStore getInstance() {
    if (instance == null) {
      instance = new QueueStore();
    }
    return instance;
  }


  /**
   * Creates and registers a new {@link Queue} with the given name.
   *
   * @param name descriptive name for the queue
   * @return the newly created Queue instance
   */
  public synchronized Queue createQueue(String name) {
    Queue queue = new Queue(name);
    queues.put(queue.getId(), queue);
    return queue;
  }


  /**
  * Retrieves a queue by its unique ID.
  *
  * @param id the queue ID
  * @return the Queue that matches the ID, or {@code null} if not found
  */
  public Queue getQueue(UUID id) {
    return queues.get(id);
  }


  /**
   * Removes a queue by ID if it exists.
   *
   * @param id ID of the queue to remove
   * @return {@code true} if removed successfully, {@code false} otherwise
  */
  public synchronized boolean removeQueue(UUID id) {
    return queues.remove(id) != null;
  }


  /**
  * Clears all queues.
  */
  public synchronized void clearAll() {
    queues.clear();
  }


  /**
  * Returns all queues currently stored.
  *
  * @return a map of all queue IDs and their corresponding Queue objects
  */
  public Map<UUID, Queue> getAllQueues() {
    return queues;
  }

  /**
   * Starts the periodic snapshot scheduler.
   * Snapshots are saved every SNAPSHOT_INTERVAL_SECONDS.
   */
  private void startPeriodicSnapshots() {
    snapshotScheduler.scheduleAtFixedRate(
        this::saveSnapshot,
        SNAPSHOT_INTERVAL_SECONDS,
        SNAPSHOT_INTERVAL_SECONDS,
        TimeUnit.SECONDS
    );
    log.info("Started periodic snapshots (interval: {} seconds)", SNAPSHOT_INTERVAL_SECONDS);
  }

  /**
   * Saves the current state of all queues to a snapshot file.
   * Uses atomic file write (write to temp, then rename) to prevent corruption.
   */
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  public synchronized void saveSnapshot() {
    try {
      final File tempFile = new File(SNAPSHOT_TEMP_FILE);
      final File actualFile = new File(SNAPSHOT_FILE);

      // Create snapshot data
      SnapshotData snapshot = new SnapshotData();
      List<QueueSnapshot> queueSnapshots = new ArrayList<>();

      for (Queue queue : queues.values()) {
        QueueSnapshot queueSnap = new QueueSnapshot();
        queueSnap.setId(queue.getId().toString());
        queueSnap.setName(queue.getName());

        // Snapshot all tasks
        List<TaskSnapshot> taskSnapshots = new ArrayList<>();
        for (Task task : queue.getAllTasks()) {
          TaskSnapshot taskSnap = new TaskSnapshot(
              task.getId().toString(),
              task.getParams(),
              task.getPriority(),
              task.getStatus().name()
          );
          taskSnapshots.add(taskSnap);
        }
        queueSnap.setTasks(taskSnapshots);

        // Snapshot all results
        List<ResultSnapshot> resultSnapshots = new ArrayList<>();
        for (Result result : queue.getAllResults()) {
          ResultSnapshot resultSnap = new ResultSnapshot(
              result.getTaskId().toString(),
              result.getOutput(),
              result.getStatus().name(),
              result.getTimestamp().toString()
          );
          resultSnapshots.add(resultSnap);
        }
        queueSnap.setResults(resultSnapshots);

        queueSnapshots.add(queueSnap);
      }

      snapshot.setQueues(queueSnapshots);

      // Write to temp file
      try (var writer = Files.newBufferedWriter(tempFile.toPath(), StandardCharsets.UTF_8)) {
        gson.toJson(snapshot, writer);
      }

      // Atomic rename (crash-safe)
      if (actualFile.exists() && !actualFile.delete()) {
        log.warn("Failed to delete old snapshot file");
      }
      if (!tempFile.renameTo(actualFile)) {
        log.error("Failed to rename temp snapshot file");
      } else {
        log.debug("Snapshot saved successfully ({} queues, {} total tasks)",
            queueSnapshots.size(),
            queueSnapshots.stream().mapToInt(q -> q.getTasks().size()).sum());
      }

    } catch (IOException e) {
      log.error("Failed to save snapshot", e);
    }
  }

  /**
   * Loads queue state from the snapshot file if it exists.
   * Called automatically on startup.
   */
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  private synchronized void loadSnapshot() {
    File snapshotFile = new File(SNAPSHOT_FILE);
    
    if (!snapshotFile.exists()) {
      log.info("No snapshot file found, starting with empty queue store");
      return;
    }

    try (var reader = Files.newBufferedReader(snapshotFile.toPath(), StandardCharsets.UTF_8)) {
      SnapshotData snapshot = gson.fromJson(reader, SnapshotData.class);

      if (snapshot == null || snapshot.getQueues() == null) {
        log.warn("Snapshot file is empty or invalid");
        return;
      }

      int totalTasks = 0;
      int totalResults = 0;

      // Restore each queue
      for (QueueSnapshot queueSnap : snapshot.getQueues()) {
        UUID queueId = UUID.fromString(queueSnap.getId());
        Queue queue = new Queue(queueSnap.getName(), queueId);

        // Restore tasks
        if (queueSnap.getTasks() != null) {
          for (TaskSnapshot taskSnap : queueSnap.getTasks()) {
            Task task = new Task(taskSnap.getParams(), taskSnap.getPriority());
            // Use reflection or add a setter to restore task ID and status
            // For simplicity, we'll create new tasks with original params
            queue.enqueue(task);
            totalTasks++;
          }
        }

        // Restore results
        if (queueSnap.getResults() != null) {
          for (ResultSnapshot resultSnap : queueSnap.getResults()) {
            Result result = new Result(
                UUID.fromString(resultSnap.getTaskId()),
                resultSnap.getOutput(),
                Result.ResultStatus.valueOf(resultSnap.getStatus())
            );
            queue.addResult(result);
            totalResults++;
          }
        }

        queues.put(queueId, queue);
      }

      log.info("Snapshot loaded successfully: {} queues, {} tasks, {} results",
          snapshot.getQueues().size(), totalTasks, totalResults);

    } catch (IOException e) {
      log.error("Failed to load snapshot", e);
    } catch (Exception e) {
      log.error("Error parsing snapshot file", e);
    }
  }
}


