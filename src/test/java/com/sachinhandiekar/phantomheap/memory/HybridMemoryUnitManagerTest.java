package com.sachinhandiekar.phantomheap.memory;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.io.IOException;

class HybridMemoryUnitManagerTest {
    private HybridMemoryManager manager;
    private static final long MEMORY_CAPACITY = 1024 * 1024; // 1MB
    private static final String TEST_FILE = "test_hybrid_storage.mm";
    private static final double MEMORY_THRESHOLD = 0.5; // 50% memory threshold

    @BeforeEach
    void setUp() throws IOException {
        manager = new HybridMemoryManager(MEMORY_CAPACITY, TEST_FILE, MEMORY_THRESHOLD);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (manager != null) {
            manager.close();
        }
        new File(TEST_FILE).delete();
    }

    @Test
    void testMemoryStorage() {
        // Small allocation should go to memory
        int smallSize = 1024; // 1KB
        byte[] testData = new byte[smallSize];
        for (int i = 0; i < smallSize; i++) {
            testData[i] = (byte) (i % 256);
        }

        MemoryPointer pointer = manager.allocate(smallSize);
        assertNotNull(pointer);
        
        manager.write(pointer, testData);
        byte[] readData = manager.read(pointer);
        
        assertArrayEquals(testData, readData);
    }

    @Test
    void testFileStorage() {
        // Large allocation that exceeds memory threshold
        int largeSize = (int) (MEMORY_CAPACITY * 0.6); // 60% of memory capacity
        byte[] testData = new byte[largeSize];
        for (int i = 0; i < largeSize; i++) {
            testData[i] = (byte) (i % 256);
        }

        MemoryPointer pointer = manager.allocate(largeSize);
        assertNotNull(pointer);
        
        manager.write(pointer, testData);
        byte[] readData = manager.read(pointer);
        
        assertArrayEquals(testData, readData);
    }

    @Test
    void testMemoryThreshold() {
        // Fill up to threshold
        int allocationSize = (int) (MEMORY_CAPACITY * MEMORY_THRESHOLD * 0.9); // 90% of threshold
        MemoryPointer pointer1 = manager.allocate(allocationSize);
        assertNotNull(pointer1);

        // Next allocation should go to file
        MemoryPointer pointer2 = manager.allocate(allocationSize);
        assertNotNull(pointer2);

        manager.free(pointer1);
        manager.free(pointer2);
    }

    @Test
    void testCapacityAndUsage() {
        assertEquals(MEMORY_CAPACITY, manager.getCapacity());
        
        int size = 1024;
        MemoryPointer pointer = manager.allocate(size);
        
        assertTrue(manager.getUsedMemory() > 0);
        assertTrue(manager.getUsedMemory() <= MEMORY_CAPACITY);
        
        manager.free(pointer);
    }
}
