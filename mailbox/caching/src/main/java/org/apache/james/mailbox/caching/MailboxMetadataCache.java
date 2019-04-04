package org.apache.james.mailbox.caching;

import java.util.Optional;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.store.mail.MessageMapper;

/**
 * Caches the simple yet possibly expensive to compute metadata info 
 * about a Mailbox like all/unseen messages count and similar
 *
 * @deprecated JAMES-2703 This class is deprecated and will be removed straight after upcoming James 3.4.0 release, unless it finds a maintainer
 *
 * This module lacks tests and is not used in James products hence the choice to deprecate it.
 */
@Deprecated
public interface MailboxMetadataCache {

    long countMessagesInMailbox(Mailbox mailbox,
                                MessageMapper underlying) throws MailboxException;

    long countUnseenMessagesInMailbox(Mailbox mailbox,
                                        MessageMapper underlying) throws MailboxException;

    MessageUid findFirstUnseenMessageUid(Mailbox mailbox,
                                            MessageMapper underlying) throws MailboxException;

    Optional<MessageUid> getLastUid(Mailbox mailbox,
                                    MessageMapper underlying) throws MailboxException;

    long getHighestModSeq(Mailbox mailbox,
                            MessageMapper underlying) throws MailboxException;

    void invalidate(Mailbox mailbox);

}