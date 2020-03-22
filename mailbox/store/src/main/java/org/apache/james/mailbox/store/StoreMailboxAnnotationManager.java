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

package org.apache.james.mailbox.store;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxAnnotationManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.AnnotationException;
import org.apache.james.mailbox.exception.InsufficientRightsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.transaction.Mapper;

public class StoreMailboxAnnotationManager implements MailboxAnnotationManager {

    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;

    private final StoreRightManager rightManager;
    private final int limitOfAnnotations;
    private final int limitAnnotationSize;

    @Inject
    public StoreMailboxAnnotationManager(MailboxSessionMapperFactory mailboxSessionMapperFactory,
                                         StoreRightManager rightManager) {
        this(mailboxSessionMapperFactory,
            rightManager,
            MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX,
            MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE);
    }

    public StoreMailboxAnnotationManager(MailboxSessionMapperFactory mailboxSessionMapperFactory,
                                         StoreRightManager rightManager,
                                         int limitOfAnnotations,
                                         int limitAnnotationSize) {
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.rightManager = rightManager;
        this.limitOfAnnotations = limitOfAnnotations;
        this.limitAnnotationSize = limitAnnotationSize;
    }

    public MailboxId checkThenGetMailboxId(MailboxPath path, MailboxSession session) throws MailboxException {
        MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mailboxMapper.findMailboxByPathBlocking(path);
        if (!rightManager.hasRight(mailbox, Right.Read, session)) {
            throw new InsufficientRightsException("Not enough rights on " + path);
        }
        return mailbox.getMailboxId();
    }

    @Override
    public List<MailboxAnnotation> getAllAnnotations(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);

        MailboxId mailboxId = checkThenGetMailboxId(mailboxPath, session);

        return annotationMapper.execute(
            () -> annotationMapper.getAllAnnotations(mailboxId));
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeys(MailboxPath mailboxPath, MailboxSession session, final Set<MailboxAnnotationKey> keys)
            throws MailboxException {
        AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        MailboxId mailboxId = checkThenGetMailboxId(mailboxPath, session);

        return annotationMapper.execute(
            () -> annotationMapper.getAnnotationsByKeys(mailboxId, keys));
    }

    @Override
    public void updateAnnotations(MailboxPath mailboxPath, MailboxSession session, List<MailboxAnnotation> mailboxAnnotations)
            throws MailboxException {
        AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        MailboxId mailboxId = checkThenGetMailboxId(mailboxPath, session);

        annotationMapper.execute(Mapper.toTransaction(() -> {
            for (MailboxAnnotation annotation : mailboxAnnotations) {
                if (annotation.isNil()) {
                    annotationMapper.deleteAnnotation(mailboxId, annotation.getKey());
                } else if (canInsertOrUpdate(mailboxId, annotation, annotationMapper)) {
                    annotationMapper.insertAnnotation(mailboxId, annotation);
                }
            }
        }));
    }

    private boolean canInsertOrUpdate(MailboxId mailboxId, MailboxAnnotation annotation, AnnotationMapper annotationMapper) throws AnnotationException {
        if (annotation.size() > limitAnnotationSize) {
            throw new AnnotationException("annotation too big.");
        }
        if (!annotationMapper.exist(mailboxId, annotation)
            && annotationMapper.countAnnotations(mailboxId) >= limitOfAnnotations) {
            throw new AnnotationException("too many annotations.");
        }
        return true;
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxPath mailboxPath, MailboxSession session,
            Set<MailboxAnnotationKey> keys) throws MailboxException {
        AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        final MailboxId mailboxId = checkThenGetMailboxId(mailboxPath, session);

        return annotationMapper.execute(
            () -> annotationMapper.getAnnotationsByKeysWithOneDepth(mailboxId, keys));
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxPath mailboxPath, MailboxSession session,
            Set<MailboxAnnotationKey> keys) throws MailboxException {
        AnnotationMapper annotationMapper = mailboxSessionMapperFactory.getAnnotationMapper(session);
        MailboxId mailboxId = checkThenGetMailboxId(mailboxPath, session);

        return annotationMapper.execute(
            () -> annotationMapper.getAnnotationsByKeysWithAllDepth(mailboxId, keys));
    }
}
