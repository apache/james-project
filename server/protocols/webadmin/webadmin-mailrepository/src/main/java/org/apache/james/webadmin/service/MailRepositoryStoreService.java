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

package org.apache.james.webadmin.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.task.Task;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.streams.Iterators;
import org.apache.james.util.streams.Limit;
import org.apache.james.util.streams.Offset;
import org.apache.james.webadmin.dto.InaccessibleFieldException;
import org.apache.james.webadmin.dto.MailDto;
import org.apache.james.webadmin.dto.MailDto.AdditionalField;
import org.apache.james.webadmin.dto.MailKeyDTO;
import org.apache.james.webadmin.dto.SingleMailRepositoryResponse;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.mailet.Mail;
import org.eclipse.jetty.http.HttpStatus;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

public class MailRepositoryStoreService {
    private final MailRepositoryStore mailRepositoryStore;

    @Inject
    public MailRepositoryStoreService(MailRepositoryStore mailRepositoryStore) {
        this.mailRepositoryStore = mailRepositoryStore;
    }

    public Stream<SingleMailRepositoryResponse> listMailRepositories() {
        return mailRepositoryStore
            .getPaths()
            .map(SingleMailRepositoryResponse::new);
    }

    public MailRepository createMailRepository(MailRepositoryPath repositoryPath, String protocol) throws MailRepositoryStore.MailRepositoryStoreException {
        return mailRepositoryStore.create(MailRepositoryUrl.fromPathAndProtocol(repositoryPath, protocol));
    }

    public Optional<List<MailKeyDTO>> listMails(MailRepositoryPath path, Offset offset, Limit limit) throws MailRepositoryStore.MailRepositoryStoreException, MessagingException {
        Optional<Stream<MailKeyDTO>> maybeMails = Optional.of(getRepositories(path)
            .flatMap(Throwing.function((MailRepository repository) -> Iterators.toStream(repository.list())).sneakyThrow())
            .map(MailKeyDTO::new)
            .skip(offset.getOffset()));

        return maybeMails.map(limit::applyOnStream)
            .map(stream -> stream.collect(ImmutableList.toImmutableList()));
    }

    public Optional<Long> size(MailRepositoryPath path) throws MailRepositoryStore.MailRepositoryStoreException {
        return Flux.fromStream(getRepositories(path))
            .flatMap(MailRepository::sizeReactive, ReactorUtils.DEFAULT_CONCURRENCY)
            .reduce(0L, Long::sum)
            .blockOptional();
    }

    public Optional<MailDto> retrieveMail(MailRepositoryPath path, MailKey mailKey, Set<AdditionalField> additionalAttributes) throws MailRepositoryStore.MailRepositoryStoreException, MessagingException, InaccessibleFieldException {
        Optional<Mail> mail = fetchMail(path, mailKey);
        try {
            return mail.map(Throwing.function((Mail aMail) -> MailDto.fromMail(aMail, additionalAttributes)).sneakyThrow());
        } finally {
            LifecycleUtil.dispose(mail);
        }
    }

    public Optional<MimeMessage> retrieveMessage(MailRepositoryPath path, MailKey mailKey) throws MailRepositoryStore.MailRepositoryStoreException, MessagingException {
        Optional<Mail> mail = fetchMail(path, mailKey);
        try {
            return mail.map(Throwing.function(Mail::getMessage).sneakyThrow());
        } finally {
            LifecycleUtil.dispose(mail);
        }
    }

    public void deleteMail(MailRepositoryPath path, MailKey mailKey) throws MailRepositoryStore.MailRepositoryStoreException, MessagingException {
        getRepositories(path)
            .forEach(Throwing.consumer((MailRepository repository) -> repository.remove(mailKey)).sneakyThrow());
    }

    public Task createClearMailRepositoryTask(MailRepositoryPath path) throws MailRepositoryStore.MailRepositoryStoreException, MessagingException {
        getRepositories(path);
        return new ClearMailRepositoryTask(mailRepositoryStore, path);
    }

    public Stream<MailRepository> getRepositories(MailRepositoryPath path) throws MailRepositoryStore.MailRepositoryStoreException {
        Stream<MailRepository> byPath = mailRepositoryStore.getByPath(path);
        List<MailRepository> repositories = byPath.collect(Collectors.toList());
        if (repositories.isEmpty()) {
            ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("%s does not exist", path.asString())
                .haltError();
        }

        return repositories.stream();
    }

    private Optional<Mail> fetchMail(MailRepositoryPath path, MailKey mailKey) throws MailRepositoryStore.MailRepositoryStoreException, MessagingException {
        return getRepositories(path)
            .map(Throwing.function((MailRepository repository) -> Optional.ofNullable(repository.retrieve(mailKey))).sneakyThrow())
            .flatMap(Optional::stream)
            .findFirst();
        }
}
