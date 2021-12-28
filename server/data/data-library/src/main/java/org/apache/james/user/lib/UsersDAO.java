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

package org.apache.james.user.lib;

import java.util.Iterator;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface UsersDAO {
    default boolean getDefaultVirtualHostingValue() {
        return false;
    }

    Optional<? extends User> getUserByName(Username name) throws UsersRepositoryException;

    void updateUser(User user) throws UsersRepositoryException;

    void removeUser(Username name) throws UsersRepositoryException;

    boolean contains(Username name) throws UsersRepositoryException;

    default Publisher<Boolean> containsReactive(Username name) {
        return Mono.fromCallable(() -> contains(name))
            .subscribeOn(Schedulers.elastic());
    }

    int countUsers() throws UsersRepositoryException;

    Iterator<Username> list() throws UsersRepositoryException;

    void addUser(Username username, String password) throws UsersRepositoryException;
}
