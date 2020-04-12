package io.roach.demo.txn;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import io.roach.demo.txn.domain.AccountType;

public class BankStressTest {
    private BankClient bankClient = new BankClient("http://localhost:8080");

    @Test
    public void transferMoneyConcurrent() {
        final LinkedList<Future<HttpStatus>> futureList = new LinkedList<>();

        bankClient.reset();

        // Use concurrent threads for "serialized" execution, subject to retry errors
        ScheduledExecutorService pool = Executors
                .newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);

        for (int i = 0; i < 200; i++) {
            String name = (i % 2 == 0) ? "alice" : "bob";
            AccountType type = (i % 2 == 0) ? AccountType.expense : AccountType.asset;
            BigDecimal amount = BigDecimal.valueOf(Math.random() * 50).setScale(2, RoundingMode.HALF_EVEN);
            // Withdrawals only
            futureList.offer(pool.submit(() -> bankClient.transfer(name, type, amount.negate()).getStatusCode()));
        }

        while (!futureList.isEmpty()) {
            try {
                HttpStatus status = futureList.poll().get();
                Assert.assertTrue("Unexpected: " + status.value(), status.is2xxSuccessful());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof HttpClientErrorException) {
                    HttpClientErrorException ex = (HttpClientErrorException) e.getCause();
                    Assert.assertEquals(HttpStatus.EXPECTATION_FAILED, ex.getStatusCode()); // Negative balance
                } else {
                    e.printStackTrace();
                    Assert.fail("Not good");
                }
            }
        }

        pool.shutdown();

        BigDecimal aliceBalance = new BigDecimal(bankClient.balanceTotal("alice").getBody());
        BigDecimal bobBalance = new BigDecimal(bankClient.balanceTotal("bob").getBody());

        // Expected to fail in PSQL unless using SSI
        Assert.assertFalse("Negative balance for Alice: " + aliceBalance, aliceBalance.compareTo(BigDecimal.ZERO) < 0);
        Assert.assertFalse("Negative balance for Bob: " + bobBalance, aliceBalance.compareTo(BigDecimal.ZERO) < 0);
    }
}
