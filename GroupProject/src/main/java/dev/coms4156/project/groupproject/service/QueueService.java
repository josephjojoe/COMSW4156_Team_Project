package dev.coms4156.project.groupproject.service;

import dev.coms4156.project.groupproject.model.Queue;
import dev.coms4156.project.groupproject.model.QueueStore;
// import dev.coms4156.project.groupproject.model.Task;
// import dev.coms4156.project.groupproject.model.Result;
import java.util.UUID;

/**
 * Service class that provides business logic for queue operations.
 * Acts as an intermediary between the controller layer and the data layer.
 */
public class QueueService {
    
    private final QueueStore queueStore;
    
    /**
     * Constructs a new QueueService with the default QueueStore instance.
     */
    public QueueService() {
        this.queueStore = QueueStore.getInstance();
    }
    
    /**
     * Constructs a new QueueService with the specified QueueStore instance.
     * This constructor is primarily for testing purposes.
     *
     * @param queueStore the QueueStore instance to use
     */
    public QueueService(QueueStore queueStore) {
        this.queueStore = queueStore;
    }
    
    /**
     * Creates a new queue with the given name.
     *
     * @param name the descriptive name for the queue
     * @return the newly created Queue instance
     * @throws IllegalArgumentException if the name is null or empty
     */
    public Queue createQueue(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Queue name cannot be null or empty");
        }
        return queueStore.createQueue(name.trim());
    }
    
    /**
     * Adds a task to the specified queue.
     *
     * @param queueId the ID of the queue to add the task to
     * @param task the task to enqueue
     * @throws IllegalArgumentException if queueId is null/empty or task is null
     * @throws IllegalStateException if the queue with the given ID does not exist
     */
    public void enqueueTask(String queueId, Task task) {
        validateQueueId(queueId);
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        
        Queue queue = queueStore.getQueue(queueId);
        if (queue == null) {
            throw new IllegalStateException("Queue with ID '" + queueId + "' does not exist");
        }
        
        boolean success = queue.enqueue(task);
        if (!success) {
            throw new IllegalStateException("Failed to enqueue task to queue '" + queueId + "'");
        }
    }
    
    /**
     * Retrieves and removes the highest priority task from the specified queue.
     * This method is typically called by workers to get their next task.
     *
     * @param queueId the ID of the queue to dequeue from
     * @return the highest priority task, or null if the queue is empty
     * @throws IllegalArgumentException if queueId is null or empty
     * @throws IllegalStateException if the queue with the given ID does not exist
     */
    public Task dequeueTask(String queueId) {
        validateQueueId(queueId);
        
        Queue queue = queueStore.getQueue(queueId);
        if (queue == null) {
            throw new IllegalStateException("Queue with ID '" + queueId + "' does not exist");
        }
        
        Task task = queue.dequeue();
        if (task != null) {
            task.setStatus(Task.TaskStatus.IN_PROGRESS);
        }
        return task;
    }
    
    /**
     * Submits a result for a completed task.
     *
     * @param queueId the ID of the queue the task belongs to
     * @param result the result to submit
     * @throws IllegalArgumentException if queueId is null/empty or result is null
     * @throws IllegalStateException if the queue with the given ID does not exist
     */
    public void submitResult(String queueId, Result result) {
        validateQueueId(queueId);
        if (result == null) {
            throw new IllegalArgumentException("Result cannot be null");
        }
        
        Queue queue = queueStore.getQueue(queueId);
        if (queue == null) {
            throw new IllegalStateException("Queue with ID '" + queueId + "' does not exist");
        }
        
        boolean success = queue.addResult(result);
        if (!success) {
            throw new IllegalStateException("Failed to submit result for task '" + result.getTaskId() + "'");
        }
    }
    
    /**
     * Retrieves the result for a specific task.
     *
     * @param queueId the ID of the queue the task belongs to
     * @param taskId the ID of the task to get the result for
     * @return the result if found, or null if no result exists for the task
     * @throws IllegalArgumentException if queueId or taskId is null
     * @throws IllegalStateException if the queue with the given ID does not exist
     */
    public Result getResult(String queueId, UUID taskId) {
        validateQueueId(queueId);
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        
        Queue queue = queueStore.getQueue(queueId);
        if (queue == null) {
            throw new IllegalStateException("Queue with ID '" + queueId + "' does not exist");
        }
        
        return queue.getResult(taskId);
    }
    
    /**
     * Retrieves a queue by its ID.
     *
     * @param queueId the ID of the queue to retrieve
     * @return the Queue instance, or null if not found
     * @throws IllegalArgumentException if queueId is null or empty
     */
    public Queue getQueue(String queueId) {
        validateQueueId(queueId);
        return queueStore.getQueue(queueId);
    }
    
    /**
     * Checks if a queue exists with the given ID.
     *
     * @param queueId the ID to check
     * @return true if the queue exists, false otherwise
     * @throws IllegalArgumentException if queueId is null or empty
     */
    public boolean queueExists(String queueId) {
        validateQueueId(queueId);
        return queueStore.getQueue(queueId) != null;
    }
    
    /**
     * Validates that a queue ID is not null or empty.
     *
     * @param queueId the queue ID to validate
     * @throws IllegalArgumentException if queueId is null or empty
     */
    private void validateQueueId(String queueId) {
        if (queueId == null || queueId.trim().isEmpty()) {
            throw new IllegalArgumentException("Queue ID cannot be null or empty");
        }
    }
}
