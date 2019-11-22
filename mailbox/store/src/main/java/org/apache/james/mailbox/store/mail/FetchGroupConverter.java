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

package org.apache.james.mailbox.store.mail;

import org.apache.james.mailbox.model.MessageResult;

public class FetchGroupConverter {
    /**
     * Use the passed {@link MessageResult.FetchGroup} and calculate the right
     * {@link MessageMapper.FetchType} for it
     */
    public static MessageMapper.FetchType getFetchType(MessageResult.FetchGroup group) {
        int content = group.content();
        boolean headers = false;
        boolean body = false;
        boolean full = false;

        if ((content & MessageResult.FetchGroup.HEADERS) > 0) {
            headers = true;
            content -= MessageResult.FetchGroup.HEADERS;
        }
        if (group.getPartContentDescriptors().size() > 0) {
            full = true;
        }
        if ((content & MessageResult.FetchGroup.BODY_CONTENT) > 0) {
            body = true;
            content -= MessageResult.FetchGroup.BODY_CONTENT;
        }

        if ((content & MessageResult.FetchGroup.FULL_CONTENT) > 0) {
            full = true;
            content -= MessageResult.FetchGroup.FULL_CONTENT;
        }

        if ((content & MessageResult.FetchGroup.MIME_DESCRIPTOR) > 0) {
            // If we need the mimedescriptor we MAY need the full content later
            // too.
            // This gives us no other choice then request it
            full = true;
            content -= MessageResult.FetchGroup.MIME_DESCRIPTOR;
        }
        if (full || (body && headers)) {
            return MessageMapper.FetchType.Full;
        } else if (body) {
            return MessageMapper.FetchType.Body;
        } else if (headers) {
            return MessageMapper.FetchType.Headers;
        } else {
            return MessageMapper.FetchType.Metadata;
        }
    }
}
