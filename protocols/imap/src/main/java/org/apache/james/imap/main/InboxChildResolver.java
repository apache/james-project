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

package org.apache.james.imap.main;

import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.collect.Lists;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * INBOX is case-insensitive, also as the first level of a hierarchical name (RFC 9051 section 5.1): {@code inbox.sub}
 * designates {@code INBOX.sub}.
 *
 * Mailboxes may however be stored with another case of INBOX as their first level (eg {@code Inbox.Archive}, created
 * by an older version or a migration). Such a mailbox, and its sub-tree, stays reachable by the name it is stored with.
 */
public class InboxChildResolver {
    private final MailboxManager mailboxManager;

    public InboxChildResolver(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    /**
     * The path of an existing mailbox designated by the client: the name as given when such a mailbox exists, its
     * INBOX child form otherwise.
     */
    public Mono<MailboxPath> existingMailbox(MailboxPath requestedPath, MailboxSession session) {
        return inboxChild(requestedPath, session.getPathDelimiter())
            .map(inboxChild -> Mono.from(mailboxManager.mailboxExists(requestedPath, session))
                .map(exists -> exists ? requestedPath : inboxChild)
                // the command acting on the name as given reports the failure
                .onErrorReturn(requestedPath))
            .orElse(Mono.just(requestedPath));
    }

    /**
     * The path of a mailbox to be created (CREATE, RENAME target): the name as given when it is, or goes under, a
     * mailbox stored with that name, its INBOX child form otherwise.
     */
    public Mono<MailboxPath> newMailbox(MailboxPath requestedPath, MailboxSession session) {
        char delimiter = session.getPathDelimiter();
        return inboxChild(requestedPath, delimiter)
            .map(inboxChild -> {
                // The first level alone designates INBOX: only the deeper levels may be stored with another case
                List<MailboxPath> levels = requestedPath.getHierarchyLevels(delimiter);
                return Flux.fromIterable(Lists.reverse(levels.subList(1, levels.size())))
                    .concatMap(level -> mailboxManager.mailboxExists(level, session))
                    .any(exists -> exists)
                    .map(storedHierarchy -> storedHierarchy ? requestedPath : inboxChild)
                    // the command acting on the name as given reports the failure
                    .onErrorReturn(requestedPath);
            })
            .orElse(Mono.just(requestedPath));
    }

    /**
     * @return the INBOX child form of this path when its first level is INBOX in another case
     */
    static Optional<MailboxPath> inboxChild(MailboxPath path, char delimiter) {
        String name = path.getName();
        String inboxPrefix = MailboxConstants.INBOX + delimiter;
        if (name == null
            || !MailboxConstants.USER_NAMESPACE.equals(path.getNamespace())
            || name.startsWith(inboxPrefix)
            || !name.regionMatches(true, 0, inboxPrefix, 0, inboxPrefix.length())) {
            return Optional.empty();
        }
        return Optional.of(new MailboxPath(path.getNamespace(), path.getUser(), inboxPrefix + name.substring(inboxPrefix.length())));
    }
}
