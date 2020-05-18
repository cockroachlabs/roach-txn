package io.roach.txn;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.roach.txn.aspect.AdvisorOrder;
import io.roach.txn.aspect.RetryableTransactionalAspect;
import io.roach.txn.aspect.SavepointTransactionalAspect;
import io.roach.txn.aspect.TransactionHintsAspect;

@Configuration
@EnableTransactionManagement(order = AdvisorOrder.LEVEL_2)
public class BankConfiguration implements WebMvcConfigurer {
    @Profile("!savepoints")
    @Bean
    public RetryableTransactionalAspect retryableTransactionalAspect() {
        return new RetryableTransactionalAspect();
    }

    // Savepoints only works with JDBC
    @Profile("savepoints")
    @Bean
    public SavepointTransactionalAspect savepointTransactionAspect() {
        return new SavepointTransactionalAspect("cockroach_restart", TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    // Transaction hints are CRDB specific
    @Bean
    @Profile("crdb")
    public TransactionHintsAspect transactionHintsAspect() {
        return new TransactionHintsAspect();
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(new FormHttpMessageConverter());
    }
}
