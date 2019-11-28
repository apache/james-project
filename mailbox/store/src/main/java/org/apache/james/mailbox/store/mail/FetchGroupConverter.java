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

import java.util.EnumSet;

import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.FetchGroup.Profile;

public class FetchGroupConverter {
    /**
     * Use the passed {@link FetchGroup} and calculate the right
     * {@link MessageMapper.FetchType} for it
     */
    public static MessageMapper.FetchType getFetchType(FetchGroup group) {
        EnumSet<Profile> profiles = group.profiles();

        if (profiles.contains(Profile.FULL_CONTENT)) {
            return MessageMapper.FetchType.Full;
        }
        if (profiles.contains(Profile.MIME_DESCRIPTOR)) {
            // If we need the mimedescriptor we MAY need the full profile later too.
            // This gives us no other choice then request it
            return MessageMapper.FetchType.Full;
        }
        if (profiles.contains(Profile.MIME_CONTENT)) {
            return MessageMapper.FetchType.Full;
        }
        if (profiles.contains(Profile.MIME_HEADERS)) {
            return MessageMapper.FetchType.Full;
        }
        if (!group.getPartContentDescriptors().isEmpty()) {
            return MessageMapper.FetchType.Full;
        }

        boolean headers = profiles.contains(Profile.HEADERS);
        boolean body = profiles.contains(Profile.BODY_CONTENT);

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
}
