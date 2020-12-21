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

package org.apache.james.jmap.memory.change;

import org.apache.james.jmap.api.change.EmailChangeRepository;
import org.apache.james.jmap.api.change.EmailChangeRepositoryContract;
import org.apache.james.jmap.api.change.MailboxChangeRepository;
import org.junit.jupiter.api.BeforeEach;

public class MemoryEmailChangeRepositoryTest implements EmailChangeRepositoryContract {
    EmailChangeRepository emailChangeRepository;

    @BeforeEach
    void setup() {
        emailChangeRepository = new MemoryEmailChangeRepository();
    }

    @Override
    public EmailChangeRepository emailChangeRepository() {
        return emailChangeRepository;
    }
}
