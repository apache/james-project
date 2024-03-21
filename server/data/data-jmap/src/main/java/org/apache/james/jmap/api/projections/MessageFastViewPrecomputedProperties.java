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

package org.apache.james.jmap.api.projections;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.jmap.api.model.Preview;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageResult;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class MessageFastViewPrecomputedProperties {
    public static class Builder {
        @FunctionalInterface
        public interface RequirePreview {
            RequireHasAttachment preview(Preview preview);
        }

        @FunctionalInterface
        public interface RequireHasAttachment {
            FinalStage hasAttachment(boolean hasAttachment);

            default FinalStage hasAttachment() {
                return hasAttachment(true);
            }

            default FinalStage noAttachments() {
                return hasAttachment(false);
            }
        }

        public static class FinalStage {
            private final Preview preview;
            private final boolean hasAttachment;

            private FinalStage(Preview preview, boolean hasAttachment) {
                this.hasAttachment = hasAttachment;
                Preconditions.checkNotNull(preview, "'preview' cannot be null");
                this.preview = preview;
            }

            public MessageFastViewPrecomputedProperties build() {
                return new MessageFastViewPrecomputedProperties(preview, hasAttachment);
            }
        }
    }

    public static Builder.RequirePreview builder() {
        return preview -> hasAttachment -> new Builder.FinalStage(preview, hasAttachment);
    }

    public static class Factory {
        private final Preview.Factory previewFactory;

        @Inject
        public Factory(Preview.Factory previewFactory) {
            this.previewFactory = previewFactory;
        }

        public MessageFastViewPrecomputedProperties from(MessageResult messageResult) throws MailboxException, IOException {
            return builder()
                .preview(previewFactory.fromMessageResult(messageResult))
                .hasAttachment(hasAttachment(messageResult.getLoadedAttachments()))
                .build();
        }

        private boolean hasAttachment(List<MessageAttachmentMetadata> attachments) {
            return attachments.stream()
                .anyMatch(Predicate.not(MessageAttachmentMetadata::isInlinedWithCid));
        }
    }

    private final Preview preview;
    private final boolean hasAttachment;

    private MessageFastViewPrecomputedProperties(Preview preview, boolean hasAttachment) {
        this.preview = preview;
        this.hasAttachment = hasAttachment;
    }

    public Preview getPreview() {
        return preview;
    }

    public boolean hasAttachment() {
        return hasAttachment;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MessageFastViewPrecomputedProperties) {
            MessageFastViewPrecomputedProperties that = (MessageFastViewPrecomputedProperties) o;

            return Objects.equals(this.preview, that.preview)
                && Objects.equals(this.hasAttachment, that.hasAttachment);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(preview, hasAttachment);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("preview", preview)
            .add("hasAttachment", hasAttachment)
            .toString();
    }
}
