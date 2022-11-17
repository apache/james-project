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

package org.apache.james.imap.message.request;

import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.mailbox.model.MailboxAnnotation;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class SetMetadataRequest extends AbstractImapRequest {
    private final String mailboxName;
    private final List<MailboxAnnotation> mailboxAnnotations;

    public SetMetadataRequest(Tag tag, String mailboxName, List<MailboxAnnotation> mailboxAnnotations) {
        super(tag, ImapConstants.SETMETADATA_COMMAND);
        this.mailboxName = mailboxName;
        this.mailboxAnnotations = ImmutableList.copyOf(mailboxAnnotations);
    }

    public String getMailboxName() {
        return mailboxName;
    }

    public List<MailboxAnnotation> getMailboxAnnotations() {
        return mailboxAnnotations;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("mailboxName", mailboxName)
            .add("mailboxAnnotations", mailboxAnnotations)
            .toString();
    }
}
