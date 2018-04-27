/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james;

import java.io.IOException;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.plist.PropertyListConfiguration;
import org.apache.james.http.jetty.ConfigurationException;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class CassandraLdapJmapTestRule implements TestRule {
    private static final int LIMIT_TO_3_MESSAGES = 3;

    public static CassandraLdapJmapTestRule defaultTestRule() {
        return new CassandraLdapJmapTestRule(
            AggregateGuiceModuleTestRule.of(new EmbeddedElasticSearchRule(), new DockerCassandraRule()));
    }

    private final TemporaryFolder temporaryFolder;
    private final GuiceModuleTestRule guiceModuleTestRule;

    public CassandraLdapJmapTestRule(GuiceModuleTestRule... guiceModuleTestRule) {
        TempFilesystemTestRule tempFilesystemTestRule = new TempFilesystemTestRule();
        temporaryFolder = tempFilesystemTestRule.getTemporaryFolder();
        this.guiceModuleTestRule =
            AggregateGuiceModuleTestRule
                .of(guiceModuleTestRule)
                .aggregate(tempFilesystemTestRule);
    }

    public GuiceJamesServer jmapServer(String ldapIp, Module... additionals) throws IOException {
        Configuration configuration = Configuration.builder()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .build();

        return new GuiceJamesServer(configuration)
            .combineWith(CassandraLdapJamesServerMain.cassandraLdapServerModule,
                binder -> binder.bind(String.class).annotatedWith(Names.named("ldapIp")).toInstance(ldapIp))
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_3_MESSAGES))
            .overrideWith(guiceModuleTestRule.getModule())
            .overrideWith(additionals)
            .overrideWith(binder -> binder.bind(ConfigurationProvider.class).to(UnionConfigurationProvider.class));
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return guiceModuleTestRule.apply(base, description);
    }

    public void await() {
        guiceModuleTestRule.await();
    }

    private static class UnionConfigurationProvider implements ConfigurationProvider {
        private final FileConfigurationProvider fileConfigurationProvider;
        private final String ldapIp;

        @Inject
        public UnionConfigurationProvider(FileConfigurationProvider fileConfigurationProvider,
            @Named("ldapIp") String ldapIp) {
            this.fileConfigurationProvider = fileConfigurationProvider;
            this.ldapIp = ldapIp;
        }

        @Override
        public HierarchicalConfiguration getConfiguration(String component) throws org.apache.commons.configuration.ConfigurationException {
            if (component.equals("usersrepository")) {
                return ldapRepositoryConfiguration();
            }
            return fileConfigurationProvider.getConfiguration(component);
        }

        private HierarchicalConfiguration ldapRepositoryConfiguration() throws ConfigurationException {
            PropertyListConfiguration configuration = new PropertyListConfiguration();
            configuration.addProperty("[@ldapHost]", ldapIp);
            configuration.addProperty("[@principal]", "cn=admin\\,dc=james\\,dc=org");
            configuration.addProperty("[@credentials]", "mysecretpassword");
            configuration.addProperty("[@userBase]", "ou=People\\,dc=james\\,dc=org");
            configuration.addProperty("[@userIdAttribute]", "uid");
            configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
            configuration.addProperty("[@maxRetries]", "4");
            configuration.addProperty("[@retryStartInterval]", "0");
            configuration.addProperty("[@retryMaxInterval]", "8");
            configuration.addProperty("[@retryIntervalScale]", "1000");
            return configuration;
        }
    }
}
