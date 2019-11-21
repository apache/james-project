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

package org.apache.james.jmap.memory.preview;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.jmap.api.preview.MessagePreviewStore;
import org.apache.james.jmap.api.preview.Preview;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class MemoryMessagePreviewStore implements MessagePreviewStore {

    private final ConcurrentHashMap<MessageId, Preview> previews;

    public MemoryMessagePreviewStore() {
        this.previews = new ConcurrentHashMap<>();
    }

    @Override
    public Publisher<Void> store(MessageId messageId, Preview preview) {
        Preconditions.checkNotNull(messageId);
        Preconditions.checkNotNull(preview);

        return Mono.fromRunnable(() -> previews.put(messageId, preview));
    }

    @Override
    public Publisher<Preview> retrieve(MessageId messageId) {
        Preconditions.checkNotNull(messageId);

        return Mono.fromSupplier(() -> previews.get(messageId));
    }

    @Override
    public Publisher<Void> delete(MessageId messageId) {
        Preconditions.checkNotNull(messageId);

        return Mono.fromRunnable(() -> previews.remove(messageId));
    }
}
