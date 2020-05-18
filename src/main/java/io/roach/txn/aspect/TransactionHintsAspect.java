package io.roach.txn.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import io.roach.txn.annotation.TransactionHint;
import io.roach.txn.annotation.TransactionHints;

/**
 * https://www.cockroachlabs.com/docs/v19.2/set-vars.html
 */
@Aspect
// This advisor must be after retry and TX advisors in the call chain (in a transactional context)
@Order(AdvisorOrder.LEVEL_3)
public class TransactionHintsAspect {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${info.build.artifact}")
    private String applicationName;

    @Around(value = "io.roach.txn.aspect.Pointcuts.anyTransactionHintedOperation(transactionHints)",
            argNames = "pjp,transactionHints")
    public Object doInTransaction(ProceedingJoinPoint pjp, TransactionHints transactionHints)
            throws Throwable {
        Assert.isTrue(TransactionSynchronizationManager.isActualTransactionActive(), "TX not active");

        jdbcTemplate.execute("SET application_name = '" + applicationName + "'");
        jdbcTemplate.execute("SET TRANSACTION PRIORITY " + transactionHints.priority().name());

        if (transactionHints.followerRead()) {
            jdbcTemplate.execute("SET TRANSACTION AS OF SYSTEM TIME experimental_follower_read_timestamp()");
        } else {
            if (!"(empty)".equals(transactionHints.timeTravelReadInterval())) {
                jdbcTemplate.update("SET TRANSACTION AS OF SYSTEM TIME INTERVAL '" + transactionHints
                        .timeTravelReadInterval() + "'");
            }
        }

        if (transactionHints.timeout() > 0) {
            jdbcTemplate.update("SET statement_timeout=?", transactionHints.timeout() * 1000);
        }

        if (transactionHints.readOnly()) {
            jdbcTemplate.execute("SET transaction_read_only=true");
        }

        for (TransactionHint hint : transactionHints.hints()) {
            if (hint.intValue() >= 0) {
                jdbcTemplate.update("SET " + hint.name() + "=" + hint.intValue());
            } else {
                jdbcTemplate.update("SET " + hint.name() + "=?", hint.value());
            }
        }

        return pjp.proceed();
    }
}
