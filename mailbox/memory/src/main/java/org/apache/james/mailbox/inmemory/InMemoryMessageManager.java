package org.apache.james.mailbox.inmemory;

import java.util.List;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.ImmutableMailboxMessage;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

import com.github.steveash.guavate.Guavate;

public class InMemoryMessageManager extends StoreMessageManager {

    private InMemoryMailboxSessionMapperFactory mapperFactory;

    public InMemoryMessageManager(MailboxSessionMapperFactory mapperFactory,
                                  MessageSearchIndex index,
                                  MailboxEventDispatcher dispatcher,
                                  MailboxPathLocker locker,
                                  Mailbox mailbox,
                                  QuotaManager quotaManager,
                                  QuotaRootResolver quotaRootResolver,
                                  MessageParser messageParser,
                                  MessageId.Factory messageIdFactory,
                                  BatchSizes batchSizes,
                                  ImmutableMailboxMessage.Factory immutableMailboxMessageFactory,
                                  StoreRightManager storeRightManager) throws MailboxException {
        super(mapperFactory, index, dispatcher, locker, mailbox, quotaManager, quotaRootResolver,
            messageParser, messageIdFactory, batchSizes, immutableMailboxMessageFactory, storeRightManager);
        this.mapperFactory = (InMemoryMailboxSessionMapperFactory) mapperFactory;
    }

    @Override
    protected Flags getPermanentFlags(MailboxSession session) {
        Flags permanentFlags = new Flags(super.getPermanentFlags(session));
        permanentFlags.add(Flags.Flag.USER);
        return permanentFlags;
    }

    @Override
    protected void storeAttachment(final MailboxMessage message, final List<MessageAttachment> messageAttachments, final MailboxSession session) throws MailboxException {
        mapperFactory.getAttachmentMapper(session)
            .storeAttachmentsForMessage(
                messageAttachments.stream()
                    .map(MessageAttachment::getAttachment)
                    .collect(Guavate.toImmutableList()),
                message.getMessageId());
    }

    @Override
    protected MailboxMessage copyMessage(MailboxMessage message) throws MailboxException {
        SimpleMailboxMessage copy = SimpleMailboxMessage.copy(message.getMailboxId(), message);
        copy.setUid(message.getUid());
        copy.setModSeq(message.getModSeq());
        return copy;
    }
}
