package com.hasandag.exchange.conversion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class OptimizedFileContentStoreService {

    @Value("${conversion.content-store.max-size:100}")
    private int maxStoreSize;

    @Value("${conversion.content-store.max-age:3600000}")
    private long maxAgeMs;

    private static final Map<String, ContentEntry> FILE_CONTENT_STORE = new ConcurrentHashMap<>();
    private final AtomicLong totalMemoryUsage = new AtomicLong(0);

    private static class ContentEntry {
        final String content;
        final LocalDateTime createdAt;
        final long sizeBytes;

        ContentEntry(String content) {
            this.content = content;
            this.createdAt = LocalDateTime.now();
            this.sizeBytes = content.getBytes().length;
        }

        boolean isExpired(long maxAgeMs) {
            return createdAt.isBefore(LocalDateTime.now().minusNanos(maxAgeMs * 1_000_000));
        }
    }

    public void storeContent(String contentKey, String fileContent) {
        if (FILE_CONTENT_STORE.size() >= maxStoreSize) {
            cleanupExpiredEntries();
            if (FILE_CONTENT_STORE.size() >= maxStoreSize) {
                log.warn("Content store is full. Removing oldest entry to make space.");
                removeOldestEntry();
            }
        }

        ContentEntry entry = new ContentEntry(fileContent);
        ContentEntry previous = FILE_CONTENT_STORE.put(contentKey, entry);
        
        if (previous != null) {
            totalMemoryUsage.addAndGet(-previous.sizeBytes);
        }
        totalMemoryUsage.addAndGet(entry.sizeBytes);
        
        log.info("Stored file content with key: {} (size: {} bytes, total memory: {} bytes)", 
                contentKey, entry.sizeBytes, totalMemoryUsage.get());
    }

    public String getContent(String contentKey) {
        ContentEntry entry = FILE_CONTENT_STORE.get(contentKey);
        if (entry == null) {
            log.error("File content not found in store for key: {}", contentKey);
            return null;
        }
        
        if (entry.isExpired(maxAgeMs)) {
            log.warn("Content expired for key: {}, removing", contentKey);
            removeContent(contentKey);
            return null;
        }
        
        log.debug("Retrieved file content for key: {} (size: {} bytes)", contentKey, entry.sizeBytes);
        return entry.content;
    }

    public boolean removeContent(String contentKey) {
        ContentEntry removed = FILE_CONTENT_STORE.remove(contentKey);
        if (removed != null) {
            totalMemoryUsage.addAndGet(-removed.sizeBytes);
            log.info("Cleaned up file content for key: {} (size: {} bytes)", contentKey, removed.sizeBytes);
            return true;
        }
        return false;
    }

    public String generateContentKey(String filename) {
        return String.format("%s_%d_%s", 
                System.currentTimeMillis(), 
                Thread.currentThread().getId(),
                filename != null ? filename.replaceAll("[^a-zA-Z0-9.]", "_") : "unknown");
    }

    public Map<String, Object> getStoreStats() {
        cleanupExpiredEntries();
        
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("totalEntries", FILE_CONTENT_STORE.size());
        stats.put("maxSize", maxStoreSize);
        stats.put("totalMemoryBytes", totalMemoryUsage.get());
        stats.put("totalMemoryMB", totalMemoryUsage.get() / (1024.0 * 1024.0));
        stats.put("availableSlots", maxStoreSize - FILE_CONTENT_STORE.size());
        return stats;
    }

    public int clearAllContent() {
        int size = FILE_CONTENT_STORE.size();
        FILE_CONTENT_STORE.clear();
        totalMemoryUsage.set(0);
        log.info("Cleared all content from store. Freed {} entries", size);
        return size;
    }

    @Scheduled(fixedRateString = "${conversion.content-store.cleanup-interval:300000}")
    public void cleanupExpiredEntries() {
        long startTime = System.currentTimeMillis();
        int removedCount = 0;
        
        FILE_CONTENT_STORE.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired(maxAgeMs)) {
                totalMemoryUsage.addAndGet(-entry.getValue().sizeBytes);
                return true;
            }
            return false;
        });
        
        if (removedCount > 0) {
            long duration = System.currentTimeMillis() - startTime;
            log.info("Cleanup completed: removed {} expired entries in {}ms, memory freed: {} bytes", 
                    removedCount, duration, totalMemoryUsage.get());
        }
    }

    private void removeOldestEntry() {
        if (FILE_CONTENT_STORE.isEmpty()) return;
        
        String oldestKey = FILE_CONTENT_STORE.entrySet().stream()
                .min(Map.Entry.comparingByValue((e1, e2) -> e1.createdAt.compareTo(e2.createdAt)))
                .map(Map.Entry::getKey)
                .orElse(null);
                
        if (oldestKey != null) {
            removeContent(oldestKey);
            log.warn("Removed oldest entry: {} to make space", oldestKey);
        }
    }
}