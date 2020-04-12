package io.roach.demo.txn.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.roach.demo.txn.annotation.TransactionService;
import io.roach.demo.txn.domain.AccountEntity;
import io.roach.demo.txn.domain.AccountType;

@Repository
@TransactionService
public interface AccountRepository extends JpaRepository<AccountEntity, Long>,
        JpaSpecificationExecutor<AccountEntity> {

    @Query(value = "select sum(a.balance) from AccountEntity a where a.name=?1")
//    @Lock(LockModeType.PESSIMISTIC_READ)
    BigDecimal getBalance(String name);

    @Modifying
    @Query("update AccountEntity a set a.balance = a.balance + ?3 where a.name = ?1 and a.type=?2")
    void updateBalance(String name, AccountType type, BigDecimal balance);

    @Modifying
    @Query("update AccountEntity a set a.balance = ?1")
    void resetBalances(BigDecimal balance);
}
