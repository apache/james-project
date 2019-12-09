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

package org.apache.james.imap.encode;

import java.io.IOException;

import org.apache.james.imap.message.response.RecentResponse;

public class RecentResponseEncoder implements ImapResponseEncoder<RecentResponse> {
    public static final String RECENT = "RECENT";

    @Override
    public Class<RecentResponse> acceptableMessages() {
        return RecentResponse.class;
    }

    @Override
    public void encode(RecentResponse recentResponse, ImapResponseComposer composer) throws IOException {
        int numberFlaggedRecent = recentResponse.getNumberFlaggedRecent();
        composer.untagged().message(numberFlaggedRecent).message(RECENT).end();
    }

}
