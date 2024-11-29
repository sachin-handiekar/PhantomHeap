package com.sachinhandiekar.phantomheap;

import com.sachinhandiekar.phantomheap.config.PhantomHeapSettings;
import com.sachinhandiekar.phantomheap.eviction.EvictionPolicy;
import com.sachinhandiekar.phantomheap.memory.MemoryUnit;
import com.sachinhandiekar.phantomheap.memory.MemoryManagerStrategy;
import com.sachinhandiekar.phantomheap.memory.MemoryPointer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PhantomHeap Tests")
@Disabled
class PhantomHeapTest {
    @Mock
    private EvictionPolicy evictionPolicy;
    
    private TestMemoryManager memoryManager;
    private PhantomHeap heap;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        memoryManager = new TestMemoryManager(MemoryUnit.GB(1));
        
        PhantomHeapSettings settings = PhantomHeapSettings.builder()
            .cleanupIntervalMs(60_000)
            .memoryManager(memoryManager)
            .evictionPolicy(evictionPolicy)
            .build();
        
        heap = PhantomHeap.create(settings);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (heap != null) {
            heap.close();
        }
    }

    // Simple test implementation of MemoryManagerStrategy
    private static class TestMemoryManager implements MemoryManagerStrategy {
        private final long capacity;
        private long usedMemory;
        private long nextAddress = 1;
        private final Map<Long, byte[]> memory = new HashMap<>();
        private final Object lock = new Object();

        TestMemoryManager(long capacity) {
            this.capacity = capacity;
        }

        @Override
        public MemoryPointer allocate(int size) {
            synchronized(lock) {
                if (size <= 0) {
                    throw new IllegalArgumentException("Size must be positive");
                }
                if (usedMemory + size > capacity) {
                    throw new OutOfMemoryError("Not enough memory");
                }
                long address = nextAddress++;
                usedMemory += size;
                return new MemoryPointer(address, size);
            }
        }

        @Override
        public void write(MemoryPointer pointer, byte[] data) {
            synchronized(lock) {
                Objects.requireNonNull(pointer, "Pointer cannot be null");
                Objects.requireNonNull(data, "Data cannot be null");
                validatePointer(pointer);
                
                if (data.length > pointer.getSize()) {
                    throw new IllegalArgumentException("Data size exceeds allocated block size");
                }
                
                memory.put(getAddress(pointer), data.clone());
            }
        }

        @Override
        public byte[] read(MemoryPointer pointer) {
            synchronized(lock) {
                Objects.requireNonNull(pointer, "Pointer cannot be null");
                validatePointer(pointer);
                
                byte[] data = memory.get(getAddress(pointer));
                if (data == null) {
                    throw new IllegalArgumentException("Memory at pointer has not been written or has been freed");
                }
                
                return data.clone();
            }
        }

        @Override
        public void free(MemoryPointer pointer) {
            synchronized(lock) {
                Objects.requireNonNull(pointer, "Pointer cannot be null");
                validatePointer(pointer);
                
                memory.remove(getAddress(pointer));
                usedMemory -= pointer.getSize();
            }
        }

        @Override
        public long getCapacity() {
            return capacity;
        }

        @Override
        public long getUsedMemory() {
            synchronized(lock) {
                return usedMemory;
            }
        }

        @Override
        public void close() {
            synchronized(lock) {
                memory.clear();
                usedMemory = 0;
            }
        }

        private void validatePointer(MemoryPointer pointer) {
            Objects.requireNonNull(pointer, "Pointer cannot be null");
            
            Object segment = pointer.getSegment();
            if (!(segment instanceof Long)) {
                throw new IllegalArgumentException("Invalid pointer type: expected Long segment");
            }
            
            long address = (Long) segment;
            if (address <= 0 || address >= nextAddress) {
                throw new IllegalArgumentException("Invalid pointer address: " + address);
            }
            
            if (pointer.getSize() <= 0) {
                throw new IllegalArgumentException("Invalid pointer size: " + pointer.getSize());
            }
        }

        private long getAddress(MemoryPointer pointer) {
            validatePointer(pointer);
            return (Long) pointer.getSegment();
        }
    }

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {
        @Test
        @DisplayName("Store and retrieve string")
        void storeAndRetrieveString() throws IOException {
            // Arrange
            TestData testData = new TestData("Test String");

            // Act
            long id = heap.store(testData);
            Object retrieved = heap.retrieve(id);

            // Assert
            assertEquals(testData, retrieved);
            verify(evictionPolicy).recordAccess(eq(id), anyInt());
        }

        @Test
        @DisplayName("Free stored object")
        void freeObject() throws IOException {
            // Arrange
            TestData testData = new TestData("Test String");

            // Act
            long id = heap.store(testData);
            heap.free(id);

            // Assert
            assertNull(heap.retrieve(id));
            verify(evictionPolicy).recordRemoval(id);
        }
    }

    @Nested
    @DisplayName("Memory Management")
    class MemoryUnitManagement {
        @Test
        @DisplayName("Eviction triggered when memory threshold reached")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void evictionTriggered() throws IOException {
            // Arrange
            when(evictionPolicy.shouldEvict(anyLong(), anyLong())).thenReturn(true);
            when(evictionPolicy.getNextEviction()).thenReturn(1L);

            // Store initial object to be evicted
            TestData initialData = new TestData("Initial Data");
            long initialId = heap.store(initialData);

            // Store new object that should trigger eviction
            TestData newData = new TestData("New Data");
            
            // Act
            heap.store(newData);

            // Assert
            verify(evictionPolicy, timeout(1000)).shouldEvict(anyLong(), anyLong());
            verify(evictionPolicy, timeout(1000)).getNextEviction();
            verify(evictionPolicy, timeout(1000)).recordRemoval(eq(initialId));
        }

        @Test
        @DisplayName("Multiple evictions for large object")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void multipleEvictions() throws IOException {
            // Arrange
            TestData largeData = new TestData(new byte[1024 * 1024]); // 1MB
            
            // Configure mock to return true twice then false
            when(evictionPolicy.shouldEvict(anyLong(), anyLong()))
                .thenReturn(true)  // First check - evict data1
                .thenReturn(true)  // Second check - evict data2
                .thenReturn(false); // Final check - no more evictions needed
                
            // Configure mock to return ids in sequence
            when(evictionPolicy.getNextEviction())
                .thenReturn(1L)    // First eviction - data1
                .thenReturn(2L);   // Second eviction - data2

            // Store initial objects to be evicted
            TestData data1 = new TestData("Data 1");
            TestData data2 = new TestData("Data 2");
            long id1 = heap.store(data1);
            long id2 = heap.store(data2);
            
            assertEquals(1L, id1, "First stored object should have ID 1");
            assertEquals(2L, id2, "Second stored object should have ID 2");

            // Act
            heap.store(largeData);

            // Assert - verify eviction sequence
            verify(evictionPolicy, timeout(1000).times(3)).shouldEvict(anyLong(), anyLong());
            verify(evictionPolicy, timeout(1000).times(2)).getNextEviction();
            
            // Verify both objects were evicted
            verify(evictionPolicy, timeout(1000)).recordRemoval(eq(1L));
            verify(evictionPolicy, timeout(1000)).recordRemoval(eq(2L));
            
            // Verify objects are no longer retrievable
            assertNull(heap.retrieve(id1), "Evicted object 1 should not be retrievable");
            assertNull(heap.retrieve(id2), "Evicted object 2 should not be retrievable");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        @Test
        @DisplayName("Store very large object")
        void storeVeryLargeObject() throws IOException {
            // Arrange
            TestData veryLargeData = new TestData(new byte[(int) MemoryUnit.MB(100)]);

            // Act & Assert
            assertDoesNotThrow(() -> heap.store(veryLargeData));
        }
    }

    private static class TestData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Object data;

        TestData(Object data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestData testData = (TestData) o;
            return java.util.Objects.deepEquals(data, testData.data);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hashCode(data);
        }
    }
}
