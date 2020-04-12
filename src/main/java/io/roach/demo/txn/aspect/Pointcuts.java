package io.roach.demo.txn.aspect;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import io.roach.demo.txn.annotation.TransactionBoundary;
import io.roach.demo.txn.annotation.TransactionHints;

/**
 * Shared AOP pointcut expression used across services and components.
 */
@Aspect
public class Pointcuts {
    @Pointcut("execution(* io.roach.demo..*(..)) "
            + "&& @annotation(transactionBoundary)")
    public void anyTransactionBoundaryOperation(TransactionBoundary transactionBoundary) {
    }

    @Pointcut("execution(* io.roach.demo..*(..)) "
            + "&& @annotation(transactionHints)")
    public void anyTransactionHintedOperation(TransactionHints transactionHints) {
    }
}
