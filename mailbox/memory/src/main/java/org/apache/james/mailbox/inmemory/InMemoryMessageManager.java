package org.apache.james.mailbox.inmemory;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.MessageFactory;
import org.apache.james.mailbox.store.MessageStorer;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

public class InMemoryMessageManager extends StoreMessageManager {
    public InMemoryMessageManager(MailboxSessionMapperFactory mapperFactory,
                                  MessageSearchIndex index,
                                  EventBus eventBus,
                                  MailboxPathLocker locker,
                                  Mailbox mailbox,
                                  QuotaManager quotaManager,
                                  QuotaRootResolver quotaRootResolver,
                                  MessageParser messageParser,
                                  MessageId.Factory messageIdFactory,
                                  BatchSizes batchSizes,
                                  StoreRightManager storeRightManager,
                                  PreDeletionHooks preDeletionHooks) {

        super(InMemoryMailboxManager.MESSAGE_CAPABILITIES, mapperFactory, index, eventBus, locker, mailbox, quotaManager, quotaRootResolver,
            batchSizes, storeRightManager, preDeletionHooks,
            new MessageStorer.WithAttachment(mapperFactory, messageIdFactory, new MessageFactory.StoreMessageFactory(), (InMemoryMailboxSessionMapperFactory) mapperFactory, messageParser));
    }

    @Override
    protected Flags getPermanentFlags(MailboxSession session) {
        Flags permanentFlags = new Flags(super.getPermanentFlags(session));
        permanentFlags.add(Flags.Flag.USER);
        return permanentFlags;
    }
}
