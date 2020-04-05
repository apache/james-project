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

package org.apache.james.mailbox.store.quota;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.quota.QuotaRootDeserializer;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

public class DefaultUserQuotaRootResolver implements UserQuotaRootResolver {

    public static class DefaultQuotaRootDeserializer implements QuotaRootDeserializer {
        @Override
        public QuotaRoot fromString(String serializedQuotaRoot) throws MailboxException {
            List<String> parts = toParts(serializedQuotaRoot);
            Username username = Username.of(parts.get(1));

            return QuotaRoot.quotaRoot(serializedQuotaRoot, username.getDomainPart());
        }

        private List<String> toParts(String serializedQuotaRoot) throws MailboxException {
            List<String> parts = Splitter.on(SEPARATOR).splitToList(serializedQuotaRoot);
            if (parts.size() != 2) {
                throw new MailboxException(serializedQuotaRoot + " used as QuotaRoot should contain exactly one \"" + SEPARATOR + "\"");
            }
            return parts;
        }
    }

    public static final String SEPARATOR = "&"; // Character illegal for mailbox naming in regard of RFC 3501 section 5.1
    private static final DefaultQuotaRootDeserializer QUOTA_ROOT_DESERIALIZER = new DefaultQuotaRootDeserializer();

    private final SessionProvider sessionProvider;
    private final MailboxSessionMapperFactory factory;

    @Inject
    public DefaultUserQuotaRootResolver(SessionProvider sessionProvider, MailboxSessionMapperFactory factory) {
        this.sessionProvider = sessionProvider;
        this.factory = factory;
    }

    @Override
    public QuotaRoot forUser(Username username) {
        return QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + username.asString(),
            username.getDomainPart());
    }

    @Override
    public QuotaRoot getQuotaRoot(MailboxPath mailboxPath) {
        Preconditions.checkArgument(!mailboxPath.getNamespace().contains(SEPARATOR), "Namespace should not contain %s", SEPARATOR);
        return Optional.ofNullable(mailboxPath.getUser())
                .map(user -> {
                    Preconditions.checkArgument(!mailboxPath.getUser().asString().contains(SEPARATOR), "Username should not contain %s", SEPARATOR);
                    return mailboxPath.getUser();
                })
                .map(user -> QuotaRoot.quotaRoot(mailboxPath.getNamespace() + SEPARATOR + user.asString(), user.getDomainPart()))
                .orElseGet(() -> QuotaRoot.quotaRoot(mailboxPath.getNamespace(), Optional.empty()));
    }

    @Override
    public QuotaRoot getQuotaRoot(MailboxId mailboxId) throws MailboxException {
        MailboxSession session = sessionProvider.createSystemSession(Username.of("DefaultUserQuotaRootResolver"));
        Username username = factory.getMailboxMapper(session)
            .findMailboxById(mailboxId)
            .generateAssociatedPath()
            .getUser();

        return forUser(username);
    }

    @Override
    public QuotaRoot fromString(String serializedQuotaRoot) throws MailboxException {
        return QUOTA_ROOT_DESERIALIZER.fromString(serializedQuotaRoot);
    }

    @Override
    public List<Mailbox> retrieveAssociatedMailboxes(QuotaRoot quotaRoot, MailboxSession mailboxSession) throws MailboxException {
        List<String> parts = QUOTA_ROOT_DESERIALIZER.toParts(quotaRoot.getValue());
        String namespace = parts.get(0);
        String user = parts.get(1);
        return factory.getMailboxMapper(mailboxSession)
            .findMailboxWithPathLike(MailboxQuery.builder()
                .namespace(namespace)
                .user(Username.of(user))
                .matchesAllMailboxNames()
                .build()
                .asUserBound())
            .collectList().block();
    }
}
