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

package org.apache.james.jwt;

import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

public interface PublicKeyProvider {

    /**
     * Returns all keys managed by this provider.
     *
     * @return all keys managed by this provider
     * @throws MissingOrInvalidKeyException if an error occurred while getting the keys
     */
    List<PublicKey> get() throws MissingOrInvalidKeyException;

    /**
     * Returns the key corresponding to the given kid.
     *
     * @param kid the kid value
     * @return the key corresponding to the given kid, or empty if not found
     * @throws MissingOrInvalidKeyException if an error occurred while getting the key
     */
    Optional<PublicKey> get(String kid) throws MissingOrInvalidKeyException;
}
