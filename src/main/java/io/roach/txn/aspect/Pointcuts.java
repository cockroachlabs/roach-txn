package io.roach.txn.aspect;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import io.roach.txn.annotation.TransactionBoundary;
import io.roach.txn.annotation.TransactionHints;

/**
 * Shared AOP pointcut expression used across services and components.
 */
@Aspect
public class Pointcuts {
    @Pointcut("execution(* io.roach..*(..)) "
            + "&& @annotation(transactionBoundary)")
    public void anyTransactionBoundaryOperation(TransactionBoundary transactionBoundary) {
    }

    @Pointcut("execution(* io.roach..*(..)) "
            + "&& @annotation(transactionHints)")
    public void anyTransactionHintedOperation(TransactionHints transactionHints) {
    }
}
