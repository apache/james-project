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

package org.apache.james.mpt.imapmailbox.lucenesearch.host;

import java.io.File;
import java.io.IOException;
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
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPAId.Factory;
import org.apache.james.mailbox.jpa.JPAMailboxSessionMapperFactory;
import org.apache.james.mailbox.jpa.JPASubscriptionManager;
import org.apache.james.mailbox.jpa.mail.JPAModSeqProvider;
import org.apache.james.mailbox.jpa.mail.JPAUidProvider;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.jpa.mail.model.JPAProperty;
import org.apache.james.mailbox.jpa.mail.model.JPAUserFlag;
import org.apache.james.mailbox.jpa.mail.model.openjpa.AbstractJPAMailboxMessage;
import org.apache.james.mailbox.jpa.mail.model.openjpa.JPAMailboxMessage;
import org.apache.james.mailbox.jpa.openjpa.OpenJPAMailboxManager;
import org.apache.james.mailbox.jpa.user.model.JPASubscription;
import org.apache.james.mailbox.lucene.search.LuceneMessageSearchIndex;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;
import org.apache.lucene.store.FSDirectory;
import org.apache.openjpa.persistence.OpenJPAPersistence;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

public class LuceneSearchHostSystem extends JamesImapHostSystem {
    public static final String META_DATA_DIRECTORY = "target/user-meta-data";
    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);

    private File tempFile;
    private OpenJPAMailboxManager mailboxManager;
    private FakeAuthenticator userManager;
    private EntityManagerFactory entityManagerFactory;

    public boolean addUser(String user, String password) throws Exception {
        userManager.addUser(user, password);
        return true;
    }

    @Override
    public void beforeTest() throws Exception {
        this.tempFile = Files.createTempDir();
        initFields();
    }

    @Override
    public void resetData() throws Exception {
        tempFile.deleteOnExit();

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

    private void initFields() {
        HashMap<String, String> properties = new HashMap<String, String>();

        // Configure OpenJPA for H2 Memory Database
        properties.put("openjpa.ConnectionDriverName", org.h2.Driver.class.getName());
        properties.put("openjpa.ConnectionURL", "jdbc:h2:mem:mailboxintegration;DB_CLOSE_DELAY=-1"); // Memory H2 database

        // Configure OpenJPA Tables creation
        properties.put("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)"); // Create Foreign Keys
        properties.put("openjpa.jdbc.MappingDefaults", "ForeignKeyDeleteAction=restrict, JoinForeignKeyDeleteAction=restrict");
        properties.put("openjpa.jdbc.SchemaFactory", "native(ForeignKeys=true)");
        properties.put("openjpa.jdbc.MappingDefaults", "ForeignKeyDeleteAction=cascade, JoinForeignKeyDeleteAction=cascade");


        // Configure OpenJPA Cache
        properties.put("openjpa.jdbc.QuerySQLCache", "false");

        // Configure OpenJPA Log
        properties.put("openjpa.Log", "JDBC=WARN, SQL=WARN, Runtime=WARN");
        //properties.put("openjpa.RuntimeUnenhancedClasses", "warn");

        // Configure OpenJPA Metadata
        properties.put("openjpa.MetaDataFactory", "jpa(Types=" +
            JPAMailbox.class.getName() + ";" +
            AbstractJPAMailboxMessage.class.getName() + ";" +
            JPAMailboxMessage.class.getName() + ";" +
            JPAProperty.class.getName() + ";" +
            JPAUserFlag.class.getName() + ";" +
            JPASubscription.class.getName() + ")");

        userManager = new FakeAuthenticator();
        entityManagerFactory = OpenJPAPersistence.getEntityManagerFactory(properties);
        JVMMailboxPathLocker locker = new JVMMailboxPathLocker();
        JPAUidProvider uidProvider = new JPAUidProvider(locker, entityManagerFactory);
        JPAModSeqProvider modSeqProvider = new JPAModSeqProvider(locker, entityManagerFactory);
        JPAMailboxSessionMapperFactory factory = new JPAMailboxSessionMapperFactory(entityManagerFactory, uidProvider, modSeqProvider);

        try {
            JPAId.Factory mailboxIdFactory = new Factory();
            FSDirectory fsDirectory = FSDirectory.open(tempFile);
            LuceneMessageSearchIndex searchIndex = new LuceneMessageSearchIndex(factory, mailboxIdFactory, fsDirectory);
            searchIndex.setEnableSuffixMatch(true);

            MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
            GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
            MessageParser messageParser = new MessageParser();
            MessageId.Factory messageIdFactory = new DefaultMessageId.Factory();

            mailboxManager = new OpenJPAMailboxManager(factory, userManager, locker, false, aclResolver, groupMembershipResolver, messageParser, messageIdFactory);
            mailboxManager.setMessageSearchIndex(searchIndex);

            mailboxManager.init();

            SubscriptionManager subscriptionManager = new JPASubscriptionManager(factory);

            final ImapProcessor defaultImapProcessorFactory =
                DefaultImapProcessorFactory.createDefaultProcessor(
                    mailboxManager,
                    subscriptionManager,
                    new NoQuotaManager(),
                    new DefaultQuotaRootResolver(factory));

            configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                defaultImapProcessorFactory);

        } catch (IOException e) {
            throw Throwables.propagate(e);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void createMailbox(MailboxPath mailboxPath) throws Exception{
        new MailboxCreationDelegate(mailboxManager).createMailbox(mailboxPath);
    }

    @Override
    public boolean supports(Feature... features) {
        return SUPPORTED_FEATURES.supports(features);
    }

}