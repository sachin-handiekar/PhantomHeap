package com.sachinhandiekar.phantomheap.memory;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ForeignMemoryUnitManagerTest {
    private ForeignMemoryManager manager;
    private static final long CAPACITY = 1024 * 1024; // 1MB

    @BeforeEach
    void setUp() {
        manager = new ForeignMemoryManager(CAPACITY);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (manager != null) {
            manager.close();
        }
    }

    @Test
    void testAllocateAndWrite() {
        byte[] testData = "Hello, World!".getBytes();
        MemoryPointer pointer = manager.allocate(testData.length);
        
        assertNotNull(pointer);
        assertEquals(testData.length, pointer.getSize());
        
        manager.write(pointer, testData);
        byte[] readData = manager.read(pointer);
        
        assertArrayEquals(testData, readData);
    }

    @Test
    void testMemoryLimit() {
        assertThrows(OutOfMemoryError.class, () -> {
            manager.allocate((int) (CAPACITY + 1));
        });
    }

    @Test
    void testMultipleAllocations() {
        int size = 1024; // 1KB
        int count = 10;
        
        for (int i = 0; i < count; i++) {
            MemoryPointer pointer = manager.allocate(size);
            assertNotNull(pointer);
            assertEquals(size, pointer.getSize());
            
            byte[] data = new byte[size];
            // Fill with some test data
            for (int j = 0; j < size; j++) {
                data[j] = (byte) (i % 256);
            }
            
            manager.write(pointer, data);
            byte[] readData = manager.read(pointer);
            assertArrayEquals(data, readData);
            
            manager.free(pointer);
        }
    }

    @Test
    void testUsedMemoryTracking() {
        int size = 1024;
        MemoryPointer pointer = manager.allocate(size);
        
        assertEquals(size, manager.getUsedMemory());
        
        manager.free(pointer);
        assertEquals(0, manager.getUsedMemory());
    }
}
