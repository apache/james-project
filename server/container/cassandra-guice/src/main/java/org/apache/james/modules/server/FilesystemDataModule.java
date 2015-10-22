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


package org.apache.james.modules.server;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.xml.XMLDomainList;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.file.XMLRecipientRewriteTable;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.file.UsersFileRepository;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.ConfigurationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class FilesystemDataModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilesystemDataModule.class);

    @Override
    protected void configure() {
        bind(DomainList.class).to(XMLDomainList.class);
        bind(UsersRepository.class).to(UsersFileRepository.class);
        bind(RecipientRewriteTable.class).to(XMLRecipientRewriteTable.class);
        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(CassandraDataConfigurationPerformer.class);
    }

    @Singleton
    public static class CassandraDataConfigurationPerformer implements ConfigurationPerformer {

        private final ConfigurationProvider configurationProvider;
        private final XMLDomainList fileDomainList;
        private final UsersFileRepository fileUsersRepository;
        private final XMLRecipientRewriteTable fileRecipientRewriteTable;

        @Inject
        public CassandraDataConfigurationPerformer(ConfigurationProvider configurationProvider,
                                                   XMLDomainList fileDomainList,
                                                   UsersFileRepository fileUsersRepository,
                                                   XMLRecipientRewriteTable fileRecipientRewriteTable) {
            this.configurationProvider = configurationProvider;
            this.fileDomainList = fileDomainList;
            this.fileUsersRepository = fileUsersRepository;
            this.fileRecipientRewriteTable = fileRecipientRewriteTable;
        }

        public void initModule() throws Exception {
            fileRecipientRewriteTable.setLog(LOGGER);
            fileRecipientRewriteTable.configure(configurationProvider.getConfiguration("recipientrewritetable"));
            fileDomainList.setLog(LOGGER);
            fileDomainList.configure(configurationProvider.getConfiguration("domainlist"));
            fileUsersRepository.setLog(LOGGER);
            fileUsersRepository.configure(configurationProvider.getConfiguration("usersrepository"));
            fileUsersRepository.init();
        }
    }

}
