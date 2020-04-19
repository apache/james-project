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

package org.apache.james.mailbox.model;

import java.io.IOException;
import java.util.Optional;

public class ParsedAttachment {
    interface Builder {
        @FunctionalInterface
        interface RequireContentType {
            RequireContent contentType(String contentType);
        }

        @FunctionalInterface
        interface RequireContent {
            RequireName content(byte[] bytes);
        }

        @FunctionalInterface
        interface RequireName {
            RequireCid name(Optional<String> name);

            default RequireCid name(String name) {
                return name(Optional.of(name));
            }

            default RequireCid noName() {
                return name(Optional.empty());
            }
        }

        @FunctionalInterface
        interface RequireCid {
            RequireIsInline cid(Optional<Cid> cid);

            default RequireIsInline cid(Cid cid) {
                return cid(Optional.of(cid));
            }

            default RequireIsInline noCid() {
                return cid(Optional.empty());
            }
        }

        @FunctionalInterface
        interface RequireIsInline {
            ParsedAttachment inline(boolean isInline);

            default ParsedAttachment inline() {
                return inline(true);
            }
        }
    }

    public static Builder.RequireContentType builder() {
        return contentType -> content -> name -> cid -> isInline -> new ParsedAttachment(contentType, content, name, cid, isInline);
    }

    private final String contentType;
    private final byte[] content;
    private final Optional<String> name;
    private final Optional<Cid> cid;
    private final boolean isInline;

    private ParsedAttachment(String contentType, byte[] content, Optional<String> name, Optional<Cid> cid, boolean isInline) {
        this.contentType = contentType;
        this.content = content;
        this.name = name;
        this.cid = cid;
        this.isInline = isInline;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }

    public Optional<String> getName() {
        return name;
    }

    public Optional<Cid> getCid() {
        return cid;
    }

    public boolean isInline() {
        return isInline;
    }

    public MessageAttachmentMetadata asMessageAttachment(AttachmentId attachmentId, long size) {
        return MessageAttachmentMetadata.builder()
            .attachment(AttachmentMetadata.builder()
                .attachmentId(attachmentId)
                .type(contentType)
                .size(size)
                .build())
            .name(name)
            .cid(cid)
            .isInline(isInline)
            .build();
    }

    public MessageAttachmentMetadata asMessageAttachment(AttachmentId attachmentId) throws IOException {
        return MessageAttachmentMetadata.builder()
            .attachment(AttachmentMetadata.builder()
                .attachmentId(attachmentId)
                .type(contentType)
                .size(content.length)
                .build())
            .name(name)
            .cid(cid)
            .isInline(isInline)
            .build();
    }
}
