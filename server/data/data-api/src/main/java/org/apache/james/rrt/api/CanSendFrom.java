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
package org.apache.james.rrt.api;

import java.util.stream.Stream;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;

public interface CanSendFrom {

    /**
     * Indicate if the connectedUser can send a mail using the fromUser in the from clause.
     */
    boolean userCanSendFrom(Username connectedUser, Username fromUser);

    /**
     * For a given user, return all the addresses he can use in the from clause of an email.
     */
    Stream<MailAddress> allValidFromAddressesForUser(Username user) throws RecipientRewriteTable.ErrorMappingException, RecipientRewriteTableException;

}
