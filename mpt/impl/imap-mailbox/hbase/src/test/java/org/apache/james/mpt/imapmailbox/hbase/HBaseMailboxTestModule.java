package org.apache.james.mpt.imapmailbox.hbase;

import org.apache.james.mpt.api.HostSystem;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.hbase.host.HBaseHostSystem;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class HBaseMailboxTestModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(HostSystem.class).to(ImapHostSystem.class);
    }

    @Provides
    @Singleton
    public ImapHostSystem provideHostSystem() throws Exception {
        return HBaseHostSystem.build();
    }

}
