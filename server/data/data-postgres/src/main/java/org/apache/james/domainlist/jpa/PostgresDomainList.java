package org.apache.james.domainlist.jpa;

import static org.apache.james.domainlist.jpa.PostgresDomainModule.PostgresDomainTable.DOMAIN;
import static org.apache.james.domainlist.jpa.PostgresDomainModule.PostgresDomainTable.TABLE_NAME;

import java.util.List;

import javax.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainList;
import org.jooq.exception.DataAccessException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresDomainList extends AbstractDomainList {
    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresDomainList(DNSService dnsService, PostgresExecutor postgresExecutor) {
        super(dnsService);
        this.postgresExecutor = postgresExecutor;
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME, DOMAIN)
                .values(domain.asString())))
            .onErrorMap(DataAccessException.class, e -> new DomainListException(domain.name() + " already exists."))
            .block();

    }

    @Override
    protected List<Domain> getDomainListInternal() throws DomainListException {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)))
            .map(record -> Domain.of(record.get(DOMAIN)))
            .collectList()
            .block();
    }

    @Override
    protected boolean containsDomainInternal(Domain domain) throws DomainListException {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.selectFrom(TABLE_NAME)
            .where(DOMAIN.eq(domain.asString()))))
            .blockOptional()
            .isPresent();
    }

    @Override
    protected void doRemoveDomain(Domain domain) throws DomainListException {
        postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(DOMAIN.eq(domain.asString()))))
            .onErrorMap(DataAccessException.class, e -> new DomainListException(domain.name() + " was not found"))
            .block();
    }
}
