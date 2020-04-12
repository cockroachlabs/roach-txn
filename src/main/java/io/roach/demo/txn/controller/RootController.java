package io.roach.demo.txn.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
public class RootController {
    @GetMapping
    public ResponseEntity<IndexModel> index() {
        IndexModel index = new IndexModel("Welcome to Roach Demo :: TXN");

        index.add(linkTo(methodOn(AccountController.class)
                .listAccounts(PageRequest.of(0, 5)))
                .withRel("accounts"));

        index.add(linkTo(methodOn(AccountController.class)
                .getBalance("alice"))
                .withRel("balance-total"));

        index.add(linkTo(methodOn(AccountController.class)
                .getBalance("bob"))
                .withRel("balance-total"));

        index.add(linkTo(methodOn(AccountController.class)
                .transfer(null))
                .withRel("transfer"));

        index.add(linkTo(methodOn(AccountController.class)
                .reset())
                .withRel("reset"));

        index.add(linkTo(methodOn(AdminController.class)
                .index())
                .withRel("admin"));

        return new ResponseEntity<>(index, HttpStatus.OK);
    }
}
