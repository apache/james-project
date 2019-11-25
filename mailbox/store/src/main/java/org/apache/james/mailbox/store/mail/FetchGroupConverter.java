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

import org.apache.james.mailbox.model.FetchGroup;

public class FetchGroupConverter {
    /**
     * Use the passed {@link FetchGroup} and calculate the right
     * {@link MessageMapper.FetchType} for it
     */
    public static MessageMapper.FetchType getFetchType(FetchGroup group) {
        if (hasMask(group, FetchGroup.FULL_CONTENT_MASK)) {
            return MessageMapper.FetchType.Full;
        }
        if (hasMask(group, FetchGroup.MIME_DESCRIPTOR_MASK)) {
            // If we need the mimedescriptor we MAY need the full content later
            // too.
            // This gives us no other choice then request it
            return MessageMapper.FetchType.Full;
        }
        if (hasMask(group, FetchGroup.MIME_CONTENT_MASK)) {
            return MessageMapper.FetchType.Full;
        }
        if (hasMask(group, FetchGroup.MIME_HEADERS_MASK)) {
            return MessageMapper.FetchType.Full;
        }
        if (!group.getPartContentDescriptors().isEmpty()) {
            return MessageMapper.FetchType.Full;
        }

        boolean headers = hasMask(group, FetchGroup.HEADERS_MASK);
        boolean body = hasMask(group, FetchGroup.BODY_CONTENT_MASK);

        if (body && headers) {
            return MessageMapper.FetchType.Full;
        } else if (body) {
            return MessageMapper.FetchType.Body;
        } else if (headers) {
            return MessageMapper.FetchType.Headers;
        } else {
            return MessageMapper.FetchType.Metadata;
        }
    }

    private static boolean hasMask(FetchGroup group, int mask) {
        return (group.content() & mask) > 0;
    }
}
