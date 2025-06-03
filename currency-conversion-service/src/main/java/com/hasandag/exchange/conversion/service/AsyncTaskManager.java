package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.common.exception.BatchJobException;
import com.hasandag.exchange.conversion.model.BatchJobResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncTaskManager {
    
    private final ConcurrentHashMap<String, CompletableFuture<BatchJobResponse>> tasks = new ConcurrentHashMap<>();
    private final AtomicInteger taskCounter = new AtomicInteger(0);
    
    public String generateTaskId() {
        return "task-" + System.currentTimeMillis() + "-" + taskCounter.incrementAndGet();
    }
    
    public void registerTask(String taskId, CompletableFuture<BatchJobResponse> asyncTask) {
        tasks.put(taskId, asyncTask);
        log.debug("Registered async task with ID: {}", taskId);
    }
    
    public BatchJobResponse getTaskStatus(String taskId) {
        CompletableFuture<BatchJobResponse> task = tasks.get(taskId);
        
        if (task == null) {
            return BatchJobResponse.failed(null, "Task not found: " + taskId);
        }
        
        if (task.isDone()) {
            try {
                BatchJobResponse response = task.get();
                if (response != null) {
                    return response;
                }
            } catch (Exception e) {
                log.error("Error getting task result for {}: {}", taskId, e.getMessage());
                return BatchJobResponse.failed(null, "Task failed: " + e.getMessage());
            }
        }
        
        return BatchJobResponse.async(taskId, "RUNNING", "Task is still running", "", 0L, "");
    }
    
    public int getActiveTaskCount() {
        return (int) tasks.entrySet().stream()
                .filter(entry -> !entry.getValue().isDone())
                .count();
    }
    
    public int cleanupCompletedTasks() {
        int cleanedCount = 0;
        var iterator = tasks.entrySet().iterator();
        
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isDone()) {
                iterator.remove();
                cleanedCount++;
            }
        }
        
        log.info("Cleaned up {} completed tasks", cleanedCount);
        return cleanedCount;
    }
} 