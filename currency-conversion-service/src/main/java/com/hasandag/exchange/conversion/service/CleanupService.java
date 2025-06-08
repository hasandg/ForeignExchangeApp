package com.hasandag.exchange.conversion.service;

import java.util.Map;

public interface CleanupService {
    
    void cleanupJobContent(String contentKey);
    
    int cleanupAllContent();
    
    int cleanupCompletedAsyncTasks();
    
    Map<String, Object> getContentStoreStats();
    
    int getActiveAsyncTaskCount();
    
    Map<String, Object> performFullCleanup();
} 