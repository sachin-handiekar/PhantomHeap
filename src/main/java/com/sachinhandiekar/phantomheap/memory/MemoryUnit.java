package com.sachinhandiekar.phantomheap.memory;

/**
 * Utility class for handling memory size specifications.
 * 
 * <p>This class provides convenient methods for specifying memory sizes in different units
 * (bytes, kilobytes, megabytes, gigabytes) and converting between them. It also includes
 * utilities for formatting memory sizes into human-readable strings.</p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Specify memory sizes in different units
 * long twoGB = Memory.GB(2);    // 2 gigabytes in bytes
 * long fiveMB = Memory.MB(5);   // 5 megabytes in bytes
 * long oneKB = Memory.KB(1);    // 1 kilobyte in bytes
 * 
 * // Format memory sizes
 * String formatted = Memory.formatSize(twoGB);  // "2.00 GB"
 * }</pre></p>
 */
public final class MemoryUnit {
    private static final long BYTES_PER_KB = 1024L;
    private static final long BYTES_PER_MB = BYTES_PER_KB * 1024L;
    private static final long BYTES_PER_GB = BYTES_PER_MB * 1024L;

    // Prevent instantiation
    private MemoryUnit() {}

    /**
     * Converts gigabytes to bytes.
     *
     * @param gb Number of gigabytes
     * @return Equivalent number of bytes
     * @throws IllegalArgumentException if gb is negative
     */
    public static long GB(long gb) {
        if (gb < 0) {
            throw new IllegalArgumentException("Memory size cannot be negative");
        }
        return gb * BYTES_PER_GB;
    }

    /**
     * Converts megabytes to bytes.
     *
     * @param mb Number of megabytes
     * @return Equivalent number of bytes
     * @throws IllegalArgumentException if mb is negative
     */
    public static long MB(long mb) {
        if (mb < 0) {
            throw new IllegalArgumentException("Memory size cannot be negative");
        }
        return mb * BYTES_PER_MB;
    }

    /**
     * Converts kilobytes to bytes.
     *
     * @param kb Number of kilobytes
     * @return Equivalent number of bytes
     * @throws IllegalArgumentException if kb is negative
     */
    public static long KB(long kb) {
        if (kb < 0) {
            throw new IllegalArgumentException("Memory size cannot be negative");
        }
        return kb * BYTES_PER_KB;
    }

    /**
     * Converts a byte count to a human-readable string.
     * The result will be formatted with two decimal places and the appropriate unit suffix.
     * 
     * <p>Examples:
     * <ul>
     *   <li>1024 bytes → "1.00 KB"</li>
     *   <li>1536 bytes → "1.50 KB"</li>
     *   <li>2097152 bytes → "2.00 MB"</li>
     *   <li>3221225472 bytes → "3.00 GB"</li>
     * </ul></p>
     *
     * @param bytes Number of bytes
     * @return Formatted string representation
     */
    public static String formatSize(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        if (bytes < BYTES_PER_KB) {
            return bytes + " B";
        }
        if (bytes < BYTES_PER_MB) {
            return String.format("%.2f KB", bytes / (double) BYTES_PER_KB);
        }
        if (bytes < BYTES_PER_GB) {
            return String.format("%.2f MB", bytes / (double) BYTES_PER_MB);
        }
        return String.format("%.2f GB", bytes / (double) BYTES_PER_GB);
    }
}
