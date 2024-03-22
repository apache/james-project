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

package org.apache.james.jmap.draft.model.message.view;

import jakarta.inject.Inject;

import org.apache.james.jmap.draft.model.MessageProperties;

public class MetaMessageViewFactory {
    private final MessageFullViewFactory messageFullViewFactory;
    private final MessageHeaderViewFactory messageHeaderViewFactory;
    private final MessageMetadataViewFactory messageMetadataViewFactory;
    private final MessageFastViewFactory messageFastViewFactory;

    @Inject
    public MetaMessageViewFactory(MessageFullViewFactory messageFullViewFactory, MessageHeaderViewFactory messageHeaderViewFactory,
                                  MessageMetadataViewFactory messageMetadataViewFactory, MessageFastViewFactory messageFastViewFactory) {
        this.messageFullViewFactory = messageFullViewFactory;
        this.messageHeaderViewFactory = messageHeaderViewFactory;
        this.messageMetadataViewFactory = messageMetadataViewFactory;
        this.messageFastViewFactory = messageFastViewFactory;
    }

    public MessageViewFactory<? extends MessageView> getFactory(MessageProperties.ReadProfile readProfile) {
        switch (readProfile) {
            case Full:
                return messageFullViewFactory;
            case Header:
                return messageHeaderViewFactory;
            case Fast:
                return messageFastViewFactory;
            case Metadata:
                return messageMetadataViewFactory;
            default:
                throw new IllegalArgumentException(readProfile + " is not a James JMAP draft supported view");
        }
    }
}
