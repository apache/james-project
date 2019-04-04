package org.apache.james.mailbox.caching;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;

/**
 * Caches the MailboxPath -> Mailbox mapping
 *
 * @deprecated JAMES-2703 This class is deprecated and will be removed straight after upcoming James 3.4.0 release, unless it finds a maintainer
 *
 * This module lacks tests and is not used in James products hence the choice to deprecate it.
 */
@Deprecated
public interface MailboxByPathCache {

    Mailbox findMailboxByPath(MailboxPath mailboxName,
                                  MailboxMapper underlying) throws MailboxNotFoundException,
            MailboxException;

    void invalidate(Mailbox mailbox);

    void invalidate(MailboxPath mailboxPath);

    // for the purpose of cascading the invalidations; does it make sense?
    //public void connectTo(MailboxMetadataCache<Id> mailboxMetadataCache);

}
