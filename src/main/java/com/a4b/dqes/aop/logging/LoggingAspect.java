/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.aop.logging;

import java.util.Arrays;
import java.util.Map;

import com.a4b.xpservicelog.provided.SerLogTraceInfoProvider;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import tech.jhipster.config.JHipsterConstants;

/**
 * Aspect for logging execution of service and repository Spring components.
 * <p>
 * By default, it only runs with the "dev" profile.
 */
@Aspect
public class LoggingAspect {

    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String EXECUTE_TIME = "EXECUTE_TIME";
    private static final String LOG_MSG = "RemoteAddr: {} Username: {} ExecuteTime: {} ms Enter: {}.{}() with argument[s] = {} Result: {} Baggage: {} Category: {}";

    private static final String BAGGAGE = "BAGGAGE";
    private static final String CATEGORY_DEFAULT = "DEFAULT";

    private final Environment env;
    private final SerLogTraceInfoProvider traceInfoProvider;

    public LoggingAspect(Environment env, SerLogTraceInfoProvider traceInfoProvider) {
        this.env = env;
        this.traceInfoProvider = traceInfoProvider;
    }

    /**
     * Pointcut that matches all repositories, services and Web REST endpoints.
     */
    @Pointcut("( within(@org.springframework.stereotype.Service *)" +
        " || within(@org.springframework.web.bind.annotation.RestController *) )"
        + " && !@annotation(com.a4b.core.server.annotation.LogIgnore)")
    public void springBeanPointcut() {
        // Method is empty as this is just a Pointcut, the implementations are in the advices.
    }

    /**
     * Pointcut that matches all Spring beans in the application's main packages.
     */
    @Pointcut("within(com.a4b.dqes.repository..*)" +
        " || within(com.a4b.dqes.service..*)" +
        " || within(com.a4b.dqes.web.rest..*)")
    public void applicationPackagePointcut() {
        // Method is empty as this is just a Pointcut, the implementations are in the advices.
    }

    /**
     * Retrieves the {@link Logger} associated to the given {@link JoinPoint}.
     *
     * @param joinPoint join point we want the logger for.
     * @return {@link Logger} associated to the given {@link JoinPoint}.
     */
    private Logger logger(JoinPoint joinPoint) {
        return LoggerFactory.getLogger(joinPoint.getSignature().getDeclaringTypeName());
    }


    /**
     * Advice that logs methods throwing exceptions.
     *
     * @param joinPoint join point for advice
     * @param e         exception
     */
    @AfterThrowing(pointcut = "applicationPackagePointcut() && springBeanPointcut()", throwing = "e")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable e) {


        if (env.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT))) {
            logger(joinPoint)
                .error(
                    "Exception in {}() with cause = \'{}\' and exception = \'{}\'",
                    joinPoint.getSignature().getName(),
                    e.getCause() != null ? e.getCause() : "NULL",
                    e.getMessage(),
                    e
                );
        } else {
            logger(joinPoint)
                .error(
                    "Exception in {}() with cause = {}",
                    joinPoint.getSignature().getName(),
                    e.getCause() != null ? e.getCause() : "NULL"
                );
        }

        //username
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = "anonymous";
        if (authentication != null) {
            username = authentication.getName();
        }

        //remoteAddr
        String remoteAddr = String.valueOf("N/A");
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)) {
                remoteAddr = request.getRemoteAddr();
            }
        }

        Map<String, String> baggageMap = traceInfoProvider.getTraceInfo();
        MDC.put(BAGGAGE, !baggageMap.isEmpty() ? baggageMap.toString() : "N/A");

        logger(joinPoint).info(LOG_MSG, remoteAddr, username, MDC.get(EXECUTE_TIME), joinPoint.getSignature().getDeclaringTypeName(),
            joinPoint.getSignature().getName(), Arrays.toString(joinPoint.getArgs()), e.getMessage(), MDC.get(BAGGAGE), CATEGORY_DEFAULT);
        MDC.clear();

    }

    /**
     * Advice that logs when a method is entered and exited.
     *
     * @param joinPoint join point for advice.
     * @return result.
     * @throws Throwable throws {@link IllegalArgumentException}.
     */
    @Around("applicationPackagePointcut() && springBeanPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        Logger log = logger(joinPoint);
        if (log.isDebugEnabled()) {
            log.debug("Enter: {}() with argument[s] = {}", joinPoint.getSignature().getName(), Arrays.toString(joinPoint.getArgs()));
        }


        long startTime = System.currentTimeMillis();
        //username
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = "anonymous";
        if (authentication != null) {
            username = authentication.getName();
        }

//    	MDC.put(USER_NAME, username);

        //remoteAddr
        String remoteAddr = String.valueOf("N/A");
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)) {
                remoteAddr = request.getRemoteAddr();
            }
        }
//    	MDC.put(REMOTE_ADDR, remoteAddr);
//    	result
        boolean execResult = Boolean.FALSE;

        try {
            Object result = joinPoint.proceed();
            if (log.isDebugEnabled()) {
                log.debug("Exit: {}() with result = {}", joinPoint.getSignature().getName(), result);
            }
//            MDC.put(RESULT, RESULT_SUCCESS);
            execResult = Boolean.TRUE;
            return result;
        } catch (IllegalArgumentException e) {
//        	execResult = Boolean.FALSE;
            log.error("Illegal argument: {} in {}()", Arrays.toString(joinPoint.getArgs()), joinPoint.getSignature().getName());
            throw e;
        } finally {
            long executeTime = System.currentTimeMillis() - startTime;
            MDC.put(EXECUTE_TIME, String.valueOf(executeTime));

            Map<String, String> baggageMap = traceInfoProvider.getTraceInfo();
            MDC.put(BAGGAGE, !baggageMap.isEmpty() ? baggageMap.toString() : "N/A");

            if (execResult) {
                log.info(LOG_MSG, remoteAddr, username, executeTime, joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(), Arrays.toString(joinPoint.getArgs()), RESULT_SUCCESS, MDC.get(BAGGAGE), CATEGORY_DEFAULT);
                MDC.clear();
            }
        }

    }
}
