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

package org.apache.james.mailbox.jpa.mail;

import java.util.List;
import java.util.Set;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.transaction.Mapper;

public class TransactionalAnnotationMapper implements AnnotationMapper {
    private final JPAAnnotationMapper wrapped;

    public TransactionalAnnotationMapper(JPAAnnotationMapper wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public List<MailboxAnnotation> getAllAnnotations(MailboxId mailboxId) {
        return wrapped.getAllAnnotations(mailboxId);
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeys(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return wrapped.getAnnotationsByKeys(mailboxId, keys);
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return wrapped.getAnnotationsByKeysWithOneDepth(mailboxId, keys);
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return wrapped.getAnnotationsByKeysWithAllDepth(mailboxId, keys);
    }

    @Override
    public void deleteAnnotation(final MailboxId mailboxId, final MailboxAnnotationKey key) {
        try {
            wrapped.execute(Mapper.toTransaction(() -> wrapped.deleteAnnotation(mailboxId, key)));
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void insertAnnotation(final MailboxId mailboxId, final MailboxAnnotation mailboxAnnotation) {
        try {
            wrapped.execute(Mapper.toTransaction(() -> wrapped.insertAnnotation(mailboxId, mailboxAnnotation)));
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean exist(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        return wrapped.exist(mailboxId, mailboxAnnotation);
    }

    @Override
    public int countAnnotations(MailboxId mailboxId) {
        return wrapped.countAnnotations(mailboxId);
    }
}
