package org.apache.james.mailbox.caching;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxListenerSupport;
import org.apache.james.mailbox.exception.MailboxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A MailboxListener that invalidates the configured caches in response to Events
 *
 */
public class CacheInvalidatingMailboxListener implements MailboxListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheInvalidatingMailboxListener.class);

    private final MailboxByPathCache mailboxCacheByPath;
    private final MailboxMetadataCache mailboxMetadataCache;

    public CacheInvalidatingMailboxListener(MailboxByPathCache mailboxCacheByPath, MailboxMetadataCache mailboxMetadataCache) {
        this.mailboxCacheByPath = mailboxCacheByPath;
        this.mailboxMetadataCache = mailboxMetadataCache;
    }

    /**
     * Used to register the CacheInvalidatingMailboxListener as a global listener
     * into the main MailboxListener
     *
     * @param listener
     * @throws MailboxException
     */
    public void register(MailboxListenerSupport listener) throws MailboxException {
        listener.addGlobalListener(this, null);
    }

    @Override
    public ListenerType getType() {
        return ListenerType.EACH_NODE;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNCHRONOUS;
    }

    @Override
    public void event(Event event) {
        if (event instanceof MailboxEvent) {
            mailboxEvent((MailboxEvent) event);
        }
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
