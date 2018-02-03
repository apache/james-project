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

import java.util.Optional;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.util.streams.Iterators;
import org.apache.mailet.Mail;

import com.github.fge.lambdas.Throwing;

public class ReprocessingService {

    private final MailQueueFactory<?> mailQueueFactory;
    private final MailRepositoryStoreService mailRepositoryStoreService;

    @Inject
    public ReprocessingService(MailQueueFactory<?> mailQueueFactory,
                               MailRepositoryStoreService mailRepositoryStoreService) {
        this.mailQueueFactory = mailQueueFactory;
        this.mailRepositoryStoreService = mailRepositoryStoreService;
    }

    public void reprocessAll(String url, Optional<String> targetProcessor, String targetQueue, Consumer<String> keyListener) throws MailRepositoryStore.MailRepositoryStoreException, MessagingException {
        MailRepository repository = mailRepositoryStoreService.getRepository(url);
        MailQueue mailQueue = getMailQueue(targetQueue);

        Iterators.toStream(repository.list())
            .peek(keyListener)
            .forEach(Throwing.consumer(key -> reprocess(repository, mailQueue, key, targetProcessor)));
    }

    public void reprocess(String url, String key, Optional<String> targetProcessor, String targetQueue) throws MailRepositoryStore.MailRepositoryStoreException, MessagingException {
        MailRepository repository = mailRepositoryStoreService.getRepository(url);
        MailQueue mailQueue = getMailQueue(targetQueue);

        reprocess(repository, mailQueue, key, targetProcessor);
    }

    private void reprocess(MailRepository repository, MailQueue mailQueue, String key, Optional<String> targetProcessor) throws MessagingException {
        Mail mail = repository.retrieve(key);
        targetProcessor.ifPresent(mail::setState);
        mailQueue.enQueue(mail);
        repository.remove(key);
    }

    private MailQueue getMailQueue(String targetQueue) {
        return mailQueueFactory.getQueue(targetQueue)
            .orElseThrow(() -> new RuntimeException("Can not find queue " + targetQueue));
    }
}
