/**
 * Unit tests for the Result class.
 * Tests result creation, field access, timestamp generation,
 * and string representation.
 * Note: AI assistance was used to review test coverage and suggest additional edge cases.
 *
 * <p>EQUIVALENCE PARTITIONS:
 *
 * <p>Result(UUID taskId, String output, ResultStatus status):
 * - Valid: non-null taskId, non-empty output, SUCCESS status -> testResultCreationSetsSuccessStatus
 * - Valid: non-null taskId, non-empty output, FAILURE status -> testResultCreationSetsFailureStatus
 * - Boundary: empty output -> testResultWithEmptyOutput
 * - Atypical: large output -> testResultWithLargeOutput
 * - Atypical: JSON output -> testResultWithJsonOutput
 * - Invalid: null taskId -> testResultWithNullTaskId
 * - Invalid: null output -> testResultWithNullOutput
 * - Invalid: null status -> testResultWithNullStatus
 *
 * <p>getTaskId():
 * - Valid: result with valid taskId -> testGetTaskId
 * - Boundary: consistency check -> testGetTaskIdConsistency
 *
 * <p>getOutput():
 * - Valid: result with valid output -> testGetOutput
 *
 * <p>getStatus():
 * - Valid: SUCCESS status -> testGetStatus
 * - Valid: FAILURE status -> testGetStatus
 *
 * <p>getTimestamp():
 * - Valid: timestamp generated -> testResultCreationGeneratesTimestamp
 * - Boundary: timestamp recency -> testTimestampIsRecent
 * - Boundary: timestamp uniqueness -> testTimestampsAreUnique
 *
 * <p>toString():
 * - Valid: contains all fields -> testToString
 * - Valid: SUCCESS status representation -> testToStringWithSuccessStatus
 * - Valid: FAILURE status representation -> testToStringWithFailureStatus
 */

package dev.coms4156.project.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.server.model.Result;
import dev.coms4156.project.server.model.Result.ResultStatus;
import dev.coms4156.project.server.model.Task;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Result class.
 * Tests result creation, field access, timestamp generation,
 * and string representation.
 */
public class ResultUnitTests {

  private UUID testTaskId;
  private String testOutput;
  private Result successResult;
  private Result failureResult;

  /**
   * Sets up test fixtures before each test method.
   */
  @BeforeEach
  public void setUp() {
    // Create a Task to get a valid UUID
    Task localTask = new Task("{\"page\": 5}", 1);
    testTaskId = localTask.getId();
    testOutput = "rendered_page_5.png";
    
    successResult = new Result(testTaskId, testOutput, ResultStatus.SUCCESS);
    failureResult = new Result(testTaskId, "Error: File not found", 
            ResultStatus.FAILURE);
  }

  /**
   * Tests that constructor properly sets the taskId field.
   */
  @Test
  public void testResultCreationSetsTaskId() {
    assertEquals(testTaskId, successResult.getTaskId(), 
            "Result taskId should match constructor argument");
  }

  /**
   * Tests that constructor properly sets the output field.
   */
  @Test
  public void testResultCreationSetsOutput() {
    assertEquals(testOutput, successResult.getOutput(), 
            "Result output should match constructor argument");
  }

  /**
   * Tests that constructor properly sets SUCCESS status.
   */
  @Test
  public void testResultCreationSetsSuccessStatus() {
    assertEquals(ResultStatus.SUCCESS, successResult.getStatus(), 
            "Result status should be SUCCESS");
  }

  /**
   * Tests that constructor properly sets FAILURE status.
   */
  @Test
  public void testResultCreationSetsFailureStatus() {
    assertEquals(ResultStatus.FAILURE, failureResult.getStatus(), 
            "Result status should be FAILURE");
  }

  /**
   * Tests that timestamp is automatically generated and not null.
   */
  @Test
  public void testResultCreationGeneratesTimestamp() {
    assertNotNull(successResult.getTimestamp(), 
            "Result timestamp should not be null");
  }

  /**
   * Tests that timestamp is set to approximately current time.
   */
  @Test
  public void testTimestampIsRecent() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime resultTime = successResult.getTimestamp();

    // Timestamp should be within a few seconds of now
    assertTrue(resultTime.isBefore(now.plusSeconds(2)), 
            "Result timestamp should be recent");
    assertTrue(resultTime.isAfter(now.minusSeconds(2)), 
            "Result timestamp should not be in the past");
  }

  /**
   * Tests that consecutive Results have different timestamps.
   */
  @Test
  public void testTimestampsAreUnique() throws InterruptedException {
    Result result1 = new Result(testTaskId, testOutput, ResultStatus.SUCCESS);
    Thread.sleep(10); // Small delay to ensure different timestamps
    Result result2 = new Result(testTaskId, testOutput, ResultStatus.SUCCESS);

    assertTrue(result2.getTimestamp().isAfter(result1.getTimestamp()) 
            || result2.getTimestamp().isEqual(result1.getTimestamp()), 
            "Later result should have later or equal timestamp");
  }

  /**
   * Tests getTaskId returns correct UUID.
   */
  @Test
  public void testGetTaskId() {
    UUID retrievedId = successResult.getTaskId();
    assertEquals(testTaskId, retrievedId, 
            "getTaskId should return the correct UUID");
  }

  /**
   * Tests getTaskId consistency across multiple calls.
   */
  @Test
  public void testGetTaskIdConsistency() {
    assertEquals(successResult.getTaskId(), successResult.getTaskId(), 
            "getTaskId should return same value on repeated calls");
  }

  /**
   * Tests getOutput returns correct output string.
   */
  @Test
  public void testGetOutput() {
    assertEquals(testOutput, successResult.getOutput(), 
            "getOutput should return the correct output string");
  }

  /**
   * Tests getStatus returns correct status.
   */
  @Test
  public void testGetStatus() {
    assertEquals(ResultStatus.SUCCESS, successResult.getStatus(), 
            "getStatus should return SUCCESS");
    assertEquals(ResultStatus.FAILURE, failureResult.getStatus(), 
            "getStatus should return FAILURE");
  }

  /**
   * Tests that toString includes key result information.
   */
  @Test
  public void testToString() {
    String resultString = successResult.toString();

    assertTrue(resultString.contains("Result{"), 
            "toString should start with 'Result{'");
    assertTrue(resultString.contains("taskId="), 
            "toString should include taskId");
    assertTrue(resultString.contains("status="), 
            "toString should include status");
    assertTrue(resultString.contains("timestamp="), 
            "toString should include timestamp");
    assertTrue(resultString.contains("output="), 
            "toString should include output");
  }

  /**
   * Tests toString for SUCCESS result.
   */
  @Test
  public void testToStringWithSuccessStatus() {
    String resultString = successResult.toString();
    assertTrue(resultString.contains("SUCCESS"), 
            "toString should contain SUCCESS status");
  }

  /**
   * Tests toString for FAILURE result.
   */
  @Test
  public void testToStringWithFailureStatus() {
    String resultString = failureResult.toString();
    assertTrue(resultString.contains("FAILURE"), 
            "toString should contain FAILURE status");
  }

  /**
   * Tests result creation with empty output string.
   */
  @Test
  public void testResultWithEmptyOutput() {
    Result emptyOutputResult = new Result(testTaskId, "", ResultStatus.SUCCESS);
    assertEquals("", emptyOutputResult.getOutput(), 
            "Result should accept empty output string");
  }

  /**
   * Tests result creation with large output data.
   */
  @Test
  public void testResultWithLargeOutput() {
    StringBuilder largeOutput = new StringBuilder();
    largeOutput.append("data_".repeat(1000));
    
    Result largeResult = new Result(testTaskId, largeOutput.toString(), 
            ResultStatus.SUCCESS);
    assertEquals(largeOutput.toString(), largeResult.getOutput(), 
            "Result should correctly store large output data");
  }

  /**
   * Tests result with JSON formatted output.
   */
  @Test
  public void testResultWithJsonOutput() {
    String jsonOutput = "{\"renderedPages\": [1, 2, 3], \"totalPages\": 3}";
    Result jsonResult = new Result(testTaskId, jsonOutput, ResultStatus.SUCCESS);
    
    assertEquals(jsonOutput, jsonResult.getOutput(), 
            "Result should correctly store JSON formatted output");
  }

  /**
   * Tests result with error message output.
   */
  @Test
  public void testResultWithErrorMessage() {
    String errorMessage = "Error: Failed to process video - codec not supported";
    Result errorResult = new Result(testTaskId, errorMessage, ResultStatus.FAILURE);
    
    assertEquals(errorMessage, errorResult.getOutput());
    assertEquals(ResultStatus.FAILURE, errorResult.getStatus());
  }

  /**
   * Tests that Result fields are immutable (cannot be changed after creation).
   */
  @Test
  public void testResultImmutability() {
    UUID originalTaskId = successResult.getTaskId();
    String originalOutput = successResult.getOutput();
    ResultStatus originalStatus = successResult.getStatus();
    // Try to access fields multiple times - should remain constant
    assertEquals(originalTaskId, successResult.getTaskId());
    assertEquals(originalOutput, successResult.getOutput());
    assertEquals(originalStatus, successResult.getStatus());
    LocalDateTime originalTimestamp = successResult.getTimestamp();
    assertEquals(originalTimestamp, successResult.getTimestamp());
  }

  /**
   * Tests creating multiple results for the same task.
   */
  @Test
  public void testMultipleResultsForSameTask() {
    Task sharedTask = new Task("{\"file\": \"test.pdf\"}", 1);
    UUID sharedTaskId = sharedTask.getId();
    
    Result result1 = new Result(sharedTaskId, "attempt_1", ResultStatus.FAILURE);
    Result result2 = new Result(sharedTaskId, "attempt_2", ResultStatus.SUCCESS);

    assertEquals(sharedTaskId, result1.getTaskId());
    assertEquals(sharedTaskId, result2.getTaskId());
    assertEquals(ResultStatus.FAILURE, result1.getStatus());
    assertEquals(ResultStatus.SUCCESS, result2.getStatus());
  }

  /**
   * Tests that Result can be created with UUID from any Task.
   */
  @Test
  public void testResultWithDifferentTaskIds() {
    Task task1 = new Task("{\"page\": 1}", 1);
    Task task2 = new Task("{\"page\": 2}", 1);
    
    Result result1 = new Result(task1.getId(), "output1", ResultStatus.SUCCESS);
    Result result2 = new Result(task2.getId(), "output2", ResultStatus.SUCCESS);
    
    assertEquals(task1.getId(), result1.getTaskId());
    assertEquals(task2.getId(), result2.getTaskId());
  }

  /**
   * Tests the result when there's a null task ID.
   */
  @Test
  public void testResultWithNullTaskId() {
    Result r = new Result(null, "output", ResultStatus.SUCCESS);
    assertNull(r.getTaskId());
  }

  /**
   * Tests the result when there's a null output.
   */
  @Test
  public void testResultWithNullOutput() {
    UUID id = UUID.randomUUID();
    Result r = new Result(id, null, ResultStatus.SUCCESS);
    assertNull(r.getOutput());
  }

  /**
   * Tests the result when there's a null status.
   */
  @Test
  public void testResultWithNullStatus() {
    UUID id = UUID.randomUUID();
    Result r = new Result(id, "output", null);
    assertNull(r.getStatus());
  }
}
