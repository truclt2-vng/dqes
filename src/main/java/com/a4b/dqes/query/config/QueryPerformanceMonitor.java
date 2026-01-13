package com.a4b.dqes.query.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Performance monitoring aspect for query execution
 * Logs slow queries and provides execution metrics
 */
@Aspect
@Component
@Slf4j
@ConditionalOnProperty(name = "app.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class QueryPerformanceMonitor {
    
    private static final long SLOW_QUERY_THRESHOLD_MS = 1000; // 1 second
    
    /**
     * Monitor query execution time
     */
    @Around("execution(* com.a4b.dqes.query.service.DynamicQueryExecutionService.executeQuery(..))")
    public Object monitorQueryExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.nanoTime();
        String methodName = joinPoint.getSignature().toShortString();
        
        try {
            Object result = joinPoint.proceed();
            long executionTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms
            
            if (executionTime > SLOW_QUERY_THRESHOLD_MS) {
                log.warn("SLOW QUERY DETECTED: {} took {}ms (threshold: {}ms)", 
                    methodName, executionTime, SLOW_QUERY_THRESHOLD_MS);
            } else if (log.isDebugEnabled()) {
                log.debug("Query executed: {} in {}ms", methodName, executionTime);
            }
            
            return result;
        } catch (Exception e) {
            long executionTime = (System.nanoTime() - startTime) / 1_000_000;
            log.error("Query failed: {} after {}ms", methodName, executionTime, e);
            throw e;
        }
    }
    
    /**
     * Monitor field resolution performance
     */
    @Around("execution(* com.a4b.dqes.query.service.FieldResolverService.batchResolveFields(..))")
    public Object monitorFieldResolution(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.nanoTime();
        
        try {
            Object result = joinPoint.proceed();
            long executionTime = (System.nanoTime() - startTime) / 1_000_000;
            
            if (log.isDebugEnabled()) {
                log.debug("Field resolution completed in {}ms", executionTime);
            }
            
            return result;
        } catch (Exception e) {
            long executionTime = (System.nanoTime() - startTime) / 1_000_000;
            log.error("Field resolution failed after {}ms", executionTime, e);
            throw e;
        }
    }
    
    /**
     * Monitor graph traversal performance
     */
    @Around("execution(* com.a4b.dqes.query.service.RelationGraphService.findPath(..))")
    public Object monitorGraphTraversal(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.nanoTime();
        Object[] args = joinPoint.getArgs();
        
        try {
            Object result = joinPoint.proceed();
            long executionTime = (System.nanoTime() - startTime) / 1_000_000;
            
            if (log.isDebugEnabled() && args.length >= 5) {
                log.debug("Graph traversal from {} to {} completed in {}ms", 
                    args[3], args[4], executionTime);
            }
            
            return result;
        } catch (Exception e) {
            long executionTime = (System.nanoTime() - startTime) / 1_000_000;
            log.error("Graph traversal failed after {}ms", executionTime, e);
            throw e;
        }
    }
}
