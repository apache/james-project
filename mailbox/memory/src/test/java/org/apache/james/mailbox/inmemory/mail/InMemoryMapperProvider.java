package org.apache.james.mailbox.inmemory.mail;

import java.util.Random;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MapperProvider;

public class InMemoryMapperProvider implements MapperProvider<InMemoryId> {

    private final Random random;

    public InMemoryMapperProvider() {
        random = new Random();
    }

    @Override
    public MailboxMapper<InMemoryId> createMailboxMapper() throws MailboxException {
        return new InMemoryMailboxSessionMapperFactory().createMailboxMapper(new MockMailboxSession("user"));
    }

    @Override
    public MessageMapper<InMemoryId> createMessageMapper() throws MailboxException {
        return new InMemoryMailboxSessionMapperFactory().createMessageMapper(new MockMailboxSession("user"));
    }

    @Override
    public InMemoryId generateId() {
        return InMemoryId.of(random.nextInt());
    }

    @Override
    public void clearMapper() throws MailboxException {

    }

    @Override
    public void ensureMapperPrepared() throws MailboxException {

    }
}
