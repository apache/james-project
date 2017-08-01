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

package org.apache.james.mailbox.store.json.event.dto;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageMetaDataDataTransferObject {
    @JsonProperty()
    private long uid;
    @JsonProperty()
    private long modseq;
    @JsonProperty()
    private FlagsDataTransferObject flags;
    @JsonProperty()
    private long size;
    @JsonProperty()
    private String date;
    @JsonProperty
    private MessageId messageId;

    private static final Logger LOG = LoggerFactory.getLogger(MessageMetaDataDataTransferObject.class);

    private static final ThreadLocal<SimpleDateFormat> simpleDateFormat = ThreadLocal.withInitial(
        () -> new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"));

    private static Date parse(String date) throws ParseException {
        if (date != null) {
            return simpleDateFormat.get().parse(date);
        } else {
            return null;
        }
    }

    private static String format(Date date) {
        if (date != null) {
            return simpleDateFormat.get().format(date);
        } else {
            return null;
        }
    }


    public MessageMetaDataDataTransferObject() {

    }

    public MessageMetaDataDataTransferObject(MessageMetaData metadata) {
        this.uid = metadata.getUid().asLong();
        this.modseq = metadata.getModSeq();
        this.flags = new FlagsDataTransferObject(metadata.getFlags());
        this.size = metadata.getSize();
        this.date = format(metadata.getInternalDate());
        this.messageId = metadata.getMessageId();
    }

    @JsonIgnore
    public SimpleMessageMetaData getMetadata() {
        try {
            return new SimpleMessageMetaData(MessageUid.of(uid), modseq, flags.getFlags(), size, parse(date), messageId);
        } catch(ParseException parseException) {
            LOG.error("Parse exception while parsing date while deserializing metadata upon event serialization. Using nowadays date instead.", parseException);
            return new SimpleMessageMetaData(MessageUid.of(uid), modseq, flags.getFlags(), size, new Date(), messageId);
        }

    }
}