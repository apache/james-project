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
package org.apache.james.mailbox.backup;

import java.util.List;
import java.util.Objects;

import org.apache.james.mailbox.model.MailboxAnnotation;

import com.google.common.collect.ImmutableList;

public class MailboxWithAnnotationsArchiveEntry implements MailArchiveEntry {
    private final String mailboxName;
    private final SerializedMailboxId mailboxId;

    private final ImmutableList<MailboxAnnotation> annotations;

    public MailboxWithAnnotationsArchiveEntry(String mailboxName, SerializedMailboxId mailboxId, List<MailboxAnnotation> annotations) {
        this.mailboxName = mailboxName;
        this.mailboxId = mailboxId;
        this.annotations = ImmutableList.copyOf(annotations);
    }

    public String getMailboxName() {
        return mailboxName;
    }

    public SerializedMailboxId getMailboxId() {
        return mailboxId;
    }

    public List<MailboxAnnotation> getAnnotations() {
        return annotations;
    }

    public MailboxWithAnnotationsArchiveEntry appendAnnotation(MailboxAnnotation annotation) {
        ImmutableList<MailboxAnnotation> newAnnotations = ImmutableList.<MailboxAnnotation>builder().addAll(annotations).add(annotation).build();
        return new MailboxWithAnnotationsArchiveEntry(mailboxName, mailboxId, newAnnotations);
    }

    @Override
    public ArchiveEntryType getType() {
        return ArchiveEntryType.MAILBOX;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MailboxWithAnnotationsArchiveEntry) {
            MailboxWithAnnotationsArchiveEntry that = (MailboxWithAnnotationsArchiveEntry) o;
            return Objects.equals(mailboxName, that.mailboxName) &&
                Objects.equals(mailboxId, that.mailboxId) &&
                Objects.equals(annotations, that.annotations);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mailboxName, mailboxId, annotations);
    }
}
