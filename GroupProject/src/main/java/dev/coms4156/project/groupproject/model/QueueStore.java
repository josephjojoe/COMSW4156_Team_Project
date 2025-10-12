package dev.coms4156.project.groupproject.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * This class serves as a centralized in-memory registry
 * for all active {@link Queue} instances in the application.
 */
public class QueueStore {

    /**
     * Singleton instance of QueueStore.
     */
    private static QueueStore instance = null;


    /**
     * Thread-safe map storing queue IDs mapped to Queue objects.
     */
    private final Map<String, Queue> queues;


    /**
     * Private constructor prevents external instantiation.
     */
    private QueueStore() {
        this.queues = new ConcurrentHashMap<>();
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
     * @param id the queue ID string
     * @return the Queue that matches the ID, or {@code null} if not found
     */
    public Queue getQueue(String id) {
        return queues.get(id);
    }


    /**
     * Removes a queue by ID if it exists.
     *
     * @param id ID of the queue to remove
     * @return {@code true} if removed successfully, {@code false} otherwise
     */
    public synchronized boolean removeQueue(String id) {
        return queues.remove(id) != null;
    }


    /**
     * Clears all queues
     */
    public synchronized void clearAll() {
        queues.clear();
    }


    /**
     * Returns all queues currently stored.
     *
     * @return a map of all queue IDs and their corresponding Queue objects
     */
    public Map<String, Queue> getAllQueues() {
        return queues;
    }
}


