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

package org.apache.james.jmap.utils;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.github.fge.lambdas.functions.ThrowingFunction;
import com.github.fge.lambdas.supplier.ThrowingSupplier;
import com.google.common.annotations.VisibleForTesting;

public class SystemMailboxesProviderImpl implements SystemMailboxesProvider {

    private final MailboxMapperFactory mailboxMapperFactory;
    private final MailboxManager mailboxManager;

    @Inject
    @VisibleForTesting SystemMailboxesProviderImpl(MailboxMapperFactory mailboxMapperFactory, MailboxManager mailboxManager) {
        this.mailboxMapperFactory = mailboxMapperFactory;
        this.mailboxManager = mailboxManager;
    }

    private boolean hasRole(Role aRole, MailboxPath mailBoxPath) {
        return Role.from(mailBoxPath.getName())
                .map(aRole::equals)
                .orElse(false);
    }

    public Stream<Mailbox> listMailboxes(Role aRole, MailboxSession session) {
        ThrowingSupplier<List<MailboxMetaData>> getAllMailboxes = () -> mailboxManager.search(MailboxQuery.builder(session).privateUserMailboxes().build(), session);
        Predicate<MailboxPath> hasSpecifiedRole = path -> hasRole(aRole, path);
        return getAllMailboxes.get().stream()
                .map(MailboxMetaData::getPath)
                .filter(hasSpecifiedRole)
                .map(loadMailbox(session));
    }

    private ThrowingFunction<MailboxPath, Mailbox> loadMailbox(MailboxSession session) {
        return path -> mailboxMapperFactory.getMailboxMapper(session).findMailboxByPath(path);
    }
}
