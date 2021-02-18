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

package org.apache.james.data;

import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;

public class UsersRepositoryModuleChooser {
    public enum Implementation {
        LDAP,
        DEFAULT;

        public static Implementation parse(FileConfigurationProvider configurationProvider) {
            try {
                return Optional.ofNullable(configurationProvider.getConfiguration("usersrepository")
                        .getString("[@ldapHost]", null))
                    .map(anyHost -> Implementation.LDAP)
                    .orElse(Implementation.DEFAULT);
            } catch (ConfigurationException e) {
                LOGGER.warn("Error reading usersrepository.xml, defaulting to default implementation", e);
                return Implementation.DEFAULT;
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(UsersRepositoryModuleChooser.class);

    private final Module defaultUsersRepositoryModule;

    public UsersRepositoryModuleChooser(Module defaultUsersRepositoryModule) {
        this.defaultUsersRepositoryModule = defaultUsersRepositoryModule;
    }

    public List<Module> chooseModules(Implementation implementation) {
        switch (implementation) {
            case LDAP:
                return ImmutableList.of(new LdapUsersRepositoryModule());
            case DEFAULT:
                return ImmutableList.of(defaultUsersRepositoryModule);
            default:
                throw new NotImplementedException(implementation + " is not a supported option");
        }
    }
}
