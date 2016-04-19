package org.apache.james.mailbox.cassandra.mail;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MapperProvider;

public class CassandraMapperProvider implements MapperProvider<CassandraId> {

    private static final CassandraCluster cassandra = CassandraCluster.create(new CassandraModuleComposite(
        new CassandraAclModule(),
        new CassandraMailboxModule(),
        new CassandraMessageModule(),
        new CassandraMailboxCounterModule(),
        new CassandraModSeqModule(),
        new CassandraUidModule()));

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
