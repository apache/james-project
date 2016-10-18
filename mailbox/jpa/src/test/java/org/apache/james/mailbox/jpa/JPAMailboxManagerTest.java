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
package org.apache.james.mailbox.jpa;

import java.util.HashMap;

import javax.persistence.EntityManagerFactory;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.mail.JPAModSeqProvider;
import org.apache.james.mailbox.jpa.mail.JPAUidProvider;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.jpa.mail.model.JPAProperty;
import org.apache.james.mailbox.jpa.mail.model.JPAUserFlag;
import org.apache.james.mailbox.jpa.mail.model.openjpa.AbstractJPAMailboxMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAMailboxMessage;
import org.apache.james.mailbox.jpa.openjpa.OpenJPAMailboxManager;
import org.apache.james.mailbox.jpa.user.model.JPASubscription;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.openjpa.persistence.OpenJPAPersistence;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import org.xenei.junit.contract.Contract;
import org.xenei.junit.contract.ContractImpl;
import org.xenei.junit.contract.ContractSuite;
import org.xenei.junit.contract.IProducer;

import com.google.common.base.Throwables;

@RunWith(ContractSuite.class)
@ContractImpl(OpenJPAMailboxManager.class)
public class JPAMailboxManagerTest {

    /**
     * The entity manager factory.
     */
    private static EntityManagerFactory entityManagerFactory;

    private IProducer<OpenJPAMailboxManager> producer = new IProducer<OpenJPAMailboxManager>() {

        private OpenJPAMailboxManager openJPAMailboxManager;

        @Override
        public OpenJPAMailboxManager newInstance() {
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
                JPASubscription.class.getName() + ")");

            entityManagerFactory = OpenJPAPersistence.getEntityManagerFactory(properties);
            JVMMailboxPathLocker locker = new JVMMailboxPathLocker();
            JPAMailboxSessionMapperFactory mf = new JPAMailboxSessionMapperFactory(entityManagerFactory, new JPAUidProvider(locker, entityManagerFactory), new JPAModSeqProvider(locker, entityManagerFactory));

            MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
            GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
            MessageParser messageParser = new MessageParser();

            openJPAMailboxManager = new OpenJPAMailboxManager(mf, null, aclResolver, groupMembershipResolver, messageParser, new DefaultMessageId.Factory());

            try {
                openJPAMailboxManager.init();
            } catch (MailboxException e) {
                throw Throwables.propagate(e);
            }

            return openJPAMailboxManager;
        }

        @Override
        public void cleanUp() {
            MailboxSession session = openJPAMailboxManager.createSystemSession("test", LoggerFactory.getLogger("Test"));
            try {
                openJPAMailboxManager.deleteEverything(session);
            } catch (MailboxException e) {
                e.printStackTrace();
            }
            session.close();
            entityManagerFactory.close();
        }
    };

    @Contract.Inject
    public IProducer<OpenJPAMailboxManager> getProducer() {
        return producer;
    }

}
