package dev.coms4156.project.groupproject.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.PriorityBlockingQueue;


/**
 * This class represents an in-memory task queue that stores
 * tasks in priority order and tracks their corresponding results.
 */
public class Queue {
    private final String id;
    private final String name;
    private final PriorityBlockingQueue<Task> tasks;
    private final Map<UUID, Result> results;


    /**
     * Constructs a new {@code Queue} with the given name and initializes its
     * task queue and result map.
     *
     * @param name the descriptive name of this queue
     */
    public Queue(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.tasks = new PriorityBlockingQueue<>();
        this.results = new HashMap<>();
    }


    /**
     * Returns the unique queue ID.
     *
     * @return the queue ID as a string
     */
    public String getId() {
        return id;
    }


    /**
     * Returns the queue name.
     *
     * @return the name of this queue
     */
    public String getName() {
        return name;
    }


    /**
     * Attempts to add a task to the queue in a thread-safe manner.
     * Returns {@code true} if the task was successfully added
     * or {@code false} if the task was invalid or could not be added.
     *
     * @param task the task to enqueue
     * @return {@code true} if added successfully, {@code false} otherwise
     */
    public synchronized boolean enqueue(Task task) {
        if (task == null) {
            return false;
        }
        return tasks.add(task);
    }


    /**
     * Retrieves and removes the highest-priority task from the queue.
     * Returns {@code null} if the queue is empty.
     *
     * @return the next task or {@code null} if no tasks remain
     */
    public synchronized Task dequeue() {
        return tasks.poll();
    }


    /**
     * Attempts to add a result from a completed task.
     * Returns {@code true} if the result was successfully stored,
     * or {@code false} if the result or its task ID was invalid.
     *
     * @param result the result to store
     * @return {@code true} if successfully added, {@code false} otherwise
     */
    public synchronized boolean addResult(Result result) {
        if (result == null || result.getTaskId() == null) {
            return false;
        }
        results.put(result.getTaskId(), result);
        return true;
    }


    /**
     * Retrieves the stored result for a given task ID.
     *
     * @param taskId the unique identifier of the task
     * @return the result if present, or {@code null} otherwise
     */
    public Result getResult(UUID taskId) {
        return results.get(taskId);
    }


    /**
     * Returns whether the queue currently has pending tasks.
     *
     * @return {@code true} if there are pending tasks, otherwise {@code false}
     */
    public boolean hasPendingTasks() {
        return !tasks.isEmpty();
    }


    /**
     * Returns the total number of tasks currently in the queue.
     *
     * @return the number of tasks in this queue
     */
    public int getTaskCount() {
        return tasks.size();
    }


    /**
     * Returns the total number of stored results.
     *
     * @return the number of results stored in this queue
     */
    public int getResultCount() {
        return results.size();
    }
}
