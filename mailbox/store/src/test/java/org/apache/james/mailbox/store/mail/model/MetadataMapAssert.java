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

package org.apache.james.mailbox.store.mail.model;

import org.apache.james.mailbox.model.MessageMetaData;
import org.assertj.core.api.AbstractAssert;

import java.util.Map;

public class MetadataMapAssert extends AbstractAssert<MetadataMapAssert, Map<Long, MessageMetaData>> {

    public MetadataMapAssert(Map<Long, MessageMetaData> actual) {
        super(actual, MetadataMapAssert.class);
    }

    public static MetadataMapAssert assertThat(Map<Long, MessageMetaData> actual) {
        return new MetadataMapAssert(actual);
    }

    public MetadataMapAssert hasSize(int expectedSize) {
        if(actual.size() != expectedSize) {
            failWithMessage("Expecting size to be <%s> but is <%s>", expectedSize, actual.size());
        }
        return this;
    }

    @SuppressWarnings("rawtypes") 
    public MetadataMapAssert containsMetadataForMessages(Message... messages) {
        for(Message message : messages) {
            if (actual.get(message.getUid()).getUid() != message.getUid()) {
                failWithMessage("Expected UID stored in MessageMetadata to be <%s> but was <%s>", actual.get(message.getUid()).getUid(), message.getUid());
            }
            if (!actual.get(message.getUid()).getInternalDate().equals(message.getInternalDate())) {
                failWithMessage("Expected Internal date in MessageMetadata to be <%s> but was <%s>", actual.get(message.getUid()).getInternalDate(), message.getInternalDate());
            }
            if (actual.get(message.getUid()).getSize() != message.getFullContentOctets()) {
                failWithMessage("Expected Size stored in MessageMetadata to be <%s> but was <%s>", actual.get(message.getUid()).getSize(), message.getFullContentOctets());
            }
        }
        return this;
    }
}
