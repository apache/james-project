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
import java.io.InputStream;
import java.util.Optional;

import org.apache.commons.io.IOUtils;

public class ParsedAttachment {
    interface Builder {
        @FunctionalInterface
        interface RequireContentType {
            RequireContent contentType(String contentType);
        }

        @FunctionalInterface
        interface RequireContent {
            RequireName content(InputStream stream);
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
    private final InputStream content;
    private final Optional<String> name;
    private final Optional<Cid> cid;
    private final boolean isInline;

    private ParsedAttachment(String contentType, InputStream content, Optional<String> name, Optional<Cid> cid, boolean isInline) {
        this.contentType = contentType;
        this.content = content;
        this.name = name;
        this.cid = cid;
        this.isInline = isInline;
    }

    public String getContentType() {
        return contentType;
    }

    public InputStream getContent() {
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

    public MessageAttachment asMessageAttachment(AttachmentId attachmentId) {
        try {
            return MessageAttachment.builder()
                .attachment(Attachment.builder()
                        .attachmentId(attachmentId)
                        .type(contentType)
                        .bytes(IOUtils.toByteArray(content))
                        .build())
                    .name(name)
                    .cid(cid)
                    .isInline(isInline)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
