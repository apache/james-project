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

package org.apache.james.utils;

import java.util.List;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.mailet.Mail;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class MailRepositoryProbeImpl implements GuiceProbe {

    private final MailRepositoryStore repositoryStore;

    @Inject
    public MailRepositoryProbeImpl(MailRepositoryStore repositoryStore) {
        this.repositoryStore = repositoryStore;
    }

    /**
     * Get the count of email currently stored in a given repository
     */
    public long getRepositoryMailCount(MailRepositoryUrl url) throws Exception {
        return repositoryStore.select(url).size();
    }

    public void createRepository(MailRepositoryUrl url) throws Exception {
        repositoryStore.select(url);
    }

    public List<MailKey> listMailKeys(MailRepositoryUrl url) throws Exception {
        return ImmutableList.copyOf(
            repositoryStore.select(url)
                .list());
    }

    public Stream<Mail> listMails(MailRepositoryUrl url) throws Exception {
        return listMailKeys(url)
            .stream()
            .map(Throwing.function(key -> getMail(url, key)));
    }

    public List<Mail> listMails(MailRepositoryUrl url, MailAddress recipient) throws Exception {
        return listMails(url).filter(Throwing.predicate(mail -> mail.getRecipients().contains(recipient)))
            .collect(ImmutableList.toImmutableList());
    }

    public Mail getMail(MailRepositoryUrl url, MailKey key) throws Exception {
        return repositoryStore.select(url)
                .retrieve(key);
    }

    public List<MailRepositoryUrl> listRepositoryUrls() {
        return repositoryStore.getUrls()
            .collect(ImmutableList.toImmutableList());
    }

    public MailRepositoryStore getMailRepositoryStore() {
        return repositoryStore;
    }
}