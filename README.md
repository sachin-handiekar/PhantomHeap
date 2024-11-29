# PhantomHeap

[![Build Status](https://travis-ci.org/yourusername/phantomheap.svg?branch=main)](https://travis-ci.org/yourusername/phantomheap)
[![codecov](https://codecov.io/gh/yourusername/phantomheap/branch/main/graph/badge.svg)](https://codecov.io/gh/yourusername/phantomheap)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

PhantomHeap is an experimental off-heap object storage library for Java, designed to manage large amounts of data outside the JVM heap. It provides flexible memory management strategies and efficient eviction policies, making it ideal for applications that need to handle large datasets while minimizing garbage collection overhead.

## Features

- **Off-Heap Storage**: Store data outside the JVM heap using Java's Foreign Memory API
- **Flexible Memory Management**: Choose between different memory management strategies
  - `ForeignMemoryManager`: Direct off-heap memory using Java 21+ Foreign Memory API
  - `HybridMemoryManager`: Combined memory and file-based storage for larger datasets
- **Configurable Eviction**: Built-in support for memory eviction policies
  - LRU (Least Recently Used) implementation included
  - Extensible interface for custom eviction policies
- **Performance Monitoring**: Built-in metrics using Micrometer
- **Thread Safety**: All operations are thread-safe for concurrent access
- **Resource Management**: Automatic cleanup of resources using try-with-resources
- **User-Friendly API**: Simple and intuitive builder pattern for configuration

## Requirements

- Java 23+
- Maven 3.6 or later

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.sachinhandiekar</groupId>
    <artifactId>phantomheap</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

Here's a simple example to get you started:

```java
// Create a PhantomHeap with 2GB capacity
try (PhantomHeap heap = PhantomHeap.create(Memory.GB(2))) {
    // Store some data
    String data = "Hello, World!";
    long id = heap.store(data);
    
    // Retrieve it later
    String retrieved = heap.retrieve(id);
    System.out.println(retrieved); // Prints: Hello, World!
    
    // Free when no longer needed
    heap.free(id);
}
```

## Advanced Configuration

PhantomHeap offers flexible configuration options using the builder pattern:

```java
// Create custom settings
PhantomHeapSettings settings = PhantomHeapSettings.builder()
    .memoryCapacity(Memory.GB(2))         // 2GB memory
    .evictionThreshold(0.75)              // Evict at 75% capacity
    .cleanupIntervalMs(30_000)            // Cleanup every 30 seconds
    .threadPoolSize(4)                    // Use 4 threads for background tasks
    .build();

// Create heap with custom settings
try (PhantomHeap heap = PhantomHeap.create(settings)) {
    // Use the heap...
}
```

### Memory Specification

Use the `Memory` utility class for readable memory size specification:

```java
// Different ways to specify memory sizes
long twoGB = Memory.GB(2);    // 2 gigabytes
long fiveMB = Memory.MB(500); // 500 megabytes
long twoKB = Memory.KB(2);    // 2 kilobytes
long bytes = Memory.bytes(1024); // 1024 bytes

// Format memory sizes for display
System.out.println(Memory.formatSize(Memory.GB(2))); // "2.00 GB"
```

### Custom Eviction Policies

Implement the `EvictionPolicy` interface to create custom eviction strategies:

```java
public class CustomEvictionPolicy implements EvictionPolicy {
    @Override
    public void recordAccess(long id, int size) {
        // Track access patterns
    }

    @Override
    public void recordRemoval(long id) {
        // Handle removal
    }

    @Override
    public long getNextEviction() {
        // Determine next item to evict
        return nextId;
    }

    @Override
    public boolean shouldEvict(long usedMemory, long totalMemory) {
        // Determine if eviction is needed
        return usedMemory > totalMemory * 0.75;
    }
}

// Use custom policy
settings = PhantomHeapSettings.builder()
    .evictionPolicy(new CustomEvictionPolicy())
    .build();
```

## Performance Monitoring

PhantomHeap integrates with Micrometer to provide metrics:

- `phantomheap.operations`: Counter for store/retrieve operations
- `phantomheap.evictions`: Counter for eviction events
- `phantomheap.latency`: Timer for operation latencies
- `phantomheap.memory.used`: Gauge for current memory usage

## Thread Safety

All PhantomHeap operations are thread-safe and can be accessed concurrently from multiple threads. The implementation uses:
- Atomic operations for counters
- Concurrent collections for internal state
- Read-write locks for memory access
- Thread-safe eviction policies