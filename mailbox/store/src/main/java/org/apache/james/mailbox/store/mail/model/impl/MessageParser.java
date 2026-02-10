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

package org.apache.james.mailbox.store.mail.model.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mime4j.dom.Message;

import com.google.common.collect.ImmutableList;

public interface MessageParser {
    class ParsingResult extends Disposable.LeakAware<ParsingResult.Resource> {

        static class Resource extends Disposable.LeakAware.Resource {
            private final List<ParsedAttachment> attachments;

            Resource(List<ParsedAttachment> attachments, Disposable cleanup) {
                super(cleanup);
                this.attachments = attachments;
            }
        }

        public static final ParsingResult EMPTY = new ParsingResult(
            new Resource(ImmutableList.of(), () -> {

            }));

        public ParsingResult(Resource resource) {
            super(resource);
        }

        public ParsingResult(List<ParsedAttachment> attachments, Disposable cleanup) {
            this(new Resource(attachments, cleanup));
        }

        public List<ParsedAttachment> getAttachments() {
            return getResource().attachments;
        }
    }

    MessageParser.ParsingResult retrieveAttachments(InputStream fullContent) throws IOException;

    List<ParsedAttachment> retrieveAttachments(Message message) throws IOException;
}
