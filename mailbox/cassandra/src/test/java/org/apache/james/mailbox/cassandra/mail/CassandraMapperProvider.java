package org.apache.james.mailbox.cassandra.mail;

import org.apache.james.backends.cassandra.CassandraClusterSingleton;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MapperProvider;

public class CassandraMapperProvider implements MapperProvider<CassandraId> {

    private static final CassandraClusterSingleton cassandra = CassandraClusterSingleton.create(new CassandraMailboxModule());

    @Override
    public MailboxMapper<CassandraId> createMailboxMapper() throws MailboxException {
        return new CassandraMailboxSessionMapperFactory(
            new CassandraUidProvider(cassandra.getConf()),
            new CassandraModSeqProvider(cassandra.getConf()),
            cassandra.getConf(),
            cassandra.getTypesProvider()
        ).getMailboxMapper(new MockMailboxSession("benwa"));
    }

    @Override
    public MessageMapper<CassandraId> createMessageMapper() throws MailboxException {
        return new CassandraMailboxSessionMapperFactory(
            new CassandraUidProvider(cassandra.getConf()),
            new CassandraModSeqProvider(cassandra.getConf()),
            cassandra.getConf(),
            cassandra.getTypesProvider()
        ).getMessageMapper(new MockMailboxSession("benwa"));
    }

    @Override
    public CassandraId generateId() {
        return CassandraId.timeBased();
    }

    @Override
    public void clearMapper() throws MailboxException {
        cassandra.clearAllTables();
    }

    @Override
    public void ensureMapperPrepared() throws MailboxException {
        cassandra.ensureAllTables();
    }
}
