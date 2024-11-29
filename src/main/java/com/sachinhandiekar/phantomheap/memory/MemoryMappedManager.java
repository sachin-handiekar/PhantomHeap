package com.sachinhandiekar.phantomheap.memory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Memory manager implementation using com.sachinhandiekar.phantomheap.memory mapped files
 */
public class MemoryMappedManager implements MemoryManagerStrategy {
    private final File backingFile;
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final MappedByteBuffer buffer;
    private final long capacity;
    private final AtomicLong usedMemory;
    
    public MemoryMappedManager(long capacityBytes, String fileName) throws IOException {
        this.capacity = capacityBytes;
        this.usedMemory = new AtomicLong(0);
        this.backingFile = new File(fileName);
        this.raf = new RandomAccessFile(backingFile, "rw");
        this.channel = raf.getChannel();
        this.buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, capacityBytes);
    }
    
    @Override
    public MemoryPointer allocate(int size) {
        synchronized (buffer) {
            if (usedMemory.get() + size > capacity) {
                throw new OutOfMemoryError("Memory mapped file limit exceeded");
            }
            
            int position = buffer.position();
            buffer.position(position + size);
            usedMemory.addAndGet(size);
            
            return new MemoryPointer(position, size);
        }
    }
    
    @Override
    public void write(MemoryPointer pointer, byte[] data) {
        synchronized (buffer) {
            buffer.position((Integer) pointer.getSegment());
            buffer.put(data);
        }
    }
    
    @Override
    public byte[] read(MemoryPointer pointer) {
        synchronized (buffer) {
            buffer.position((Integer) pointer.getSegment());
            byte[] data = new byte[pointer.getSize()];
            buffer.get(data);
            return data;
        }
    }
    
    @Override
    public void free(MemoryPointer pointer) {
        // Memory mapped files don't support true freeing
        // We just zero out the com.sachinhandiekar.phantomheap.memory
        synchronized (buffer) {
            buffer.position((Integer) pointer.getSegment());
            for (int i = 0; i < pointer.getSize(); i++) {
                buffer.put((byte) 0);
            }
        }
        usedMemory.addAndGet(-pointer.getSize());
    }
    
    @Override
    public void close() throws Exception {
        channel.close();
        raf.close();
        backingFile.delete();
    }
    
    @Override
    public long getCapacity() {
        return capacity;
    }
    
    @Override
    public long getUsedMemory() {
        return usedMemory.get();
    }
}
