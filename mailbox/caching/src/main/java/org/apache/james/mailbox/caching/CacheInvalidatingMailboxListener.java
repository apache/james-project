package org.apache.james.mailbox.caching;

import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A MailboxListener that invalidates the configured caches in response to Events
 *
 * @deprecated JAMES-2703 This class is deprecated and will be removed straight after upcoming James 3.4.0 release, unless it finds a maintainer
 *
 * This module lacks tests and is not used in James products hence the choice to deprecate it.
 */
@Deprecated
public class CacheInvalidatingMailboxListener implements MailboxListener.GroupMailboxListener {
    public static class CacheInvalidatingMailboxListenerGroup extends Group {}

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheInvalidatingMailboxListener.class);
    private static final Group GROUP = new CacheInvalidatingMailboxListenerGroup();

    private final MailboxByPathCache mailboxCacheByPath;
    private final MailboxMetadataCache mailboxMetadataCache;

    public CacheInvalidatingMailboxListener(MailboxByPathCache mailboxCacheByPath, MailboxMetadataCache mailboxMetadataCache) {
        this.mailboxCacheByPath = mailboxCacheByPath;
        this.mailboxMetadataCache = mailboxMetadataCache;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    /**
     * Used to register the CacheInvalidatingMailboxListener as a global listener
     * into the main MailboxListener
     *
     * @param eventBus
     */
    public void register(EventBus eventBus) {
        eventBus.register(this);
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxEvent;
    }

    @Override
    public void event(Event event) {
            mailboxEvent((MailboxEvent) event);
    }

    private void mailboxEvent(MailboxEvent event) {
        // TODO this needs for sure to be smarter
        try {
            if (event instanceof MessageEvent) {
                // invalidate the metadata caches
                invalidateMetadata(event);
            }
            invalidateMailbox(event);
        } catch (MailboxException e) {
            LOGGER.error("Error while invalidation cache", e);
        }
    }

    private void invalidateMetadata(MailboxEvent event) throws MailboxException {
        //HMM, race conditions welcome?
        mailboxMetadataCache.invalidate(mailboxCacheByPath.findMailboxByPath(event.getMailboxPath(), null));

    }

    private void invalidateMailbox(MailboxEvent event) {
        mailboxCacheByPath.invalidate(event.getMailboxPath());
    }

}
