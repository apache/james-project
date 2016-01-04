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

package org.apache.james.jmap.methods;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.model.GetMessagesRequest;
import org.apache.james.jmap.model.GetMessagesResponse;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.javatuples.Pair;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class GetMessagesMethod<Id extends MailboxId> implements Method {

    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMessages");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messages");
    private final MessageMapperFactory<Id> messageMapperFactory;
    private final MailboxMapperFactory<Id> mailboxMapperFactory;
    

    @Inject
    @VisibleForTesting GetMessagesMethod(
            MessageMapperFactory<Id> messageMapperFactory, 
            MailboxMapperFactory<Id> mailboxMapperFactory) {
        this.messageMapperFactory = messageMapperFactory;
        this.mailboxMapperFactory = mailboxMapperFactory;
    }
    
    @Override
    public Method.Request.Name requestHandled() {
        return METHOD_NAME;
    }
    
    @Override
    public Method.Response.Name responseName() {
        return RESPONSE_NAME;
    }
    
    @Override
    public Class<? extends JmapRequest> requestType() {
        return GetMessagesRequest.class;
    }
    
    @Override
    public GetMessagesResponse process(JmapRequest request, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkArgument(request instanceof GetMessagesRequest);
        GetMessagesRequest getMessagesRequest = (GetMessagesRequest) request;
        getMessagesRequest.getAccountId().ifPresent(GetMessagesMethod::notImplemented);
        
        Function<MessageId, Stream<Pair<org.apache.james.mailbox.store.mail.model.Message<Id>, MailboxPath>>> loadMessages = loadMessage(mailboxSession);
        Function<Pair<org.apache.james.mailbox.store.mail.model.Message<Id>, MailboxPath>, Message> convertToJmapMessage = toJmapMessage(mailboxSession);
        
        List<Message> result = getMessagesRequest.getIds().stream()
            .flatMap(loadMessages)
            .map(convertToJmapMessage)
            .collect(Collectors.toList());

        return GetMessagesResponse.builder().messages(result).expectedMessageIds(getMessagesRequest.getIds()).build();
    }

    private static void notImplemented(String input) {
        throw new NotImplementedException();
    }

    
    private Function<Pair<org.apache.james.mailbox.store.mail.model.Message<Id>, MailboxPath>, Message> toJmapMessage(MailboxSession mailboxSession) {
        return (value) -> {
            org.apache.james.mailbox.store.mail.model.Message<Id> messageResult = value.getValue0();
            MailboxPath mailboxPath = value.getValue1();
            return Message.fromMailboxMessage(messageResult, uid -> new MessageId(mailboxSession.getUser(), mailboxPath , uid));
        };
    }

    private Function<MessageId, Stream<
                                    Pair<org.apache.james.mailbox.store.mail.model.Message<Id>, 
                                         MailboxPath>>> 
                loadMessage(MailboxSession mailboxSession) {

        return Throwing
                .function((MessageId messageId) -> {
                     MailboxPath mailboxPath = messageId.getMailboxPath(mailboxSession);
                     MessageMapper<Id> messageMapper = messageMapperFactory.getMessageMapper(mailboxSession);
                     Mailbox<Id> mailbox = mailboxMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(mailboxPath);
                     return Pair.with(
                             messageMapper.findInMailbox(mailbox, MessageRange.one(messageId.getUid()), MessageMapper.FetchType.Full, 1),
                             mailboxPath
                             );
         })
                .andThen(this::iteratorToStream);
    }
    
    private Stream<Pair<org.apache.james.mailbox.store.mail.model.Message<Id>, MailboxPath>> iteratorToStream(Pair<Iterator<org.apache.james.mailbox.store.mail.model.Message<Id>>, MailboxPath> value) {
        Iterable<org.apache.james.mailbox.store.mail.model.Message<Id>> iterable = () -> value.getValue0();
        Stream<org.apache.james.mailbox.store.mail.model.Message<Id>> targetStream = StreamSupport.stream(iterable.spliterator(), false);
        
        MailboxPath mailboxPath = value.getValue1();
        return targetStream.map(x -> Pair.with(x, mailboxPath));
    }
}
