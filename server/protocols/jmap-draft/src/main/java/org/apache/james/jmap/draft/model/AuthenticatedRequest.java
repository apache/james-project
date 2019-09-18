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
package org.apache.james.jmap.draft.model;

import javax.servlet.http.HttpServletRequest;

import org.apache.james.jmap.draft.AuthenticationFilter;
import org.apache.james.mailbox.MailboxSession;

public class AuthenticatedRequest extends InvocationRequest {
    
    public static AuthenticatedRequest decorate(InvocationRequest request, HttpServletRequest httpServletRequest) {
        return new AuthenticatedRequest(request, httpServletRequest);
    }

    private final HttpServletRequest httpServletRequest;

    private AuthenticatedRequest(InvocationRequest request, HttpServletRequest httpServletRequest) {
        super(request.getMethodName(), request.getParameters(), request.getMethodCallId());
        this.httpServletRequest = httpServletRequest;
        
    }

    public MailboxSession getMailboxSession() {
        return (MailboxSession) httpServletRequest.getAttribute(AuthenticationFilter.MAILBOX_SESSION);
    }
}