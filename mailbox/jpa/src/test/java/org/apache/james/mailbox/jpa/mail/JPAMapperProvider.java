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

import java.util.HashMap;
import java.util.List;
import java.util.Random;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.jpa.mail.model.JPAMailboxAnnotation;
import org.apache.james.mailbox.jpa.mail.model.JPAProperty;
import org.apache.james.mailbox.jpa.mail.model.JPAUserFlag;
import org.apache.james.mailbox.jpa.mail.model.openjpa.AbstractJPAMailboxMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAMailboxMessage;
import org.apache.james.mailbox.jpa.user.model.JPASubscription;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.openjpa.persistence.OpenJPAPersistence;

import com.google.common.collect.ImmutableList;

public class JPAMapperProvider implements MapperProvider {

    @Override
    public MailboxMapper createMailboxMapper() throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public MessageMapper createMessageMapper() throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public AttachmentMapper createAttachmentMapper() throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public AnnotationMapper createAnnotationMapper() throws MailboxException {
        return new TransactionalAnnotationMapper(new JPAAnnotationMapper(createEntityManagerFactory()));
    }

    @Override
    public MailboxId generateId() {
        return JPAId.of(Math.abs(new Random().nextInt()));
    }

    @Override
    public MessageId generateMessageId() {
        return new DefaultMessageId.Factory().generate();
    }

    @Override
    public void clearMapper() throws MailboxException {
        EntityManager entityManager = createEntityManagerFactory().createEntityManager();
        entityManager.getTransaction().begin();
        entityManager.createNativeQuery("TRUNCATE table JAMES_MAIL_USERFLAG;");
        entityManager.createNativeQuery("TRUNCATE table JAMES_MAIL_PROPERTY;");
        entityManager.createNativeQuery("TRUNCATE table JAMES_MAILBOX_ANNOTATION;");
        entityManager.createNativeQuery("TRUNCATE table JAMES_MAILBOX;");
        entityManager.createNativeQuery("TRUNCATE table JAMES_MAIL;");
        entityManager.getTransaction().commit();
        entityManager.close();
    }

    @Override
    public void ensureMapperPrepared() throws MailboxException {

    }

    @Override
    public boolean supportPartialAttachmentFetch() {
        return false;
    }

    private EntityManagerFactory createEntityManagerFactory() {
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("openjpa.ConnectionDriverName", "org.h2.Driver");
        properties.put("openjpa.ConnectionURL", "jdbc:h2:mem:imap;DB_CLOSE_DELAY=-1");
        properties.put("openjpa.Log", "JDBC=WARN, SQL=WARN, Runtime=WARN");
        properties.put("openjpa.ConnectionFactoryProperties", "PrettyPrint=true, PrettyPrintLineLength=72");
        properties.put("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)");
        properties.put("openjpa.MetaDataFactory", "jpa(Types=" +
            JPAMailbox.class.getName() + ";" +
            AbstractJPAMailboxMessage.class.getName() + ";" +
            JPAMailboxMessage.class.getName() + ";" +
            JPAProperty.class.getName() + ";" +
            JPAUserFlag.class.getName() + ";" +
            JPAMailboxAnnotation.class.getName() + ";" +
            JPASubscription.class.getName() + ")");

        return OpenJPAPersistence.getEntityManagerFactory(properties);
    }

    @Override
    public List<Capabilities> getNotImplemented() {
        return ImmutableList.of(Capabilities.MAILBOX, Capabilities.MESSAGE, Capabilities.ATTACHMENT);
    }
}
