package org.apache.james.wkd;

import org.apache.james.wkd.crypto.WebKeyDirectorySubmissionAddressKeyPairManager;
import org.apache.james.wkd.store.MemoryWebKeyDirectoryStore;
import org.apache.james.wkd.store.WebKeyDirectoryStore;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class WebKeyDirectoryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(WebKeyDirectoryServer.class).in(Scopes.SINGLETON);
        bind(WebKeyDirectoryStore.class).to(MemoryWebKeyDirectoryStore.class).in(Scopes.SINGLETON);
        bind(WebKeyDirectorySubmissionAddressKeyPairManager.class).in(Scopes.SINGLETON);
    }
}
