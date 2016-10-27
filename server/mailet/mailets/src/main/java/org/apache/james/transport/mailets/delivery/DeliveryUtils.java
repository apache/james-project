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


package org.apache.james.transport.mailets.delivery;

import org.apache.commons.logging.Log;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.MailAddress;

public class DeliveryUtils {

    public static String getUsername(MailAddress m, UsersRepository usersRepository, Log log) {
        try {
            if (usersRepository.supportVirtualHosting()) {
                return m.toString();
            } else {
                return m.getLocalPart() + "@localhost";
            }
        } catch (UsersRepositoryException e) {
            log.error("Unable to access UsersRepository", e);
            return m.getLocalPart() + "@localhost";

        }
    }

    public static String prettyPrint(MailAddress mailAddress) {
        if (mailAddress != null) {
            return  "<" + mailAddress.toString() + ">";
        } else {
            return  "<>";
        }
    }

}
