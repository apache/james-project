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

import org.apache.james.core.Username;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.ProvisioningAPI;
import org.apache.james.mpt.imapmailbox.external.james.host.SmtpHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.Guice;
import com.google.inject.Injector;

@Disabled("Not to be run on CI, as it will not use the current build")
class DockerDeploymentValidationSpringJPATest extends DeploymentValidation {

    private ImapHostSystem system;
    private SmtpHostSystem smtpHostSystem;

    @RegisterExtension
    public DockerJamesRule dockerJamesRule = new DockerJamesRule("linagora/james-jpa-spring");

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        ProvisioningAPI provisioningAPI = dockerJamesRule.cliShellDomainsAndUsersAdder();
        Injector injector = Guice.createInjector(new ExternalJamesModule(getConfiguration(), provisioningAPI));
        system = injector.getInstance(ImapHostSystem.class);
        provisioningAPI.addDomain(DOMAIN);
        provisioningAPI.addUser(Username.of(USER_ADDRESS), PASSWORD);
        smtpHostSystem = injector.getInstance(SmtpHostSystem.class);
        system.beforeTest();

        super.setUp();
    }

    @Test
    @Disabled("Not to be run on CI, as it will not use the current build. Uncomment to test on local dev environment")
    @Override
    public void validateDeployment() throws Exception {
    }

    @Test
    @Disabled("Not to be run on CI, as it will not use the current build. Uncomment to test on local dev environment")
    @Override
    public void validateDeploymentWithMailsFromSmtp() throws Exception {
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
        return dockerJamesRule.getConfiguration();
    }

    @AfterEach
    public void tearDown() throws Exception {
        system.afterTest();
    }
}
