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
package org.apache.james.container.spring.resource;

import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.DefaultUserEntityValidator;
import org.apache.james.RecipientRewriteTableUserEntityValidator;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.user.api.UsersRepository;

public class SpringUserEntityValidator implements UserEntityValidator {
    private UsersRepository usersRepository;
    private RecipientRewriteTable rrt;
    private UserEntityValidator delegate;

    @Inject
    public void setUsersRepository(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
        if (rrt != null) {
            delegate = UserEntityValidator.aggregate(
                new DefaultUserEntityValidator(usersRepository),
                new RecipientRewriteTableUserEntityValidator(rrt));
        }
    }

    @Inject
    public void setRrt(RecipientRewriteTable rrt) {
        this.rrt = rrt;
        if (usersRepository != null) {
            delegate = UserEntityValidator.aggregate(
                new DefaultUserEntityValidator(usersRepository),
                new RecipientRewriteTableUserEntityValidator(rrt));
        }
    }

    @Override
    public Optional<ValidationFailure> canCreate(Username username, Set<EntityType> ignoredTypes) throws Exception {
        return delegate.canCreate(username, ignoredTypes);
    }
}
