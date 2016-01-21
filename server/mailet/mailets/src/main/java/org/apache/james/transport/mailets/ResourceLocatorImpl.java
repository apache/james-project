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

package org.apache.james.transport.mailets;

import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.SieveRepositoryException;
import org.apache.jsieve.mailet.ResourceLocator;

import java.util.Date;

public class ResourceLocatorImpl implements ResourceLocator {

    private final boolean virtualHosting;
    private final SieveRepository sieveRepository;

    public ResourceLocatorImpl(boolean virtualHosting, SieveRepository sieveRepository) {
        this.virtualHosting = virtualHosting;
        this.sieveRepository = sieveRepository;
    }

    public UserSieveInformation get(String uri) throws SieveRepositoryException {
        // Use the complete email address for finding the sieve file
        uri = uri.substring(2);

        String username;
        if (virtualHosting) {
            username = uri.substring(0, uri.indexOf("/"));
        } else {
            username = uri.substring(0, uri.indexOf("@"));
        }

        return new UserSieveInformation(sieveRepository.getStorageDateForActiveScript(username), new Date(), sieveRepository.getActive(username));
    }
}
