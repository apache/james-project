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

import java.util.Optional;
import java.util.stream.Stream;

public interface MailRepositoryStore {

    /**
     * Select the {@link MailRepository} for the given url.
     *
     * If the repository is not referenced by {@link MailRepositoryStore::getUrls}, it will be created, and its URL referenced
     * by {@link MailRepositoryStore::getUrls}.
     */
    MailRepository select(MailRepositoryUrl url) throws MailRepositoryStoreException;

    Optional<Protocol> defaultProtocol();

    /**
     * Create the {@link MailRepository} for the given url and return it. If the repository already exists,
     * then no new repository is created, the old one will be returned.
     *
     * The URL of the created repository will be referenced by {@link MailRepositoryStore::getUrls}
     */
    default MailRepository create(MailRepositoryUrl url) throws MailRepositoryStoreException {
        return select(url);
    }

    /**
     * Returns the {@link MailRepository} for the given url.
     *
     * This mail repository will not be created if the URL is not referenced by {@link MailRepositoryStore::getUrls}.
     *
     * If the repository is referenced by {@link MailRepositoryStore::getUrls}, and the repository do not exist locally, then
     * this repository will be created locally.
     */
    Optional<MailRepository> get(MailRepositoryUrl url) throws MailRepositoryStoreException;

    /**
     * Returns all the {@link MailRepository} referenced by {@link MailRepositoryStore::getUrls} got a given path.
     *
     * The corresponding mail repositories will not be created if they do not exist.
     *
     * If the path matches URLs referenced by {@link MailRepositoryStore::getUrls}, and the repositories do not exist locally, then
     * these repositories will be created locally.
     */
    Stream<MailRepository> getByPath(MailRepositoryPath path) throws MailRepositoryStoreException;

    /**
     * Return a {@link Stream} which contains all urls of the selected {@link MailRepository}'s.
     *
     * Note that this may include MailRepositories that do not exist locally.
     *
     * This can be the case if:
     *  - The MailRepository had been created by another James server in a clustered environment
     *  - The MailRepository had been dynamically created, and James was restarted
     */
    Stream<MailRepositoryUrl> getUrls();

    /**
     * Return a {@link Stream} which contains all paths of the selected {@link MailRepository}'s
     */
    default Stream<MailRepositoryPath> getPaths() {
        return getUrls()
            .map(MailRepositoryUrl::getPath)
            .sorted()
            .distinct();
    }

    class MailRepositoryStoreException extends Exception {
        public MailRepositoryStoreException(String msg, Throwable t) {
            super(msg, t);
        }

        public MailRepositoryStoreException(String msg) {
            super(msg);
        }
    }

    class UnsupportedRepositoryStoreException extends MailRepositoryStoreException {
        public UnsupportedRepositoryStoreException(String msg, Throwable t) {
            super(msg, t);
        }

        public UnsupportedRepositoryStoreException(String msg) {
            super(msg);
        }
    }
}
