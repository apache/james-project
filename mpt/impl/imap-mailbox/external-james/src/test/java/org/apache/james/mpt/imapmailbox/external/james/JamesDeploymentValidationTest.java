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

package org.apache.james.mpt.imapmailbox.external.james;

import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.SmtpHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfigurationEnvironnementVariables;
import org.apache.james.mpt.imapmailbox.external.james.host.external.NoopDomainsAndUserAdder;
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import com.google.inject.Guice;
import com.google.inject.Injector;

class JamesDeploymentValidationTest extends DeploymentValidation {
    private ImapHostSystem system;
    private SmtpHostSystem smtpHostSystem;
    private final ExternalJamesConfiguration configuration = new ExternalJamesConfigurationEnvironnementVariables();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        Injector injector = Guice.createInjector(new ExternalJamesModule(configuration, new NoopDomainsAndUserAdder()));
        system = injector.getInstance(ImapHostSystem.class);
        smtpHostSystem = injector.getInstance(SmtpHostSystem.class);
        system.beforeTest();
        super.setUp();
    }

    @Override
    protected ImapHostSystem createImapHostSystem() {
        return system;
    }

    @Override
    protected SmtpHostSystem createSmtpHostSystem() {
        return smtpHostSystem;
    }

    @Override
    protected ExternalJamesConfiguration getConfiguration() {
        return configuration;
    }

    @AfterEach
    public void tearDown() throws Exception {
        system.afterTest();
    }
}
