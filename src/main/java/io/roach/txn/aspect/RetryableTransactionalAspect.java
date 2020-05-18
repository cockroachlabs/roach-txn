package io.roach.txn.aspect;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.atomic.AtomicLong;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import io.roach.txn.annotation.TransactionBoundary;

/**
 * AOP around advice that intercepts and retries transient concurrency exceptions such
 * as deadlock looser, pessmistic and optimistic locking failures. Methods matching
 * the pointcut expression (annotated with @TransactionBoundary) are retried a number
 * of times with exponential backoff.
 * <p>
 * NOTE: This advice needs to runs in a non-transactional context, that is before the
 * underlying transaction advisor.
 */
@Aspect
@Order(AdvisorOrder.LEVEL_1) // This advisor must be before the TX advisor in the call chain
public class RetryableTransactionalAspect {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Around(value = "io.roach.txn.aspect.Pointcuts.anyTransactionBoundaryOperation(transactionBoundary)",
            argNames = "pjp,transactionBoundary")
    public Object retryableOperation(ProceedingJoinPoint pjp, TransactionBoundary transactionBoundary)
            throws Throwable {
        int numAttempts = 0;
        AtomicLong backoffMillis = new AtomicLong(150);

        Assert.isTrue(!TransactionSynchronizationManager.isActualTransactionActive(), "TX active");

        do {
            try {
                numAttempts++;
                return pjp.proceed();
            } catch (TransientDataAccessException | TransactionSystemException | JpaSystemException ex) {
                handleTransientException(ex, numAttempts, transactionBoundary.retryAttempts(), pjp, backoffMillis);
            } catch (UndeclaredThrowableException ex) {
                Throwable t = ex.getUndeclaredThrowable();
                if (t instanceof TransientDataAccessException) {
                    handleTransientException(t, numAttempts,
                            transactionBoundary.retryAttempts(), pjp, backoffMillis);
                } else {
                    throw ex;
                }
            }
        } while (numAttempts < transactionBoundary.retryAttempts());

        throw new ConcurrencyFailureException("Too many transient errors (" + numAttempts + ") for method ["
                + pjp.getSignature().toLongString() + "]. Giving up!");
    }

    private void handleTransientException(Throwable ex, int numAttempts, int totalAttempts,
                                          ProceedingJoinPoint pjp, AtomicLong backoffMillis) {
        if (logger.isWarnEnabled()) {
            logger.warn("Transient data access exception (" + numAttempts + " of max " + totalAttempts + ") "
                    + "detected (retry in " + backoffMillis + " ms) "
                    +"in method '" + pjp.getSignature().getDeclaringTypeName() + "." + pjp.getSignature().getName()
                    + "': " + ex.getMessage());
        }
        if (backoffMillis.get() >= 0) {
            try {
                Thread.sleep(backoffMillis.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
            backoffMillis.set(Math.min((long) (backoffMillis.get() * 1.5), 1000));
        }
    }
}
