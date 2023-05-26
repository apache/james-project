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

package org.apache.james;

import java.util.Set;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.rrt.ForwardUsernameChangeTaskStep;
import org.apache.james.rrt.RecipientRewriteTableUserDeletionTaskStep;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.user.api.DelegationUserDeletionTaskStep;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.UsernameChangeTaskStep;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class CoreDataModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), UserEntityValidator.class).addBinding().to(DefaultUserEntityValidator.class);
        Multibinder.newSetBinder(binder(), UserEntityValidator.class).addBinding().to(RecipientRewriteTableUserEntityValidator.class);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class).addBinding().to(ForwardUsernameChangeTaskStep.class);

        Multibinder<DeleteUserDataTaskStep> deleteUserDataTaskSteps = Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class);
        deleteUserDataTaskSteps.addBinding().to(RecipientRewriteTableUserDeletionTaskStep.class);
        deleteUserDataTaskSteps.addBinding().to(DelegationUserDeletionTaskStep.class);
    }

    @Provides
    @Singleton
    UserEntityValidator userEntityValidator(Set<UserEntityValidator> validatorSet) {
        return new AggregateUserEntityValidator(validatorSet);
    }

    @Provides
    @Singleton
    public DomainListConfiguration provideDomainListConfiguration(ConfigurationProvider configurationProvider) throws ConfigurationException {
        return DomainListConfiguration.from(configurationProvider.getConfiguration("domainlist"));
    }
}
