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

package org.apache.james.user.api;

import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

public interface DelegationStore {
    interface Fluent {
        Publisher<Void> forUser(Username baseUser);
    }

    /**
     * @return Lists of the users authorized to impersonnate to baseUser.
     */
    Publisher<Username> authorizedUsers(Username baseUser); // delegatees list

    Publisher<Void> clear(Username baseUser);

    Publisher<Void> addAuthorizedUser(Username baseUser, Username userWithAccess);

    default Fluent addAuthorizedUser(Username userWithAccess) {
        return baseUser -> addAuthorizedUser(baseUser, userWithAccess);
    }

    Publisher<Void> removeAuthorizedUser(Username baseUser, Username userWithAccess);

    default Fluent removeAuthorizedUser(Username userWithAccess) {
        return baseUser -> removeAuthorizedUser(baseUser, userWithAccess);
    }

    Publisher<Username> delegatedUsers(Username baseUser);

    Publisher<Void> removeDelegatedUser(Username baseUser, Username delegatedToUser);

}
