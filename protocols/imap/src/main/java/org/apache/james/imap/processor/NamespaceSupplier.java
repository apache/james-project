/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.imap.processor;

import java.util.Collection;

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
            return ImmutableList.of(new NamespaceResponse.Namespace("#user", session.getPathDelimiter()));
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
