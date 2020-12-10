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

package org.apache.james.protocols.smtp.hook;

import java.util.Set;

import org.apache.james.protocols.smtp.SMTPSession;

import com.google.common.collect.ImmutableSet;

/**
 * Implement this interfaces to hook in the HELO Command
 */
public interface HeloHook extends Hook {
    default Set<String> implementedEsmtpFeatures() {
        return ImmutableSet.of();
    }

    /**
     * Return the HookResult after run the hook
     * 
     * @param session the SMTPSession
     * @param helo the helo name
     * @return HockResult
     */
    HookResult doHelo(SMTPSession session, String helo);
}
