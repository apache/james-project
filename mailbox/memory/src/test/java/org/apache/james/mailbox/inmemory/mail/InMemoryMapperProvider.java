package org.apache.james.mailbox.inmemory.mail;

import java.util.List;
import java.util.Random;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageUidProvider;

import com.google.common.collect.ImmutableList;

public class InMemoryMapperProvider implements MapperProvider {

    private final Random random;
    private final MessageId.Factory messageIdFactory;
    private final MessageUidProvider messageUidProvider;
    private final InMemoryMailboxSessionMapperFactory inMemoryMailboxSessionMapperFactory;


    public InMemoryMapperProvider() {
        random = new Random();
        messageIdFactory = new InMemoryMessageId.Factory();
        messageUidProvider = new MessageUidProvider();
        inMemoryMailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
    }

    @Override
    public MailboxMapper createMailboxMapper() throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.createMailboxMapper(new MockMailboxSession("user"));
    }

    @Override
    public MessageMapper createMessageMapper() throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.createMessageMapper(new MockMailboxSession("user"));
    }

    @Override
    public MessageIdMapper createMessageIdMapper() throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public AttachmentMapper createAttachmentMapper() throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.createAttachmentMapper(new MockMailboxSession("user"));
    }

    @Override
    public InMemoryId generateId() {
        return InMemoryId.of(random.nextInt());
    }

    @Override
    public MessageUid generateMessageUid() {
        return messageUidProvider.next();
    }

    @Override
    public void clearMapper() throws MailboxException {
        inMemoryMailboxSessionMapperFactory.deleteAll();
    }

    @Override
    public void ensureMapperPrepared() throws MailboxException {

    }

    @Override
    public boolean supportPartialAttachmentFetch() {
        return false;
    }

    @Override
    public AnnotationMapper createAnnotationMapper() throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.createAnnotationMapper(new MockMailboxSession("user"));
    }
    
    @Override
    public MessageId generateMessageId() {
        return messageIdFactory.generate();
    }

    @Override
    public List<Capabilities> getSupportedCapabilities() {
        return ImmutableList.copyOf(Capabilities.values());
    }

    @Override
    public long generateModSeq(Mailbox mailbox) throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.getModSeqProvider()
                .nextModSeq(new MockMailboxSession("user"), mailbox);
    }


    @Override
    public long highestModSeq(Mailbox mailbox) throws MailboxException {
        return inMemoryMailboxSessionMapperFactory.getModSeqProvider()
            .highestModSeq(new MockMailboxSession("user"), mailbox);
    }
}
