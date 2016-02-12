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

package org.apache.james.mpt.smtp.host;

import java.util.Iterator;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mpt.api.SmtpHostSystem;
import org.apache.james.mpt.monitor.SystemLoggingMonitor;
import org.apache.james.mpt.session.ExternalSessionFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.utils.ConfigurationsPerformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

public class JamesSmtpHostSystem extends ExternalSessionFactory implements SmtpHostSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(JamesSmtpHostSystem.class);

    private final DomainList domainList;
    private final UsersRepository usersRepository;
    private final ConfigurationsPerformer configurationsPerformer;

    public JamesSmtpHostSystem(ConfigurationsPerformer configurationsPerformer, DomainList domainList, UsersRepository usersRepository) {
        super("localhost", 1025, new SystemLoggingMonitor(), "220 mydomain.tld smtp");
        this.configurationsPerformer = configurationsPerformer;
        this.domainList = domainList;
        this.usersRepository = usersRepository;
    }

    @Override
    public boolean addUser(String userAtDomain, String password) throws Exception {
        Preconditions.checkArgument(userAtDomain.contains("@"), "The 'user' should contain the 'domain'");
        Iterator<String> split = Splitter.on("@").split(userAtDomain).iterator();
        split.next();
        String domain = split.next();

        domainList.addDomain(domain);
        usersRepository.addUser(userAtDomain, password);
        return true;
    }

    @Override
    public void beforeTests() throws Exception {
    }

    @Override
    public void afterTests() throws Exception {
    }

    @Override
    public void beforeTest() throws Exception {
        LOGGER.info("Initializing modules");
        configurationsPerformer.initModules();
    }

    @Override
    public void afterTest() throws Exception {
    }
}
