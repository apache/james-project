package org.apache.james.imap.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.james.imap.message.response.NamespaceResponse;
import org.apache.james.imap.message.response.NamespaceResponse.Namespace;
import org.apache.james.mailbox.MailboxSession;

import com.google.common.collect.ImmutableList;

public interface NamespaceSupplier {
    class Default implements NamespaceSupplier {
        @Override
        public Collection<Namespace> personalNamespaces(MailboxSession session) {
            return ImmutableList.of(new NamespaceResponse.Namespace("", session.getPathDelimiter()));
        }

        @Override
        public Collection<Namespace> otherUsersNamespaces(MailboxSession session) {
            return ImmutableList.of();
        }

        @Override
        public Collection<Namespace> sharedNamespaces(MailboxSession session) {
            return ImmutableList.of();
        }
    }

    /**
     * Gets the <a href='http://www.isi.edu/in-notes/rfc2342.txt' rel='tag'>RFC
     * 2342</a> personal namespace for the current session.<br>
     * Note that though servers may offer multiple personal namespaces, support
     * is not offered through this API. This decision may be revised if
     * reasonable use cases emerge.
     *
     * @return Personal Namespace, not null
     */
    Collection<Namespace> personalNamespaces(MailboxSession session);

    /**
     * Gets the <a href='http://www.isi.edu/in-notes/rfc2342.txt' rel='tag'>RFC
     * 2342</a> other users namespace for the current session.<br>
     * Note that though servers may offer multiple other users namespaces,
     * support is not offered through this API. This decision may be revised if
     * reasonable use cases emerge.
     *
     * @return Other Users Namespace or null when there is non available
     */
    Collection<Namespace> otherUsersNamespaces(MailboxSession session);

    /**
     * Iterates the <a href='http://www.isi.edu/in-notes/rfc2342.txt'
     * rel='tag'>RFC 2342</a> Shared Namespaces available for the current
     * session.
     *
     * @return not null though possibly empty
     */
    Collection<Namespace> sharedNamespaces(MailboxSession session);
}
