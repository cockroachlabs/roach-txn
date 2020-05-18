package io.roach.txn.aspect;

import org.springframework.core.Ordered;

/**
 * Ordering constants for transaction advisors.
 */
public interface AdvisorOrder {
    int LEVEL_1 = Ordered.LOWEST_PRECEDENCE - 4;

    int LEVEL_2 = Ordered.LOWEST_PRECEDENCE - 3;

    int LEVEL_3 = Ordered.LOWEST_PRECEDENCE - 2;
}
