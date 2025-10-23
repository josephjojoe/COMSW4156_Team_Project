package dev.coms4156.project.groupproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.groupproject.model.Task;
import dev.coms4156.project.groupproject.model.Task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Task class.
 * Tests task creation, priority ordering, status management,
 * and comparable implementation.
 */
public class TaskUnitTests {

    private Task task;
    private String testParams;
    private int testPriority;

    /**
     * Sets up test fixtures before each test method.
     */
    @BeforeEach
    public void setUp() {
        testParams = "{\"pageNumber\": 5, \"pdfPath\": \"/uploads/test.pdf\"}";
        testPriority = 1;
        task = new Task(testParams, testPriority);
    }

    /**
     * Tests that a newly created task has a non-null UUID.
     */
    @Test
    public void testTaskCreationGeneratesId() {
        assertNotNull(task.getId(), "Task ID should not be null");
    }

    /**
     * Tests that each task gets a unique ID.
     */
    @Test
    public void testTaskIdsAreUnique() {
        Task task2 = new Task(testParams, testPriority);
        assertNotEquals(task.getId(), task2.getId(), 
                "Each task should have a unique ID");
    }

    /**
     * Tests that constructor properly sets the params field.
     */
    @Test
    public void testTaskCreationSetsParams() {
        assertEquals(testParams, task.getParams(), 
                "Task params should match constructor argument");
    }

    /**
     * Tests that constructor properly sets the priority field.
     */
    @Test
    public void testTaskCreationSetsPriority() {
        assertEquals(testPriority, task.getPriority(), 
                "Task priority should match constructor argument");
    }

    /**
     * Tests that new tasks are initialized with PENDING status.
     */
    @Test
    public void testTaskCreationSetsInitialStatus() {
        assertEquals(TaskStatus.PENDING, task.getStatus(), 
                "New task should have PENDING status");
    }

    /**
     * Tests that setStatus successfully updates task status.
     */
    @Test
    public void testSetStatus() {
        task.setStatus(TaskStatus.IN_PROGRESS);
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus(), 
                "Task status should be updated to IN_PROGRESS");
    }

    /**
     * Tests status transitions through the complete lifecycle.
     */
    @Test
    public void testStatusLifecycle() {
        // PENDING -> IN_PROGRESS
        task.setStatus(TaskStatus.IN_PROGRESS);
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());

        // IN_PROGRESS -> COMPLETED
        task.setStatus(TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
    }

    /**
     * Tests status transition to FAILED.
     */
    @Test
    public void testStatusFailure() {
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStatus(TaskStatus.FAILED);
        assertEquals(TaskStatus.FAILED, task.getStatus(), 
                "Task status should be updated to FAILED");
    }

    /**
     * Tests that compareTo orders tasks by priority correctly.
     * Lower priority values should come first (higher priority).
     */
    @Test
    public void testCompareToWithDifferentPriorities() {
        Task highPriorityTask = new Task(testParams, 1);
        Task lowPriorityTask = new Task(testParams, 5);

        assertTrue(highPriorityTask.compareTo(lowPriorityTask) < 0, 
                "Task with priority 1 should come before priority 5");
        assertTrue(lowPriorityTask.compareTo(highPriorityTask) > 0, 
                "Task with priority 5 should come after priority 1");
    }

    /**
     * Tests that compareTo returns 0 for tasks with equal priority.
     */
    @Test
    public void testCompareToWithEqualPriorities() {
        Task task1 = new Task(testParams, 3);
        Task task2 = new Task(testParams, 3);

        assertEquals(0, task1.compareTo(task2), 
                "Tasks with equal priority should compare as equal");
    }

    /**
     * Tests compareTo with priority 0 (highest priority).
     */
    @Test
    public void testCompareToWithZeroPriority() {
        Task highestPriorityTask = new Task(testParams, 0);
        Task normalPriorityTask = new Task(testParams, 1);

        assertTrue(highestPriorityTask.compareTo(normalPriorityTask) < 0, 
                "Task with priority 0 should have highest priority");
    }

    /**
     * Tests compareTo with negative priority values.
     */
    @Test
    public void testCompareToWithNegativePriority() {
        Task negativePriorityTask = new Task(testParams, -1);
        Task positivePriorityTask = new Task(testParams, 1);

        assertTrue(negativePriorityTask.compareTo(positivePriorityTask) < 0, 
                "Task with negative priority should come before positive priority");
    }

    /**
     * Tests that toString includes key task information.
     */
    @Test
    public void testToString() {
        String taskString = task.toString();
        
        assertTrue(taskString.contains("Task{"), 
                "toString should start with 'Task{'");
        assertTrue(taskString.contains("id="), 
                "toString should include task ID");
        assertTrue(taskString.contains("priority="), 
                "toString should include priority");
        assertTrue(taskString.contains("status="), 
                "toString should include status");
        assertTrue(taskString.contains("params="), 
                "toString should include params");
    }

    /**
     * Tests that toString reflects updated status.
     */
    @Test
    public void testToStringWithUpdatedStatus() {
        task.setStatus(TaskStatus.COMPLETED);
        String taskString = task.toString();
        
        assertTrue(taskString.contains("COMPLETED"), 
                "toString should reflect updated status");
    }

    /**
     * Tests task creation with empty params string.
     */
    @Test
    public void testTaskWithEmptyParams() {
        Task emptyParamsTask = new Task("", 1);
        assertEquals("", emptyParamsTask.getParams(), 
                "Task should accept empty params string");
    }

    /**
     * Tests task creation with complex JSON params.
     */
    @Test
    public void testTaskWithComplexParams() {
        String complexParams = "{\"modelType\": \"CNN\", \"epochs\": 50, "
                + "\"learningRate\": 0.001, \"dataset\": \"imagenet\"}";
        Task complexTask = new Task(complexParams, 2);
        
        assertEquals(complexParams, complexTask.getParams(), 
                "Task should correctly store complex JSON params");
    }

    /**
     * Tests that getId returns consistent value across multiple calls.
     */
    @Test
    public void testGetIdConsistency() {
        assertEquals(task.getId(), task.getId(), 
                "getId should return the same value on repeated calls");
    }

    /**
     * Tests that priority getter returns exact constructor value.
     */
    @Test
    public void testGetPriorityWithVariousValues() {
        Task task0 = new Task(testParams, 0);
        Task task100 = new Task(testParams, 100);
        Task taskNegative = new Task(testParams, -5);

        assertEquals(0, task0.getPriority());
        assertEquals(100, task100.getPriority());
        assertEquals(-5, taskNegative.getPriority());
    }

    /**
     * Tests task creation with null params.
     * This tests how the system handles invalid input.
     */
    @Test
    public void testTaskWithNullParams() {
        Task nullParamsTask = new Task(null, 1);
        assertEquals(null, nullParamsTask.getParams(), 
                "Task should accept null params");
    }

    /**
     * Tests setStatus with null value.
     * This tests how the system handles invalid status updates.
     */
    @Test
    public void testSetStatusWithNull() {
        task.setStatus(null);
        assertEquals(null, task.getStatus(), 
                "Task should accept null status");
    }

    /**
     * Tests compareTo with task comparing to itself.
     */
    @Test
    public void testCompareToSelf() {
        assertEquals(0, task.compareTo(task), 
                "Task compared to itself should return 0");
    }

    /**
     * Tests all possible status values.
     */
    @Test
    public void testAllStatusValues() {
        task.setStatus(TaskStatus.PENDING);
        assertEquals(TaskStatus.PENDING, task.getStatus());
        
        task.setStatus(TaskStatus.IN_PROGRESS);
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        
        task.setStatus(TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        
        task.setStatus(TaskStatus.FAILED);
        assertEquals(TaskStatus.FAILED, task.getStatus());
    }
}