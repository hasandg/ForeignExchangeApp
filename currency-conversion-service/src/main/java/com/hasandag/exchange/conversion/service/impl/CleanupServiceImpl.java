package com.hasandag.exchange.conversion.service.impl;

import com.hasandag.exchange.conversion.service.CleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class CleanupServiceImpl implements CleanupService {

    private final Map<String, LocalDateTime> contentStore = new ConcurrentHashMap<>();
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    @Value("${conversion.content-store.max-age:3600000}")
    private long maxAgeMs;

    @Override
    public void cleanupJobContent(String contentKey) {
        if (contentKey != null && contentStore.containsKey(contentKey)) {
            contentStore.remove(contentKey);
            log.info("Cleaned up content for key: {}", contentKey);
        } else {
            log.warn("Content key not found for cleanup: {}", contentKey);
        }
    }

    @Override
    public int cleanupAllContent() {
        int sizeBefore = contentStore.size();
        contentStore.clear();
        log.info("Cleaned up all content. Removed {} entries", sizeBefore);
        return sizeBefore;
    }

    @Override
    public int cleanupCompletedAsyncTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusNanos(maxAgeMs * 1_000_000);
        AtomicInteger removedCount = new AtomicInteger(0);
        
        contentStore.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(cutoff)) {
                removedCount.incrementAndGet();
                return true;
            }
            return false;
        });
        
        int finalCount = removedCount.get();
        log.info("Cleaned up {} completed async tasks older than {} ms", finalCount, maxAgeMs);
        return finalCount;
    }

    @Override
    public Map<String, Object> getContentStoreStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntries", contentStore.size());
        stats.put("activeAsyncTasks", activeTaskCount.get());
        stats.put("maxAgeMs", maxAgeMs);
        stats.put("currentTime", LocalDateTime.now());
        
        long expiredCount = contentStore.values().stream()
                .mapToLong(timestamp -> {
                    LocalDateTime cutoff = LocalDateTime.now().minusNanos(maxAgeMs * 1_000_000);
                    return timestamp.isBefore(cutoff) ? 1 : 0;
                })
                .sum();
        
        stats.put("expiredEntries", expiredCount);
        return stats;
    }

    @Override
    public int getActiveAsyncTaskCount() {
        return activeTaskCount.get();
    }

    @Override
    public Map<String, Object> performFullCleanup() {
        Map<String, Object> result = new HashMap<>();
        
        int contentCleaned = cleanupAllContent();
        int tasksCleaned = cleanupCompletedAsyncTasks();
        activeTaskCount.set(0);
        
        result.put("contentEntriesRemoved", contentCleaned);
        result.put("expiredTasksRemoved", tasksCleaned);
        result.put("activeTasksReset", true);
        result.put("cleanupTime", LocalDateTime.now());
        result.put("status", "completed");
        
        log.info("Full cleanup completed. Content: {}, Tasks: {}", contentCleaned, tasksCleaned);
        return result;
    }

    public void addContent(String contentKey) {
        contentStore.put(contentKey, LocalDateTime.now());
        log.debug("Added content key: {}", contentKey);
    }

    public void incrementActiveTaskCount() {
        activeTaskCount.incrementAndGet();
    }

    public void decrementActiveTaskCount() {
        activeTaskCount.decrementAndGet();
    }
}