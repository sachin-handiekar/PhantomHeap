package com.sachinhandiekar.phantomheap.integration;

import com.sachinhandiekar.phantomheap.PhantomHeap;
import com.sachinhandiekar.phantomheap.config.PhantomHeapSettings;
import com.sachinhandiekar.phantomheap.memory.MemoryUnit;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Memory Management Integration Tests")
@Disabled
class MemoryUnitManagementIntegrationTest {
    private PhantomHeap heap;
    private static final int HEAP_SIZE = (int) MemoryUnit.MB(100); // 100MB

    @BeforeEach
    void setUp() {
        PhantomHeapSettings settings = PhantomHeapSettings.builder()
                .memoryCapacity(HEAP_SIZE)
                .cleanupIntervalMs(1000)
                .build();
        
        heap = PhantomHeap.create(settings);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (heap != null) {
            heap.close();
        }
    }

    @Test
    @DisplayName("Should handle concurrent store and retrieve operations")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shouldHandleConcurrentOperations() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        
        // Start threads that will perform concurrent operations
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            // Store object
                            TestData data = new TestData(
                                "Thread-" + threadId + "-Data-" + j,
                                new byte[10 * 1024] // 10KB
                            );
                            long id = heap.store(data);
                            
                            // Retrieve object
                            TestData retrieved = heap.retrieve(id);
                            if (retrieved != null && retrieved.equals(data)) {
                                successfulOperations.incrementAndGet();
                            }
                            
                            // Free object
                            heap.free(id);
                            
                        } catch (OutOfMemoryError e) {
                            // Expected occasionally due to memory pressure
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for completion
        assertTrue(completionLatch.await(25, TimeUnit.SECONDS), 
                "All threads should complete within timeout");
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
                "Executor should shut down cleanly");
        
        // Verify that most operations were successful
        int totalOperations = threadCount * operationsPerThread;
        double successRate = (double) successfulOperations.get() / totalOperations;
        assertTrue(successRate > 0.8, 
                "Success rate should be above 80%, was: " + (successRate * 100) + "%");
    }

    @Test
    @DisplayName("Should handle large objects near capacity")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void shouldHandleLargeObjects() throws IOException {
        int largeObjectSize = (int) (HEAP_SIZE * 0.4); // 40% of heap size
        List<Long> storedIds = new ArrayList<>();
        
        // Store multiple large objects
        for (int i = 0; i < 2; i++) {
            TestData data = new TestData("Large-" + i, new byte[largeObjectSize]);
            long id = heap.store(data);
            storedIds.add(id);
        }
        
        // Verify objects were stored
        for (Long id : storedIds) {
            assertNotNull(heap.retrieve(id), "Large object should be retrievable");
        }
        
        // Attempt to store another large object should fail
        TestData overflowData = new TestData("Overflow", new byte[largeObjectSize]);
        assertThrows(OutOfMemoryError.class, () -> heap.store(overflowData),
                "Storing beyond capacity should throw OutOfMemoryError");
        
        // Free one object
        heap.free(storedIds.get(0));
        
        // Should now be able to store another large object
        TestData newData = new TestData("New-Large", new byte[largeObjectSize]);
        long newId = heap.store(newData);
        assertNotNull(heap.retrieve(newId), "New large object should be stored after freeing space");
    }

    @Test
    @DisplayName("Should maintain memory consistency under load")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void shouldMaintainMemoryConsistency() throws IOException {
        int iterations = 1000;
        int objectSize = 50 * 1024; // 50KB
        List<Long> ids = new ArrayList<>();
        
        // Store many objects
        for (int i = 0; i < iterations; i++) {
            TestData data = new TestData("Data-" + i, new byte[objectSize]);
            ids.add(heap.store(data));
            
            // Periodically verify and free some objects
            if (i > 0 && i % 100 == 0) {
                // Verify random objects
                for (int j = 0; j < 10; j++) {
                    int index = (int) (Math.random() * ids.size());
                    Long id = ids.get(index);
                    TestData retrieved = heap.retrieve(id);
                    assertNotNull(retrieved, "Object should be retrievable");
                    assertEquals("Data-" + index, retrieved.name,
                            "Retrieved object should have correct data");
                }
                
                // Free some objects
                for (int j = 0; j < 50; j++) {
                    int index = (int) (Math.random() * ids.size());
                    heap.free(ids.get(index));
                }
            }
        }
        
        // Verify heap capacity and used memory
        assertTrue(heap.getUsedMemory() <= heap.getCapacity(),
                "Used memory should not exceed capacity");
    }

    private static class TestData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final byte[] data;

        TestData(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestData testData = (TestData) o;
            return name.equals(testData.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
