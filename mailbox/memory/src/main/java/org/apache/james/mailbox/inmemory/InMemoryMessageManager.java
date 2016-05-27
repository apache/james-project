package org.apache.james.mailbox.inmemory;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

public class InMemoryMessageManager extends StoreMessageManager {

    public InMemoryMessageManager(MailboxSessionMapperFactory mapperFactory, MessageSearchIndex index, MailboxEventDispatcher dispatcher, MailboxPathLocker locker, Mailbox mailbox, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver, MessageParser messageParser) throws MailboxException {
        super(mapperFactory, index, dispatcher, locker, mailbox, aclResolver, groupMembershipResolver, quotaManager, quotaRootResolver, messageParser);
    }

    @Override
    protected Flags getPermanentFlags(MailboxSession session) {
        Flags permanentFlags = new Flags(super.getPermanentFlags(session));
        permanentFlags.add(Flags.Flag.USER);
        return permanentFlags;
    }
}
