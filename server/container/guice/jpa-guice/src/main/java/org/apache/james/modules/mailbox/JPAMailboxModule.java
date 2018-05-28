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
package org.apache.james.modules.mailbox;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Singleton;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.JPAConfiguration;
import org.apache.james.adapter.mailbox.store.UserRepositoryAuthenticator;
import org.apache.james.adapter.mailbox.store.UserRepositoryAuthorizator;
import org.apache.james.backends.jpa.JPAConstants;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPAMailboxSessionMapperFactory;
import org.apache.james.mailbox.jpa.JPASubscriptionManager;
import org.apache.james.mailbox.jpa.mail.JPAModSeqProvider;
import org.apache.james.mailbox.jpa.mail.JPAUidProvider;
import org.apache.james.mailbox.jpa.openjpa.OpenJPAMailboxManager;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.modules.Names;
import org.apache.james.utils.MailboxManagerDefinition;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.base.Joiner;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;

public class JPAMailboxModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new JpaQuotaModule());
        install(new JPAQuotaSearchModule());

        bind(JPAMailboxSessionMapperFactory.class).in(Scopes.SINGLETON);
        bind(OpenJPAMailboxManager.class).in(Scopes.SINGLETON);
        bind(JVMMailboxPathLocker.class).in(Scopes.SINGLETON);
        bind(JPASubscriptionManager.class).in(Scopes.SINGLETON);
        bind(JPAModSeqProvider.class).in(Scopes.SINGLETON);
        bind(JPAUidProvider.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthenticator.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthorizator.class).in(Scopes.SINGLETON);
        bind(JPAId.Factory.class).in(Scopes.SINGLETON);
        bind(SimpleGroupMembershipResolver.class).in(Scopes.SINGLETON);
        bind(UnionMailboxACLResolver.class).in(Scopes.SINGLETON);
        bind(DefaultMessageId.Factory.class).in(Scopes.SINGLETON);

        bind(MessageMapperFactory.class).to(JPAMailboxSessionMapperFactory.class);
        bind(MailboxMapperFactory.class).to(JPAMailboxSessionMapperFactory.class);
        bind(MailboxSessionMapperFactory.class).to(JPAMailboxSessionMapperFactory.class);
        bind(MessageId.Factory.class).to(DefaultMessageId.Factory.class);

        bind(ModSeqProvider.class).to(JPAModSeqProvider.class);
        bind(UidProvider.class).to(JPAUidProvider.class);
        bind(SubscriptionManager.class).to(JPASubscriptionManager.class);
        bind(MailboxPathLocker.class).to(JVMMailboxPathLocker.class);
        bind(Authenticator.class).to(UserRepositoryAuthenticator.class);
        bind(MailboxManager.class).to(OpenJPAMailboxManager.class);
        bind(Authorizator.class).to(UserRepositoryAuthorizator.class);
        bind(MailboxId.Factory.class).to(JPAId.Factory.class);
        bind(GroupMembershipResolver.class).to(SimpleGroupMembershipResolver.class);
        bind(MailboxACLResolver.class).to(UnionMailboxACLResolver.class);
        
        Multibinder.newSetBinder(binder(), MailboxManagerDefinition.class).addBinding().to(JPAMailboxManagerDefinition.class);
    }

    @Provides
    @Named(Names.MAILBOXMANAGER_NAME)
    @Singleton
    public MailboxManager provideMailboxManager(OpenJPAMailboxManager jpaMailboxManager, ListeningCurrentQuotaUpdater quotaUpdater,
                                                QuotaManager quotaManager, QuotaRootResolver quotaRootResolver) throws MailboxException {
        jpaMailboxManager.setQuotaRootResolver(quotaRootResolver);
        jpaMailboxManager.setQuotaManager(quotaManager);
        jpaMailboxManager.setQuotaUpdater(quotaUpdater);
        jpaMailboxManager.init();
        return jpaMailboxManager;
    }
    
    @Singleton
    private static class JPAMailboxManagerDefinition extends MailboxManagerDefinition {
        @Inject
        private JPAMailboxManagerDefinition(OpenJPAMailboxManager manager) {
            super("jpa-mailboxmanager", manager);
        }
    }
    
    @Provides
    @Singleton
    public EntityManagerFactory provideEntityManagerFactory(JPAConfiguration jpaConfiguration) {
        HashMap<String, String> properties = new HashMap<>();
        
        properties.put("openjpa.ConnectionDriverName", jpaConfiguration.getDriverName());
        properties.put("openjpa.ConnectionURL", jpaConfiguration.getDriverURL());

        List<String> connectionFactoryProperties = new ArrayList<>();
        connectionFactoryProperties.add("TestOnBorrow=" + jpaConfiguration.isTestOnBorrow());
        if (jpaConfiguration.getValidationQueryTimeoutSec() > 0) {
            connectionFactoryProperties.add("ValidationTimeout=" + jpaConfiguration.getValidationQueryTimeoutSec() * 1000);
        }
        if (jpaConfiguration.getValidationQuery() != null) {
            connectionFactoryProperties.add("ValidationSQL='" + jpaConfiguration.getValidationQuery() + "'");
        }
        properties.put("openjpa.ConnectionFactoryProperties", Joiner.on(", ").join(connectionFactoryProperties));

        return Persistence.createEntityManagerFactory("Global", properties);
    }

    @Provides
    @Singleton
    JPAConfiguration provideConfiguration(PropertiesProvider propertiesProvider) throws FileNotFoundException, ConfigurationException {
        PropertiesConfiguration dataSource = propertiesProvider.getConfiguration("james-database");
        return JPAConfiguration.builder()
                .driverName(dataSource.getString("database.driverClassName"))
                .driverURL(dataSource.getString("database.url"))
                .testOnBorrow(dataSource.getBoolean("datasource.testOnBorrow", false))
                .validationQueryTimeoutSec(dataSource.getInt("datasource.validationQueryTimeoutSec", JPAConstants.VALIDATION_NO_TIMEOUT))
                .validationQuery(dataSource.getString("datasource.validationQuery", null))
                .build();
    }
}