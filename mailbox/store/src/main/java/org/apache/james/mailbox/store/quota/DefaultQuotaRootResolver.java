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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class DefaultQuotaRootResolver implements QuotaRootResolver {

    public static final String SEPARATOR = "&"; // Character illegal for mailbox naming in regard of RFC 3501 section 5.1

    private final MailboxSessionMapperFactory factory;

    @Inject
    public DefaultQuotaRootResolver(MailboxSessionMapperFactory factory) {
        this.factory = factory;
    }

    @Override
    public QuotaRoot createQuotaRoot(String quotaRootValue) {
        return QuotaRootImpl.quotaRoot(quotaRootValue);
    }

    @Override
    public QuotaRoot getQuotaRoot(MailboxPath mailboxPath) throws MailboxException {
        Preconditions.checkArgument(!mailboxPath.getNamespace().contains(SEPARATOR), "Namespace should not contain " + SEPARATOR);
        Preconditions.checkArgument(!mailboxPath.getUser().contains(SEPARATOR), "Username should not contain " + SEPARATOR);
        return QuotaRootImpl.quotaRoot(mailboxPath.getNamespace() + SEPARATOR + mailboxPath.getUser());
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<MailboxPath> retrieveAssociatedMailboxes(QuotaRoot quotaRoot, MailboxSession mailboxSession) throws MailboxException {
        List<String> parts = Lists.newArrayList(Splitter.on(SEPARATOR).split(quotaRoot.getValue()));
        if (parts.size() != 2) {
            throw new MailboxException(quotaRoot + " used as QuotaRoot should not contain 2 \""+SEPARATOR+"\"");
        }
        String namespace = parts.get(0);
        String user = parts.get(1);
        return Lists.transform(factory.getMailboxMapper(mailboxSession).findMailboxWithPathLike(new MailboxPath(namespace, user, "%")),
            new Function<Mailbox, MailboxPath>() {
                @Override
                public MailboxPath apply(Mailbox idMailbox) {
                    return new MailboxPath(idMailbox.getNamespace(), idMailbox.getUser(), idMailbox.getName());
                }
            });
    }
}
