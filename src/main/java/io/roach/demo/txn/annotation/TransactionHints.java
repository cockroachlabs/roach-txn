package io.roach.demo.txn.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface TransactionHints {
    /**
     * @return Arbitrary key value transaction hints.
     */
    TransactionHint[] hints() default {};

    /**
     * @return transaction read-only hint optimization
     */
    boolean readOnly() default false;

    /**
     * @return session/transaction timeout in seconds
     */
    int timeout() default 300;

    /**
     * @return true to enable follower reads
     */
    boolean followerRead() default false;

    /**
     * See https://www.cockroachlabs.com/docs/stable/interval.html
     *
     * @return enables time travel read (ignored if followerRead is true)
     */
    String timeTravelReadInterval() default "(empty)";

    Priority priority() default Priority.NORMAL;

    enum Priority {
        LOW,
        NORMAL,
        HIGH
    }
}
