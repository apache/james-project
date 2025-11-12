/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.managesieve.api.commands;

import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.UnknownSaslMechanism;


/**
 * @see <a href=http://tools.ietf.org/html/rfc5804#section-2.1>RFC 5804 AUTHENTICATE Command</a>
 */
public interface Authenticate {

    enum SupportedMechanism {
        PLAIN, XOAUTH2, OAUTHBEARER;

        public static SupportedMechanism retrieveMechanism(String serializedData) throws UnknownSaslMechanism {
            for (SupportedMechanism supportedMechanism : SupportedMechanism.values()) {
                if (supportedMechanism.toString().equalsIgnoreCase(serializedData)) {
                    return supportedMechanism;
                }
            }
            throw new UnknownSaslMechanism(serializedData);
        }
    }
    
    String chooseMechanism(Session session, String mechanism);
    
    String authenticate(Session session, String suppliedData);
}
