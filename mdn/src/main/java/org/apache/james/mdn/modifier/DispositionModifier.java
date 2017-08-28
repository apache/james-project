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

package org.apache.james.mdn.modifier;

/**
 * Interface <code>DispositionModifier</code> marks a type encapsulating
 * disposition modifier information as defined by RFC 8098.
 *
 * https://tools.ietf.org/html/rfc8098#section-3.2.6.3
 */
public class DispositionModifier {
    public static DispositionModifier Error = new DispositionModifier("error");
    public static DispositionModifier Expired = new DispositionModifier("expired");
    public static DispositionModifier Failed = new DispositionModifier("failed");
    public static DispositionModifier MailboxTerminated = new DispositionModifier("mailbox-terminated");
    public static DispositionModifier Superseded = new DispositionModifier("superseded");
    public static DispositionModifier Warning = new DispositionModifier("warning");

    private final String value;

    public DispositionModifier(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
