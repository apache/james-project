/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.mailbox.store.mail;

import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ThreadIdGuessingAlgorithm {
    Mono<ThreadId> guessThreadIdReactive(MessageId messageId, Optional<MimeMessageId> thisMimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references, Optional<Subject> subject, MailboxSession session);

    Flux<MessageId> getMessageIdsInThread(ThreadId threadId, MailboxSession session);
}
