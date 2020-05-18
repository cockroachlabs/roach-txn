# Roach Demo :: Spring Boot Transactions

The purpose of this CockroachDB Spring Boot demo is to showcase how a larger codebase could 
remain decluttered from retry logic and other boilerplate elements that has no actual 
business purpose.

It's a simple Spring Boot application showing:

- The ECB pattern for transactional robustness and clarity
- Annotation-driven transaction demarcation with retries and exponential backoff.
- Aspect to inject transaction hints/attributes
- Write skew anomaly in practice 

## Entity Control Boundary Pattern 

The classic stability pattern called [Entity-Control-Boundary (ECB)](https://en.wikipedia.org/wiki/Entity-control-boundary) 
can be applied to bring clarity and robustness to transaction management in a typical Spring Boot application.

It basically works by using meta-annotations to assign ECB architectural roles to different elements, and then use aspects
to apply transaction behavior for these roles.

In a nutshell:

- Use meta-annotations to drive transaction demarcation and propagation 
- Enable Spring's annotation driven transaction manager
- Use aspects to implement transparent transaction retries on transient errors 

### Elements

- **Boundary** - Typically a web controller, service facade or JMS/Kafka service activator method that exposes the 
functionality of a service and interacts with clients.  
- **Control** - Typically a fine-grained service behind a boundary web that implements business logic.
- **Entity** - Refers to a persistent domain object, typically mapped to a JPA entity.

### Annotations

These (meta-)annotations are applied to the elements.

- @TransactionBoundary - Annotation marking a transaction boundary. A boundary is allowed to start new transactions 
and suspend existing ones, hence it must use REQUIRES_NEW.   

        @Inherited
        @Documented
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.TYPE, ElementType.METHOD})
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public @interface TransactionBoundary {
            int retryAttempts() default 30;
        }

- @TransactionService - Annotation marking a control service method that is NOT allowed to start new transactions and 
must be invoked from a transactional context. Hence it must use MANDATORY.

        @Inherited
        @Documented
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.TYPE, ElementType.METHOD})
        @Transactional(propagation = Propagation.MANDATORY)
        public @interface TransactionService {
        }

- @javax.persistence.Entity - Standard JPA entity annotation marking a managed persistent entity.

### Aspects

So far we only applied semantic descriptors and transaction demarcation through annotations. Now we want to do something
more interesting.

[Aspects](https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html#aop) are very useful to weave 
in specific _advices_ either around, before or after (+throwing) method calls. 

An appropriate _around_ advice is to wrap a transactional method and do a retry loop on transient errors with 
exponential backoff. The exponential backoff is optional but helps to reduce congestion.

- **RetryableTransactionalAspect** - Catch transient errors and retry with exp backoff
- **SavepointTransactionalAspect** - Catch transient errors and retry by rolling back to savepoint with exp backoff
- **TransactionHintsAspect** - Unrelated to retrys, used to apply transaction attributes

Notice that savepoints are not supported by Hibernate, so it would require using JDBC instead.

The order in which these advices are weaved in between the source and target have significance. The ordering
must be relative to Spring's transactional advice activated with `@EnableTransactionManagement`.

The typical call chain need to be something like this:

    source (controller/service facade/service activator)
        |--> retryableOperationAdvice (no transaction context allowed)  
        |--> transactionAdvice (Spring advice that starts a transaction)  
        |--> transactionHintsAdvice (now there's a transaction context)  
    target (business service/repository expecting a TXN)
    
### Usage

Following ECB elements in this demo, controllers are boundaries and repositories 
are 'control services' and entities are simply JPA entities.

Declare a controller method to be a transactional boundary with retries:

    @PostMapping(value = "/transfer")
    @TransactionBoundary  // translates to @Transactional(REQUIRES_NEW)
    public HttpEntity<Void> transfer(@RequestBody TransferRequest request) {
        ...
        accountRepository.updateBalance(..);
        ...
        return new ResponseEntity<>(HttpStatus.OK);
    }

And the corresponding repository: 

    @Repository
    @TransactionService // translates to @Transactional(MANDATORY)
    public interface AccountRepository extends JpaRepository<AccountEntity, Long>,
            JpaSpecificationExecutor<AccountEntity> { ... }

If the account repository is acidentally called from a non-transactional method, it will fail
with a runtime error.

# Retry Error Example

To showcase retry errors we are going to provoke a certain anomaly called 
[write skew](https://www.cockroachlabs.com/blog/what-write-skew-looks-like/). 

Another candidate for retries is when the database protects you from lost updates 
(reading and writing to the same keys concurrently).

Write skew can occur if two transactions read the same data which overlaps what the other is writing. 
Put another way, a transaction reads data and decides to write based on the observed value. By the time 
the write happens however, the premise of the decision has changed due to some other transaction writing
to the data read before.
 
It's a subtle race condition that is hard to detect. If interleaved read and write operations that are 
subject to write skew would run serially (one after the other) then it will not appear. This anomaly is
visible only when running transactions concurrently.
                                                        
CockroachDB always runs in SERIALIZABLE, which protects against write skew. To actually witness write skew 
in this demo, you need to run it with PostgreSQL in the default READ_COMMITTED (or REPEATABLE_READ) isolation level. 
                                                        
This demo uses a simple bank example where the invariant (business rule) is to protecting account balances 
from going negative.

    create table account
    (
        id      bigint         not null primary key default unique_rowid(),
        balance numeric(19, 2) not null,
        name    varchar(128)   not null,
        type    varchar(25)    not null
    );
    create unique index idx_account ON account (name,type);
    
    insert into account (id, balance, name, type) values (1, 500.00, 'alice', 'asset');
    insert into account (id, balance, name, type) values (2, 500.00, 'alice', 'expense');

Alice in this case has two accounts with a total withdrawal limit of 1000. Both accounts can go negative, 
as long as the total sum remains positive. 

Using REPEATABLE_READ or lower isolation level in PostgreSQL, the following sequence could unfold:

    -- SESSION 1                                        SESSION 2
    begin;
    select balance from account where name='alice';
                                                        begin; 
                                                        select balance from account where name='alice';
    update account set 
        balance = balance - 700 
        where name = 'alice' and type='asset';
    -- So far so good        
                                                        update account set 
                                                            balance = balance - 700 
                                                            where name = 'alice' and type='expense';
                                                        -- From an application standpoint, this also looks OK 
                                                        -- but if accepted Alice will end up with a total of -400.    
    commit;
    -- First committer wins
                                                        commit;
                                                        -- Succeess!? Both transactions are reading data which overlaps
                                                        -- what the other is writing, which is know as write skew.
                                                        -- The invariant has been violated. 

Same sequence, but this time using SERIALIZABLE SNAPSHOT (SSI) isolation:

    -- SESSION 1                                        SESSION 2
    begin;
    set transaction isolation level serializable;
    select balance from account where name='alice';
                                                        begin;
                                                        set transaction isolation level serializable; 
                                                        select balance from account where name='alice';
    update account set 
        balance = balance - 700 
        where name = 'alice' and type='asset';
    -- So far so good        
                                                        update account set 
                                                            balance = balance - 700 
                                                            where name = 'alice' and type='expense';
                                                        -- Also succeeds because of the first committer wins policy.
                                                        -- In PostgreSQL, this will not block.    
    commit;
    -- First committer wins
                                                        commit;
                                                        -- Transaction fails with the following:
                                                        -- ERROR:  could not serialize access due to read/write dependencies among transactions
                                                        -- DETAIL:  Reason code: Canceled on identification as a pivot, during commit attempt.
                                                        -- HINT:  The transaction might succeed if retried.
    
Same example in CockroachDB (always using SERIALIZABLE):

    -- SESSION 1                                        SESSION 2
    begin;
    set transaction isolation level serializable;
    select balance from account where name='alice';
                                                        begin;
                                                        set transaction isolation level serializable; 
                                                        select balance from account where name='alice';
    update account set 
        balance = balance - 700 
        where name = 'alice' and type='asset';
    -- So far so good        
                                                        update account set 
                                                            balance = balance - 700 
                                                            where name = 'alice' and type='expense';
                                                        -- In CockroachDB, this will block for SESSION 1.    
    commit;
    -- First committer wins
                                                        -- Transaction fails with the following:
                                                        pq: restart transaction: TransactionRetryWithProtoRefreshError: ReadWithinUncertaintyIntervalError: read at time 1586621770.672057705 ..
    
# Project Setup

## Prerequisites

- JDK8+ with 1.8 language level (OpenJDK compatible)
- Maven 3+ (optional)

## Supported Databases
            
- [CockroachDB](https://www.cockroachlabs.com) via PostgreSQL driver
- PostgreSQL 9.1+ 
 
The database is selected at startup by activating the appropriate Spring profile. 
Schema creation and initial data population is automatic via Liquibase. 
  
## Database Setup

Create the database to run test against:

    CREATE database roach_txn;
    
The schema and data is created via Liquibase when starting the server.    

## Building and running from codebase

The application is built with [Maven 3.1+](https://maven.apache.org/download.cgi).
Tanuki's Maven wrapper is included (mvnw). All 3rd party dependencies are available in public Maven repos.

To build and deploy to your local Maven repo, execute:

    ./mvnw clean install

## Start server

Either start with:

    java -jar target/roach-txn.jar 

or open your favourite IDE IntelliJ and run `io.roach.txn.Application`.

You can select between the databases by using Spring profiles (crdb or psql):

CockroachDB and a custom URL:

    java -jar target/roach-txn.jar --spring.profiles.active=crdb --spring.datasource.url=jdbc:postgresql://192.168.1.1:26257/roach_txn?sslmode=disable    

PostgreSQL and a custom URL:

    java -jar target/roach-txn.jar --spring.profiles.active=psql --spring.datasource.url=jdbc:postgresql://192.168.1.1:5432/roach_txn --spring.datasource.username=bobby --spring.datasource.password=tables

To drop and recreate the schema, add:

    --spring.liquibase.drop-first=true
    
PostgreSQL use RC isolation by default, so the test will fail. To make it not fail, enable SSI with:
    
    --spring.datasource.hikari.transaction-isolation=TRANSACTION_SERIALIZABLE    

## Integration Tests

Stress test the bank by sending concurrent HTTP requests to `localhost:8080`:

    ./mvnw -DskipTests=false -Dtest=io.roach.txn.BankStressTest test
