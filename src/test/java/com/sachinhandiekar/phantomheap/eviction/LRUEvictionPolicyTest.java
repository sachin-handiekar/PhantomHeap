package com.sachinhandiekar.phantomheap.eviction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LRUEvictionPolicyTest {
    private LRUEvictionPolicy policy;
    private static final double EVICTION_THRESHOLD = 0.8; // 80%
    private static final long TOTAL_CAPACITY = 1000;

    @BeforeEach
    void setUp() {
        policy = new LRUEvictionPolicy(EVICTION_THRESHOLD);
    }

    @Test
    void recordAccess_ShouldUpdateAccessTime() {
        // Act
        policy.recordAccess(1L, 100);
        policy.recordAccess(2L, 200);

        // Assert - verify the order of eviction
        assertEquals(1L, policy.getNextEviction());
        policy.recordRemoval(1L);
        assertEquals(2L, policy.getNextEviction());
    }

    @Test
    void recordAccess_ShouldUpdateAccessOrder() {
        // Arrange
        policy.recordAccess(1L, 100);
        policy.recordAccess(2L, 200);
        
        // Act - access item 1 again
        policy.recordAccess(1L, 100);

        // Assert - item 2 should be evicted first now
        assertEquals(2L, policy.getNextEviction());
        policy.recordRemoval(2L);
        assertEquals(1L, policy.getNextEviction());
    }

    @Test
    void shouldEvict_ShouldReturnTrue_WhenThresholdExceeded() {
        // Arrange
        long usedMemory = (long)(TOTAL_CAPACITY * EVICTION_THRESHOLD) + 1;

        // Act & Assert
        assertTrue(policy.shouldEvict(usedMemory, TOTAL_CAPACITY));
    }

    @Test
    void shouldEvict_ShouldReturnFalse_WhenBelowThreshold() {
        // Arrange
        long usedMemory = (long)(TOTAL_CAPACITY * EVICTION_THRESHOLD) - 1;

        // Act & Assert
        assertFalse(policy.shouldEvict(usedMemory, TOTAL_CAPACITY));
    }

    @Test
    void getNextEviction_ShouldReturnMinusOne_WhenNoItems() {
        // Act & Assert
        assertEquals(-1L, policy.getNextEviction());
    }

    @Test
    void evict_ShouldRemoveEvictedItem() {
        // Arrange
        policy.recordAccess(1L, 100);
        
        // Act
        Long evictedId = policy.getNextEviction();
        policy.recordRemoval(evictedId);
        
        // Assert
        assertEquals(1L, evictedId);
        assertEquals(-1L, policy.getNextEviction(), "Second evict should return -1 as item was removed");
    }

    @Test
    void evict_ShouldMaintainLRUOrder() {
        // Arrange
        policy.recordAccess(1L, 100);
        policy.recordAccess(2L, 100);
        policy.recordAccess(3L, 100);
        policy.recordAccess(2L, 100); // Access 2 again

        // Act & Assert
        assertEquals(1L, policy.getNextEviction(), "Oldest item (1) should be evicted first");
        policy.recordRemoval(1L);
        assertEquals(3L, policy.getNextEviction(), "Second oldest item (3) should be evicted second");
        policy.recordRemoval(3L);
        assertEquals(2L, policy.getNextEviction(), "Most recently accessed item (2) should be evicted last");
    }

    @Test
    void constructor_ShouldThrowException_WhenThresholdInvalid() {
        // Assert
        assertThrows(IllegalArgumentException.class, () -> new LRUEvictionPolicy(-0.1));
        assertThrows(IllegalArgumentException.class, () -> new LRUEvictionPolicy(1.1));
    }

    @Test
    void recordAccess_ShouldHandleMultipleAccessesToSameId() {
        // Arrange
        policy.recordAccess(1L, 100);
        policy.recordAccess(2L, 100);
        
        // Act - access item 1 multiple times
        for (int i = 0; i < 5; i++) {
            policy.recordAccess(1L, 100);
        }

        // Assert - item 2 should still be evicted first
        assertEquals(2L, policy.getNextEviction());
        policy.recordRemoval(2L);
        assertEquals(1L, policy.getNextEviction());
    }

    @Test
    void shouldEvict_ShouldConsiderTotalMemoryCapacity() {
        // Arrange
        long usedMemory = 800;
        long totalMemory = 1000;

        // Act & Assert
        assertTrue(policy.shouldEvict(usedMemory, totalMemory), 
            "Should evict when used memory (800) exceeds threshold (80%) of total memory (1000)");
        assertFalse(policy.shouldEvict(700, totalMemory), 
            "Should not evict when used memory (700) is below threshold (80%) of total memory (1000)");
    }
}
