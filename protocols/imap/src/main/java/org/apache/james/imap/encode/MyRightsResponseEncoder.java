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
import java.util.Optional;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.message.response.MyRightsResponse;

import com.github.fge.lambdas.Throwing;

/**
 * MYRIGHTS Response Encoder.
 */
public class MyRightsResponseEncoder implements ImapResponseEncoder<MyRightsResponse> {
    @Override
    public Class<MyRightsResponse> acceptableMessages() {
        return MyRightsResponse.class;
    }

    @Override
    public void encode(MyRightsResponse aclResponse, ImapResponseComposer composer) throws IOException {
        composer.untagged();
        composer.commandName(ImapConstants.MYRIGHTS_COMMAND);

        Optional.ofNullable(aclResponse.getMailboxName()).ifPresent(Throwing.consumer(value -> composer.quote(value.asString())));
        Optional.ofNullable(aclResponse.getMyRights()).ifPresent(Throwing.consumer(value -> composer.quote(value.serialize())));

        composer.end();
    }
}
