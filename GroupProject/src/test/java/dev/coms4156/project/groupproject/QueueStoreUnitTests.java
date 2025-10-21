package dev.coms4156.project.groupproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.groupproject.model.Queue;
import dev.coms4156.project.groupproject.model.QueueStore;
import dev.coms4156.project.groupproject.model.Result;
import dev.coms4156.project.groupproject.model.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * This class contains the unit tests for the QueueStore class.
 */
@SpringBootTest
public class QueueStoreUnitTests {
    private QueueStore store;

    @BeforeEach
    void setUp() {
        store = QueueStore.getInstance();
        store.clearAll();
    }

    @Test
    void testSingletonInstance() {
        QueueStore store1 = QueueStore.getInstance();
        QueueStore store2 = QueueStore.getInstance();
        assertSame(store1, store2, "QueueStore return the same instance");
    }

    @Test
    void testCreateQueue() {
        Queue q1 = store.createQueue("Queue 1");
        assertNotNull(q1);
        assertEquals("Queue 1", q1.getName());
        assertNotNull(store.getQueue(q1.getId()));
    }

    @Test
    void testRemoveQueueSuccess() {
        Queue q = store.createQueue("Remove");
        assertTrue(store.removeQueue(q.getId()));
        assertNull(store.getQueue(q.getId()));
    }

    @Test
    void testRemoveQueueFailure() {
        assertFalse(store.removeQueue("none"));
    }
}