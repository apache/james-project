package org.apache.james.modules.server;

import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;

import com.google.inject.AbstractModule;

public class QuotaModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(QuotaManager.class).to(NoQuotaManager.class);
        bind(QuotaRootResolver.class).to(DefaultQuotaRootResolver.class);
    }
    
}
