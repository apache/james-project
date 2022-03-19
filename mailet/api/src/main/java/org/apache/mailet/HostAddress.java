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


package org.apache.mailet;

/** 
 * A specialized subclass of jakarta.mail.URLName, which provides
 * the name of a URL as well as its corresponding host name.
 * 
 * @since Mailet API v2.3
 * @deprecated
 */
@Deprecated
public class HostAddress extends jakarta.mail.URLName {
    private final String hostname;

    /**
     * Constructs a new host address with the given details.
     * 
     * @param hostname the hostname corresponding to the url
     * @param url the url name
     */
    public HostAddress(String hostname, String url) {
        super(url);
        this.hostname = hostname;
    }

    /**
     * Returns the host name corresponding to the url
     * 
     * @return the host name
     */
    public String getHostName() {
        return hostname;
    }

}
