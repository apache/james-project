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
import java.util.function.Function;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * A mailet to split email with too much recipients. The first batch is sent directly, while the remaining batches are sent asynchronously.
 * The batch size can be configured via the <b>batchSize</b> parameter (optional, defaults to 100).
 *
 * <h3>Configuration</h3>
 * <pre><code>
 * {@code
 * <matcher name="notify-matcher" match="org.apache.james.mailetcontainer.impl.matchers.And">
 *     <matcher match="SenderIs=admin@gov.org"/>
 *     <matcher match="RecipientIs=all@gov.org"/>
 * </matcher>
 * <mailet match="notify-matcher" class="SplitMail">
 *     <batchSize>100</batchSize>
 * </mailet>
 * }
 * </code></pre>
 *
 */
public class SplitMail extends GenericMailet {
    private static final int DEFAULT_BATCH_SIZE = 100;
    private int batchSize;

    @Override
    public void init() throws MessagingException {
        batchSize = Integer.parseInt(Optional.ofNullable(getInitParameter("batchSize"))
            .orElse(String.valueOf(DEFAULT_BATCH_SIZE)));
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        if (mail.getRecipients().stream().count() > batchSize) {
            Flux.fromIterable(mail.getRecipients())
                .window(batchSize)
                .index()
                .flatMap(sendMail(mail))
                .then()
                .block();
        }
    }

    private Function<Tuple2<Long, Flux<MailAddress>>, Publisher<Void>> sendMail(Mail mail) {
        return tuple -> {
            boolean firstBatch = tuple.getT1() == 0;
            if (firstBatch) {
                return sendMailToFirstRecipientsBatchDirectly(mail, tuple.getT2());
            }
            return sendMailToRemainingRecipientsBatchAsynchronously(mail, tuple.getT2());
        };
    }

    private Mono<Void> sendMailToFirstRecipientsBatchDirectly(Mail mail, Flux<MailAddress> firstRecipientsBatch) {
        return firstRecipientsBatch
            .collectList()
            .flatMap(recipients -> Mono.fromRunnable(() -> mail.setRecipients(recipients)))
            .then();
    }

    private Mono<Void> sendMailToRemainingRecipientsBatchAsynchronously(Mail mail, Flux<MailAddress> remainingRecipientsBatch) {
        return remainingRecipientsBatch
            .collectList()
            .flatMap(recipients -> Mono.fromRunnable(Throwing.runnable(() -> {
                Mail duplicateMail = mail.duplicate();
                try {
                    duplicateMail.setRecipients(recipients);
                    getMailetContext().sendMail(duplicateMail);
                } finally {
                    LifecycleUtil.dispose(duplicateMail);
                }
            })))
            .then();
    }

    @Override
    public String getMailetName() {
        return "SplitMail";
    }
}
