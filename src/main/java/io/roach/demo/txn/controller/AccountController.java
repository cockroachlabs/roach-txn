package io.roach.demo.txn.controller;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.roach.demo.txn.annotation.TransactionBoundary;
import io.roach.demo.txn.annotation.TransactionHints;
import io.roach.demo.txn.domain.AccountEntity;
import io.roach.demo.txn.domain.AccountType;
import io.roach.demo.txn.domain.NegativeBalanceException;
import io.roach.demo.txn.repository.AccountRepository;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping(path = "/account")
public class AccountController {
    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountResourceAssembler accountResourceAssembler;

    @Autowired
    private PagedResourcesAssembler<AccountEntity> pagedResourcesAssembler;

    @GetMapping
    @TransactionBoundary
    @TransactionHints(readOnly = true, followerRead = true)
    public HttpEntity<PagedModel<AccountModel>> listAccounts(
            @PageableDefault(size = 5, direction = Sort.Direction.ASC) Pageable page) {
        return ResponseEntity.ok(pagedResourcesAssembler
                .toModel(accountRepository.findAll(page), accountResourceAssembler));
    }

    @GetMapping(value = "/{id}")
    @TransactionBoundary
    @TransactionHints(readOnly = true)
    public HttpEntity<AccountModel> getAccount(@PathVariable("id") Long accountId) {
        return new ResponseEntity<>(accountResourceAssembler
                .toModel(accountRepository.getOne(accountId)), HttpStatus.OK);
    }

    @GetMapping(value = "/{name}/balance")
    @TransactionBoundary
    @TransactionHints(readOnly = true)
    public HttpEntity<String> getBalance(@PathVariable("name") String name) {
        return new ResponseEntity<>(accountRepository.getBalance(name).toPlainString(), HttpStatus.OK);
    }

    @GetMapping(value = "/transfer")
    public HttpEntity<TransferRequest> getTransferRequestForm() {
        TransferRequest form = TransferRequest.builder()
                .setName("alice")
                .setAccountType(AccountType.expense)
                .setAmount(new BigDecimal("100.00").negate())
                .build();
        form.add(linkTo(methodOn(AccountController.class)
                .transfer(form))
                .withRel("transfer"));
        return new ResponseEntity<>(form, HttpStatus.OK);
    }


    @PostMapping(value = "/transfer")
    @TransactionBoundary
    public HttpEntity<Void> transfer(@RequestBody TransferRequest request) {
        gcPause(25, 150);

        BigDecimal totalBalance = accountRepository.getBalance(request.getName());

        if (totalBalance.add(request.getAmount()).compareTo(BigDecimal.ZERO) < 0) {
            throw new NegativeBalanceException(
                    "Insufficient funds " + request.getAmount() + " for user " + request.getName());
        }

        gcPause(25, 150);
        accountRepository.updateBalance(request.getName(), request.getAccountType(), request.getAmount());

        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Simulate a GC pause to create unpredictable interleavings.
     */
    private void gcPause(long min, long max) {
        try {
            Thread.sleep(min + (int) (Math.random() * max));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @PostMapping(value = "/reset")
    @TransactionBoundary
    public HttpEntity<Void> reset() {
        accountRepository.resetBalances(new BigDecimal(500.00));
        return ResponseEntity.ok().build();
    }
}
