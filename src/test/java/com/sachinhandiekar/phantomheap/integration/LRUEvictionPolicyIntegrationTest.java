package com.sachinhandiekar.phantomheap.integration;

import com.sachinhandiekar.phantomheap.PhantomHeap;
import com.sachinhandiekar.phantomheap.config.PhantomHeapSettings;
import com.sachinhandiekar.phantomheap.eviction.LRUEvictionPolicy;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LRU Eviction Policy Integration Tests")
@Disabled
class LRUEvictionPolicyIntegrationTest {
    private PhantomHeap heap;
    private static final int SMALL_HEAP_SIZE = 10 * 1024 * 1024; // 10MB
    private static final double EVICTION_THRESHOLD = 0.8; // 80%

    @BeforeEach
    void setUp() {
        LRUEvictionPolicy evictionPolicy = new LRUEvictionPolicy(EVICTION_THRESHOLD);
        PhantomHeapSettings settings = PhantomHeapSettings.builder()
                .memoryCapacity(SMALL_HEAP_SIZE)
                .evictionPolicy(evictionPolicy)
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
    @DisplayName("Should evict least recently used objects when memory is full")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void shouldEvictLeastRecentlyUsedObjects() throws IOException, InterruptedException {
        // Create test data that will fill up memory
        int objectSize = 1024 * 1024; // 1MB
        byte[] data = new byte[objectSize];
        List<Long> storedIds = new ArrayList<>();
        
        // Store objects until we hit the memory limit
        for (int i = 0; i < 8; i++) { // Should store ~8MB
            TestData testData = new TestData("Data " + i, data.clone());
            long id = heap.store(testData);
            storedIds.add(id);
        }
        
        // Access some objects to update their LRU status
        // Access objects in reverse order to make first objects least recently used
        for (int i = storedIds.size() - 1; i >= 2; i--) {
            heap.retrieve(storedIds.get(i));
        }
        
        // Store a new large object that should trigger eviction
        TestData largeData = new TestData("Large Data", new byte[2 * 1024 * 1024]); // 2MB
        long largeDataId = heap.store(largeData);
        
        // Wait for eviction to complete
        Thread.sleep(100);
        
        // Verify that least recently used objects were evicted
        assertNull(heap.retrieve(storedIds.get(0)), "First object should be evicted");
        assertNull(heap.retrieve(storedIds.get(1)), "Second object should be evicted");
        
        // Verify that recently used objects are still present
        for (int i = 2; i < storedIds.size(); i++) {
            assertNotNull(heap.retrieve(storedIds.get(i)), 
                    "Recently used object " + i + " should not be evicted");
        }
        
        // Verify that new object is present
        assertNotNull(heap.retrieve(largeDataId), "Newly stored object should be present");
    }

    @Test
    @DisplayName("Should maintain object access order")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void shouldMaintainAccessOrder() throws IOException, InterruptedException {
        // Store multiple objects
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestData data = new TestData("Data " + i, new byte[1024 * 1024]); // 1MB each
            ids.add(heap.store(data));
        }
        
        // Access objects in specific order
        heap.retrieve(ids.get(0)); // Access first object
        Thread.sleep(10);
        heap.retrieve(ids.get(2)); // Access third object
        Thread.sleep(10);
        heap.retrieve(ids.get(4)); // Access fifth object
        
        // Store a large object to trigger eviction
        TestData largeData = new TestData("Large Data", new byte[5 * 1024 * 1024]); // 5MB
        heap.store(largeData);
        
        // Wait for eviction to complete
        Thread.sleep(100);
        
        // Verify that least recently accessed objects were evicted
        assertNull(heap.retrieve(ids.get(1)), "Unaccessed object 1 should be evicted");
        assertNull(heap.retrieve(ids.get(3)), "Unaccessed object 3 should be evicted");
        
        // Verify that recently accessed objects remain
        assertNotNull(heap.retrieve(ids.get(0)), "Recently accessed object 0 should remain");
        assertNotNull(heap.retrieve(ids.get(2)), "Recently accessed object 2 should remain");
        assertNotNull(heap.retrieve(ids.get(4)), "Recently accessed object 4 should remain");
    }

    @Test
    @DisplayName("Should handle rapid store and retrieve operations")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void shouldHandleRapidOperations() throws IOException {
        List<Long> ids = new ArrayList<>();
        int iterations = 100;
        
        // Rapidly store and retrieve objects
        for (int i = 0; i < iterations; i++) {
            TestData data = new TestData("Data " + i, new byte[50 * 1024]); // 50KB each
            long id = heap.store(data);
            ids.add(id);
            
            // Randomly access some previous objects
            if (i > 0 && i % 10 == 0) {
                for (int j = 0; j < 3; j++) {
                    int index = (int) (Math.random() * ids.size());
                    heap.retrieve(ids.get(index));
                }
            }
        }
        
        // Verify that some objects were evicted and others remain
        int evictedCount = 0;
        int remainingCount = 0;
        
        for (Long id : ids) {
            if (heap.retrieve(id) == null) {
                evictedCount++;
            } else {
                remainingCount++;
            }
        }
        
        assertTrue(evictedCount > 0, "Some objects should be evicted");
        assertTrue(remainingCount > 0, "Some objects should remain");
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
