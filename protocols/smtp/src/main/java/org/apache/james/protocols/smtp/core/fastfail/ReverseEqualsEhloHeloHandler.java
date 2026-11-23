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

package org.apache.james.protocols.smtp.core.fastfail;

import java.net.UnknownHostException;

import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;

public class ReverseEqualsEhloHeloHandler extends ResolvableEhloHeloHandler {

    @Override
    protected boolean isBadHelo(SMTPSession session, String argument) {
        try {
            String reverse = withoutTrailingDot(resolve(session.getRemoteAddress().getAddress().getHostAddress()));
            if (!withoutTrailingDot(argument).equalsIgnoreCase(reverse)) {
                return true;
            }
        } catch (UnknownHostException e) {
            // ignore
        }

        return false;
    }

    /**
     * Clients allowed to relay, authenticated ones included, are not checked.
     */
    @Override
    protected HookResult doCheck(SMTPSession session) {
        if (session.isRelayingAllowed() || session.getUsername() != null) {
            return HookResult.DECLINED;
        }
        return super.doCheck(session);
    }

    private static String withoutTrailingDot(String name) {
        if (name.endsWith(".")) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }
}
