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

package org.apache.james.mpt.imapmailbox.jpa.host;

import java.io.File;
import java.util.HashMap;

import javax.persistence.EntityManagerFactory;

import org.apache.commons.io.FileUtils;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPAMailboxSessionMapperFactory;
import org.apache.james.mailbox.jpa.JPASubscriptionManager;
import org.apache.james.mailbox.jpa.mail.JPAModSeqProvider;
import org.apache.james.mailbox.jpa.mail.JPAUidProvider;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.jpa.mail.model.JPAProperty;
import org.apache.james.mailbox.jpa.mail.model.JPAUserFlag;
import org.apache.james.mailbox.jpa.mail.model.openjpa.AbstractJPAMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAMessage;
import org.apache.james.mailbox.jpa.openjpa.OpenJPAMailboxManager;
import org.apache.james.mailbox.jpa.user.model.JPASubscription;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;
import org.apache.openjpa.persistence.OpenJPAPersistence;
import org.slf4j.LoggerFactory;

public class JPAHostSystem extends JamesImapHostSystem {

    public static final String META_DATA_DIRECTORY = "target/user-meta-data";

    public static JamesImapHostSystem build() throws Exception {
        JPAHostSystem host =  new JPAHostSystem();
        return host;
    }
    
    private final OpenJPAMailboxManager mailboxManager;
    private final MockAuthenticator userManager; 
    private final EntityManagerFactory entityManagerFactory;

    public JPAHostSystem() throws Exception {

        HashMap<String, String> properties = new HashMap<String, String>();
        
        // Configure OpenJPA for H2 Memory Database
        properties.put("openjpa.ConnectionDriverName", org.h2.Driver.class.getName());
        properties.put("openjpa.ConnectionURL", "jdbc:h2:mem:mailboxintegration;DB_CLOSE_DELAY=-1"); // Memory H2 database
        
        // Configure OpenJPA for Derby Memory Database
        // properties.put("openjpa.ConnectionDriverName", org.apache.derby.jdbc.EmbeddedDriver.class.getName());
        // properties.put("openjpa.ConnectionURL", "jdbc:derby:memory:mailboxintegration;create=true"); // Memory Derby database
        
        // Configure OpenJPA for Derby Embedded Database
        //properties.put("openjpa.ConnectionURL", "jdbc:derby:test;create=true");
        //properties.put("openjpa.ConnectionFactoryProperties", "PrettyPrint=true, PrettyPrintLineLength=72");

        // Configure OpenJPA Tables creation
        properties.put("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)"); // Create Foreign Keys
        properties.put("openjpa.jdbc.MappingDefaults", "ForeignKeyDeleteAction=restrict, JoinForeignKeyDeleteAction=restrict");
        properties.put("openjpa.jdbc.SchemaFactory", "native(ForeignKeys=true)");
        properties.put("openjpa.jdbc.MappingDefaults", "ForeignKeyDeleteAction=cascade, JoinForeignKeyDeleteAction=cascade");


        // Configure OpenJPA Cache
        properties.put("openjpa.jdbc.QuerySQLCache", "false");
        
        // Configure OpenJPA Log
        properties.put("openjpa.Log", "JDBC=WARN, SQL=WARN, Runtime=WARN");
        //properties.put("openjpa.Log", "SQL=TRACE");          // Use SQL=TRACE to trace SQL.
        //properties.put("openjpa.Log", "DefaultLevel=TRACE"); // Use the DefaultLevel=TRACE to trace all.

        // Configure OpenJPA Metadata
        properties.put("openjpa.MetaDataFactory", "jpa(Types=" +
                JPAMailbox.class.getName() + ";" +
                AbstractJPAMessage.class.getName() + ";" +
                JPAMessage.class.getName() + ";" +
                JPAProperty.class.getName() + ";" +
                JPAUserFlag.class.getName() + ";" + 
                JPASubscription.class.getName() + ")");
        
        userManager = new MockAuthenticator();
        entityManagerFactory = OpenJPAPersistence.getEntityManagerFactory(properties);
        JVMMailboxPathLocker locker = new JVMMailboxPathLocker();
        JPAUidProvider uidProvider = new JPAUidProvider(locker, entityManagerFactory);
        JPAModSeqProvider modSeqProvider = new JPAModSeqProvider(locker, entityManagerFactory);
        JPAMailboxSessionMapperFactory mf = new JPAMailboxSessionMapperFactory(entityManagerFactory, uidProvider, modSeqProvider);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();

        mailboxManager = new OpenJPAMailboxManager(mf, userManager, locker, false, aclResolver, groupMembershipResolver);
        mailboxManager.init();

        SubscriptionManager subscriptionManager = new JPASubscriptionManager(mf);
        
        final ImapProcessor defaultImapProcessorFactory = DefaultImapProcessorFactory.createDefaultProcessor(mailboxManager, subscriptionManager, new NoQuotaManager(), new DefaultQuotaRootResolver(mf));
        
        resetUserMetaData();
        
        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                defaultImapProcessorFactory);

    }

    public boolean addUser(String user, String password) {
        userManager.addUser(user, password);
        return true;
    }

    public void resetData() throws Exception {
        resetUserMetaData();
        MailboxSession session = mailboxManager.createSystemSession("test", LoggerFactory.getLogger("TestLog"));
        mailboxManager.startProcessingRequest(session);
        mailboxManager.deleteEverything(session);
        mailboxManager.endProcessingRequest(session);
        mailboxManager.logout(session, false);
        
    }
    
    public void resetUserMetaData() throws Exception {
        File dir = new File(META_DATA_DIRECTORY);
        if (dir.exists()) {
            FileUtils.deleteDirectory(dir);
        }
        dir.mkdirs();
    }

    @Override
    public void createMailbox(MailboxPath mailboxPath) throws Exception {
        new MailboxCreationDelegate(mailboxManager).createMailbox(mailboxPath);
    }

}
