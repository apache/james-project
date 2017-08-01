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

import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;

import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPATransactionalMapper;
import org.apache.james.mailbox.jpa.mail.model.JPAMailboxAnnotation;
import org.apache.james.mailbox.jpa.mail.model.JPAMailboxAnnotationId;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class JPAAnnotationMapper extends JPATransactionalMapper implements AnnotationMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(JPAAnnotationMapper.class);

    public static final Function<JPAMailboxAnnotation, MailboxAnnotation> READ_ROW =
        input -> MailboxAnnotation.newInstance(new MailboxAnnotationKey(input.getKey()), input.getValue());

    public JPAAnnotationMapper(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    public List<MailboxAnnotation> getAllAnnotations(MailboxId mailboxId) {
        JPAId jpaId = (JPAId) mailboxId;
        return Lists.transform(getEntityManager().createNamedQuery("retrieveAllAnnotations", JPAMailboxAnnotation.class)
                .setParameter("idParam", jpaId.getRawId()).getResultList(),
            READ_ROW);
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeys(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        try {
            final JPAId jpaId = (JPAId) mailboxId;
            return ImmutableList.copyOf(Iterables.transform(keys,
                input -> READ_ROW.apply(
                    getEntityManager()
                        .createNamedQuery("retrieveByKey", JPAMailboxAnnotation.class)
                        .setParameter("idParam", jpaId.getRawId())
                        .setParameter("keyParam", input.asString())
                        .getSingleResult())));
        } catch (NoResultException e) {
            return ImmutableList.of();
        }
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return getFilteredLikes((JPAId) mailboxId,
            keys,
            key ->
                annotation ->
                    key.isParentOrIsEqual(annotation.getKey()));
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return getFilteredLikes((JPAId) mailboxId,
            keys,
            key ->
                annotation -> key.isAncestorOrIsEqual(annotation.getKey()));
    }

    private List<MailboxAnnotation> getFilteredLikes(final JPAId jpaId, Set<MailboxAnnotationKey> keys, final Function<MailboxAnnotationKey, Predicate<MailboxAnnotation>> predicateFunction) {
        try {
            return flatMapToList(Iterables.transform(keys,
                key -> ImmutableList.copyOf(
                    Iterables.filter(
                        Iterables.transform(
                            getEntityManager()
                                .createNamedQuery("retrieveByKeyLike", JPAMailboxAnnotation.class)
                                .setParameter("idParam", jpaId.getRawId())
                                .setParameter("keyParam", key.asString() + '%')
                                .getResultList(),
                            READ_ROW),
                        predicateFunction.apply(key)))));
        } catch (NoResultException e) {
            return ImmutableList.of();
        }
    }

    private List<MailboxAnnotation> flatMapToList(Iterable<List<MailboxAnnotation>> likes) {
        ImmutableList.Builder<MailboxAnnotation> resultBuilder = ImmutableList.builder();
        for (List<MailboxAnnotation> mailboxAnnotations: likes) {
            resultBuilder.addAll(mailboxAnnotations);
        }
        return resultBuilder.build();
    }

    @Override
    public void deleteAnnotation(MailboxId mailboxId, MailboxAnnotationKey key) {
        try {
            JPAId jpaId = (JPAId) mailboxId;
            JPAMailboxAnnotation jpaMailboxAnnotation = getEntityManager()
                .find(JPAMailboxAnnotation.class, new JPAMailboxAnnotationId(jpaId.getRawId(), key.asString()));
            getEntityManager().remove(jpaMailboxAnnotation);
        } catch (NoResultException e) {
            LOGGER.debug("Mailbox annotation not found for ID {} and key {}", mailboxId.serialize(), key.asString());
        } catch (PersistenceException pe) {
            throw Throwables.propagate(pe);
        }
    }

    @Override
    public void insertAnnotation(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        Preconditions.checkArgument(!mailboxAnnotation.isNil());
        JPAId jpaId = (JPAId) mailboxId;
        if (getAnnotationsByKeys(mailboxId, ImmutableSet.of(mailboxAnnotation.getKey())).isEmpty()) {
            getEntityManager().persist(
                new JPAMailboxAnnotation(jpaId.getRawId(),
                    mailboxAnnotation.getKey().asString(),
                    mailboxAnnotation.getValue().orNull()));
        } else {
            getEntityManager().find(JPAMailboxAnnotation.class,
                new JPAMailboxAnnotationId(jpaId.getRawId(), mailboxAnnotation.getKey().asString()))
                .setValue(mailboxAnnotation.getValue().orNull());
        }
    }

    @Override
    public boolean exist(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        JPAId jpaId = (JPAId) mailboxId;
        Optional<JPAMailboxAnnotation> row = Optional.fromNullable(getEntityManager().find(JPAMailboxAnnotation.class,
            new JPAMailboxAnnotationId(jpaId.getRawId(), mailboxAnnotation.getKey().asString())));
        return row.isPresent();
    }

    @Override
    public int countAnnotations(MailboxId mailboxId) {
        try {
            JPAId jpaId = (JPAId) mailboxId;
            return ((Long)getEntityManager().createNamedQuery("countAnnotationsInMailbox")
                .setParameter("idParam", jpaId.getRawId()).getSingleResult()).intValue();
        } catch (PersistenceException pe) {
            throw Throwables.propagate(pe);
        }
    }
}
