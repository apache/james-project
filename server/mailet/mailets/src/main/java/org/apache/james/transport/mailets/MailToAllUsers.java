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

package org.apache.james.transport.mailets;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A mailet that helps to email all users in the system. The emails are sent in batches to manage
 * the load. The first batch is sent directly, while the remaining batches are sent asynchronously.
 * The batch size can be configured via the <b>batchSize</b> parameter (optional, defaults to 100).
 *
 * <h3>Configuration</h3>
 * <pre><code>
 * {@code
 * <matcher name="notify-matcher" match="org.apache.james.mailetcontainer.impl.matchers.And">
 *     <matcher match="SenderIs=admin@gov.org"/>
 *     <matcher match="RecipientIs=all@gov.org"/>
 * </matcher>
 * <mailet match="notify-matcher" class="MailToAllUsers">
 *     <batchSize>100</batchSize>
 * </mailet>
 * }
 * </code></pre>
 *
 */
public class MailToAllUsers extends GenericMailet {
    private static final int DEFAULT_BATCH_SIZE = 100;
    private final UsersRepository usersRepository;
    private int batchSize;

    @Inject
    public MailToAllUsers(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public void init() throws MessagingException {
        batchSize = Integer.parseInt(Optional.ofNullable(getInitParameter("batchSize"))
            .orElse(String.valueOf(DEFAULT_BATCH_SIZE)));
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        Flux<Flux<MailAddress>> recipientsBatches = Flux.from(usersRepository.listReactive())
            .map(Throwing.function(Username::asMailAddress))
            .window(batchSize);

        Flux.merge(sendMailToFirstRecipientsBatchDirectly(mail, recipientsBatches),
                sendMailToRemainingRecipientsBatchAsynchronously(mail, recipientsBatches))
            .then()
            .block();
    }

    private Mono<Void> sendMailToFirstRecipientsBatchDirectly(Mail mail, Flux<Flux<MailAddress>> recipientsBatches) {
        return recipientsBatches
            .take(1)
            .flatMap(firstRecipientsBatch -> firstRecipientsBatch
                .collectList()
                .flatMap(recipients -> Mono.fromRunnable(() -> mail.setRecipients(recipients))))
            .then();
    }

    private Mono<Void> sendMailToRemainingRecipientsBatchAsynchronously(Mail mail, Flux<Flux<MailAddress>> recipientsBatches) {
        return recipientsBatches
            .skip(1)
            .flatMap(remainingRecipientsBatch -> remainingRecipientsBatch
                .collectList()
                .flatMap(recipients -> Mono.fromRunnable(Throwing.runnable(() -> {
                    Mail duplicateMail = mail.duplicate();
                    try {
                        duplicateMail.setRecipients(recipients);
                        getMailetContext().sendMail(duplicateMail);
                    } finally {
                        LifecycleUtil.dispose(duplicateMail);
                    }
                }))))
            .then();
    }

    @Override
    public String getMailetName() {
        return "MailToAllUsers";
    }
}
