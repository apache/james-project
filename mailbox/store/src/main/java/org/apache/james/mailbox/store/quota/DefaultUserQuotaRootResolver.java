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

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

public class DefaultUserQuotaRootResolver implements UserQuotaRootResolver {

    public static final String SEPARATOR = "&"; // Character illegal for mailbox naming in regard of RFC 3501 section 5.1

    private final MailboxSessionMapperFactory factory;

    @Inject
    public DefaultUserQuotaRootResolver(MailboxSessionMapperFactory factory) {
        this.factory = factory;
    }

    @Override
    public QuotaRoot forUser(User user) {
        return QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + user.asString(),
            user.getDomainPart());
    }

    @Override
    public QuotaRoot getQuotaRoot(MailboxPath mailboxPath) {
        Preconditions.checkArgument(!mailboxPath.getNamespace().contains(SEPARATOR), "Namespace should not contain " + SEPARATOR);
        return Optional.ofNullable(mailboxPath.getUser())
                .map(user -> {
                    Preconditions.checkArgument(!mailboxPath.getUser().contains(SEPARATOR), "Username should not contain " + SEPARATOR);
                    return User.fromUsername(mailboxPath.getUser());
                })
                .map(user -> QuotaRoot.quotaRoot(mailboxPath.getNamespace() + SEPARATOR + user.asString(), user.getDomainPart()))
                .orElseGet(() -> QuotaRoot.quotaRoot(mailboxPath.getNamespace(), Optional.empty()));
    }

    @Override
    public QuotaRoot fromString(String serializedQuotaRoot) throws MailboxException {
        List<String> parts = toParts(serializedQuotaRoot);
        User user = User.fromUsername(parts.get(1));

        return QuotaRoot.quotaRoot(serializedQuotaRoot, user.getDomainPart());
    }

    @Override
    public List<MailboxPath> retrieveAssociatedMailboxes(QuotaRoot quotaRoot, MailboxSession mailboxSession) throws MailboxException {
        List<String> parts = toParts(quotaRoot.getValue());
        String namespace = parts.get(0);
        String user = parts.get(1);
        return Lists.transform(factory.getMailboxMapper(mailboxSession)
            .findMailboxWithPathLike(new MailboxPath(namespace, user, "%")),
            Mailbox::generateAssociatedPath);
    }

    public List<String> toParts(String serializedQuotaRoot) throws MailboxException {
        List<String> parts = Splitter.on(SEPARATOR).splitToList(serializedQuotaRoot);
        if (parts.size() != 2) {
            throw new MailboxException(serializedQuotaRoot + " used as QuotaRoot should contain exactly one \"" + SEPARATOR + "\"");
        }
        return parts;
    }
}
