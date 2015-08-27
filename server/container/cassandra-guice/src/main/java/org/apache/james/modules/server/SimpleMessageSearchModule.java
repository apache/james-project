package org.apache.james.modules.server;

import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;

public class SimpleMessageSearchModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(new TypeLiteral<MessageSearchIndex<CassandraId>>(){}).to(new TypeLiteral<SimpleMessageSearchIndex<CassandraId>>(){});
    }
    
}
