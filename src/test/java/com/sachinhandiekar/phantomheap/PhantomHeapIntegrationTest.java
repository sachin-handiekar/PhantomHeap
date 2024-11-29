package com.sachinhandiekar.phantomheap;

import com.sachinhandiekar.phantomheap.config.PhantomHeapSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhantomHeapIntegrationTest {
    private static final long CAPACITY = 10 * 1024 * 1024; // 10MB
    private static final String HYBRID_FILE = "test_hybrid_integration.mm";

    @AfterEach
    void cleanup() {
        new File(HYBRID_FILE).delete();
    }

    @Test
    void testForeignMemoryHeap() throws Exception {
        // Create a heap using Foreign Memory
        try (PhantomHeap heap = PhantomHeap.create()) {
            // Store and retrieve a simple string
            String testString = "Hello from Foreign Memory!";
            long id = heap.store(testString);
            String retrieved = heap.retrieve(id);
            assertEquals(testString, retrieved);

            // Store and retrieve a complex object
            TestObject obj = new TestObject("test", 42, new int[]{1, 2, 3});
            long objId = heap.store(obj);
            TestObject retrievedObj = heap.retrieve(objId);
            assertEquals(obj, retrievedObj);

            // Test multiple objects
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                TestObject temp = new TestObject("test" + i, i, new int[]{i, i + 1});
                ids.add(heap.store(temp));
            }

            // Verify all objects
            for (int i = 0; i < ids.size(); i++) {
                TestObject retrieved2 = heap.retrieve(ids.get(i));
                assertEquals("test" + i, retrieved2.getName());
                assertEquals(i, retrieved2.getValue());
            }
        }
    }

    @Test
    void testHybridMemoryHeap() throws Exception {

        // Create a heap using Hybrid Memory with 50% memory threshold
        try (PhantomHeap heap = PhantomHeap.create()) {

            // Test with small objects (should be stored in memory)
            for (int i = 0; i < 10; i++) {
                TestObject small = new TestObject("small" + i, i, new int[]{i});
                long id = heap.store(small);
                TestObject retrieved = heap.retrieve(id);
                assertEquals(small, retrieved);
            }

            // Test with large objects (should be stored in file)
            byte[] largeData = new byte[1024 * 1024]; // 1MB
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i % 256);
            }
            for (int i = 0; i < 5; i++) {
                TestObject large = new TestObject("large" + i, i, largeData);
                long id = heap.store(large);
                TestObject retrieved = heap.retrieve(id);
                assertEquals(large, retrieved);
            }
        }
    }

    // Test object class for serialization/deserialization
    private static class TestObject implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final int value;
        private final byte[] data;

        public TestObject(String name, int value, byte[] data) {
            this.name = name;
            this.value = value;
            this.data = data != null ? data.clone() : null;
        }

        public TestObject(String name, int value, int[] intData) {
            this.name = name;
            this.value = value;
            if (intData != null) {
                this.data = new byte[intData.length];
                for (int i = 0; i < intData.length; i++) {
                    this.data[i] = (byte) intData[i];
                }
            } else {
                this.data = null;
            }
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestObject that = (TestObject) o;
            return value == that.value &&
                    Objects.equals(name, that.name) &&
                    Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(name, value);
            result = 31 * result + Arrays.hashCode(data);
            return result;
        }
    }
}
