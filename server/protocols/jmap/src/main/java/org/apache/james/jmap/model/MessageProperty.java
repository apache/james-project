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
package org.apache.james.jmap.model;

import com.google.common.collect.ImmutableSet;

public enum MessageProperty implements Property {
    id("id"),
    blobId("blobId"),
    threadId("threadId"),
    mailboxIds("mailboxIds"),
    inReplyToMessageId("inReplyToMessageId"),
    isUnread("isUnread"),
    isFlagged("isFlagged"),
    isAnswered("isAnswered"),
    isDraft("isDraft"),
    hasAttachment("hasAttachment"),
    headers("headers"),
    from("from"),
    to("to"),
    cc("cc"),
    bcc("bcc"),
    replyTo("replyTo"),
    subject("subject"),
    date("date"),
    size("size"),
    preview("preview"),
    textBody("textBody"),
    htmlBody("htmlBody"),
    attachments("attachments"),
    attachedMessages("attachedMessages"),
    body("body"),
    headers_property("headers.property");
    
    private String property;

    MessageProperty(String property) {
        this.property = property;
    }
    
    public String asFieldName() {
        return property;
    }
    
    public static ImmutableSet<MessageProperty> all() {
        return ImmutableSet.copyOf(values());
    }
}
