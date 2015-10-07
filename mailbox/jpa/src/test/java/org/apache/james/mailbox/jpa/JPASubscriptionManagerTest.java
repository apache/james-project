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

import org.apache.james.mailbox.AbstractSubscriptionManagerTest;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.jpa.mail.JPAModSeqProvider;
import org.apache.james.mailbox.jpa.mail.JPAUidProvider;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.jpa.mail.model.JPAProperty;
import org.apache.james.mailbox.jpa.mail.model.JPAUserFlag;
import org.apache.james.mailbox.jpa.mail.model.openjpa.AbstractJPAMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAMessage;
import org.apache.james.mailbox.jpa.user.model.JPASubscription;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.openjpa.persistence.OpenJPAEntityManagerFactory;
import org.apache.openjpa.persistence.OpenJPAPersistence;
import org.junit.After;
import org.junit.Before;

public class JPASubscriptionManagerTest extends AbstractSubscriptionManagerTest{

    private OpenJPAEntityManagerFactory entityManagerFactory;

    @Before
    public void setup() {

        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("openjpa.ConnectionDriverName", "org.h2.Driver");
        properties.put("openjpa.ConnectionURL", "jdbc:h2:mem:imap;DB_CLOSE_DELAY=-1");
        properties.put("openjpa.Log", "JDBC=WARN, SQL=WARN, Runtime=WARN");
        properties.put("openjpa.ConnectionFactoryProperties", "PrettyPrint=true, PrettyPrintLineLength=72");
        properties.put("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)");
        properties.put("openjpa.MetaDataFactory", "jpa(Types=" +
                JPAMailbox.class.getName() + ";" +
                AbstractJPAMessage.class.getName() + ";" +
                JPAMessage.class.getName() + ";" +
                JPAProperty.class.getName() + ";" +
                JPAUserFlag.class.getName() + ";" +
                JPASubscription.class.getName() + ")");
       
        entityManagerFactory = OpenJPAPersistence.getEntityManagerFactory(properties);

        super.setup();
    }
    
    @Override
    public SubscriptionManager createSubscriptionManager() {
        JVMMailboxPathLocker locker = new JVMMailboxPathLocker();

        JPAMailboxSessionMapperFactory mf = new JPAMailboxSessionMapperFactory(entityManagerFactory, new JPAUidProvider(locker, entityManagerFactory), new JPAModSeqProvider(locker, entityManagerFactory));

        JPASubscriptionManager sm = new JPASubscriptionManager(mf);
        
        return sm;
    }

    @After
    public void teardown() throws SubscriptionException {
        super.teardown();
        entityManagerFactory.close();
    }
}
