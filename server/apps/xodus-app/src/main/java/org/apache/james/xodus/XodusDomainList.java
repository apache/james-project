package org.apache.james.xodus;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainList;

import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityIterable;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.StoreTransaction;

public class XodusDomainList extends AbstractDomainList {
    static final String ENTITY_TYPE = "JamesDomain";
    private static final String PROP_DOMAIN = "domain";

    private final PersistentEntityStore entityStore;

    @Inject
    public XodusDomainList(DNSService dnsService, PersistentEntityStore entityStore) {
        super(dnsService);
        this.entityStore = entityStore;
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        try {
            entityStore.executeInTransaction(txn -> {
                Entity existing = findEntity(txn, domain);
                if (existing != null) {
                    throw new RuntimeException(new DomainListException(domain.name() + " already exists."));
                }
                Entity entity = txn.newEntity(ENTITY_TYPE);
                entity.setProperty(PROP_DOMAIN, domain.asString());
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof DomainListException) {
                throw (DomainListException) e.getCause();
            }
            throw e;
        }
    }

    @Override
    protected List<Domain> getDomainListInternal() throws DomainListException {
        return entityStore.computeInReadonlyTransaction(txn -> {
            List<Domain> domains = new ArrayList<>();
            for (Entity entity : txn.getAll(ENTITY_TYPE)) {
                String d = (String) entity.getProperty(PROP_DOMAIN);
                if (d != null) {
                    domains.add(Domain.of(d));
                }
            }
            return domains;
        });
    }

    @Override
    protected boolean containsDomainInternal(Domain domain) throws DomainListException {
        return entityStore.computeInReadonlyTransaction(txn -> !txn.find(ENTITY_TYPE, PROP_DOMAIN, domain.asString()).isEmpty());
    }

    @Override
    protected void doRemoveDomain(Domain domain) throws DomainListException {
        boolean removed = entityStore.computeInTransaction(txn -> {
            Entity entity = findEntity(txn, domain);
            if (entity != null) {
                return entity.delete();
            }
            return false;
        });

        if (!removed) {
            throw new DomainListException(domain.name() + " was not found");
        }
    }

    private Entity findEntity(StoreTransaction txn, Domain domain) {
        EntityIterable iter = txn.find(ENTITY_TYPE, PROP_DOMAIN, domain.asString());
        return iter.getFirst();
    }
}
