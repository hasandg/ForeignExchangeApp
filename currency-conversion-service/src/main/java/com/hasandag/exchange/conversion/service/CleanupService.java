package com.hasandag.exchange.conversion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Specialized service for cleanup operations following Single Responsibility Principle.
 * Handles all cleanup-related tasks for batch jobs, files, and async tasks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupService {
    
    private final FileContentStoreService fileContentStoreService;
    private final AsyncTaskManager asyncTaskManager;
    
    /**
     * Cleanup specific job content by content key
     */
    public void cleanupJobContent(String contentKey) {
        log.info("Starting cleanup for content key: {}", contentKey);
        boolean removed = fileContentStoreService.removeContent(contentKey);
        
        if (removed) {
            log.info("Successfully cleaned up content for key: {}", contentKey);
        } else {
            log.warn("Content not found for cleanup key: {}", contentKey);
        }
    }
    
    /**
     * Cleanup all content from the store
     */
    public int cleanupAllContent() {
        log.info("Starting cleanup of all content store");
        int cleanedCount = fileContentStoreService.clearAllContent();
        log.info("Cleaned up {} content entries from store", cleanedCount);
        return cleanedCount;
    }
    
    /**
     * Cleanup completed async tasks
     */
    public int cleanupCompletedAsyncTasks() {
        log.info("Starting cleanup of completed async tasks");
        int cleanedCount = asyncTaskManager.cleanupCompletedTasks();
        log.info("Cleaned up {} completed async tasks", cleanedCount);
        return cleanedCount;
    }
    
    /**
     * Get content store statistics for monitoring
     */
    public Map<String, Object> getContentStoreStats() {
        return fileContentStoreService.getStoreStats();
    }
    
    /**
     * Get active async task count for monitoring
     */
    public int getActiveAsyncTaskCount() {
        return asyncTaskManager.getActiveTaskCount();
    }
    
    /**
     * Perform comprehensive cleanup - both content and async tasks
     */
    public Map<String, Object> performFullCleanup() {
        log.info("Starting full cleanup operation");
        
        int contentCleaned = cleanupAllContent();
        int tasksCleaned = cleanupCompletedAsyncTasks();
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("contentFilesCleared", contentCleaned);
        result.put("asyncTasksCleared", tasksCleaned);
        result.put("timestamp", java.time.Instant.now());
        
        log.info("Full cleanup completed - Content files: {}, Async tasks: {}", 
                contentCleaned, tasksCleaned);
        
        return result;
    }
} 