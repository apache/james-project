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

package org.apache.james.mailrepository.postgres;

import java.util.Collection;
import java.util.Iterator;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.mailet.Mail;

import reactor.core.publisher.Mono;

public class PostgresMailRepository implements MailRepository {
    private final MailRepositoryUrl url;
    private final PostgresMailRepositoryContentDAO postgresMailRepositoryContentDAO;

    @Inject
    public PostgresMailRepository(MailRepositoryUrl url,
                                  PostgresMailRepositoryContentDAO postgresMailRepositoryContentDAO) {
        this.url = url;
        this.postgresMailRepositoryContentDAO = postgresMailRepositoryContentDAO;
    }

    @Override
    public long size() throws MessagingException {
        return postgresMailRepositoryContentDAO.size(url);
    }

    @Override
    public Mono<Long> sizeReactive() {
        return postgresMailRepositoryContentDAO.sizeReactive(url);
    }

    @Override
    public MailKey store(Mail mail) throws MessagingException {
        return postgresMailRepositoryContentDAO.store(mail, url);
    }

    @Override
    public Iterator<MailKey> list() throws MessagingException {
        return postgresMailRepositoryContentDAO.list(url);
    }

    @Override
    public Mail retrieve(MailKey key) {
        return postgresMailRepositoryContentDAO.retrieve(key, url);
    }

    @Override
    public void remove(MailKey key) {
        postgresMailRepositoryContentDAO.remove(key, url);
    }

    @Override
    public void remove(Collection<MailKey> keys) {
        postgresMailRepositoryContentDAO.remove(keys, url);
    }

    @Override
    public void removeAll() {
        postgresMailRepositoryContentDAO.removeAll(url);
    }
}
