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

package org.apache.james.mailrepository.api;

import java.util.List;
import java.util.Optional;

public interface MailRepositoryStore {

    /**
     * Select the {@link MailRepository} for the given url. Repository will be created if it does not exist already.
     * 
     * @param url
     * @return repository
     * @throws MailRepositoryStoreException
     */
    MailRepository select(String url) throws MailRepositoryStoreException;

    /**
     * Returns the {@link MailRepository} for the given url.
     * This mail repository will not be created if it does not exist.
     */
    Optional<MailRepository> get(String url) throws MailRepositoryStoreException;

    /**
     * Return a {@link List} which contains all urls of the selected
     * {@link MailRepository}'s
     * 
     * @return urls
     */
    List<String> getUrls();

    class MailRepositoryStoreException extends Exception {
        public MailRepositoryStoreException(String msg, Throwable t) {
            super(msg, t);
        }

        public MailRepositoryStoreException(String msg) {
            super(msg);
        }
    }
}
