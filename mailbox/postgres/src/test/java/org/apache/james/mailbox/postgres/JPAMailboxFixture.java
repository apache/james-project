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

package org.apache.james.mailbox.postgres;

import java.util.List;

import org.apache.james.mailbox.postgres.quota.model.MaxDomainMessageCount;
import org.apache.james.mailbox.postgres.quota.model.MaxDomainStorage;
import org.apache.james.mailbox.postgres.quota.model.MaxGlobalMessageCount;
import org.apache.james.mailbox.postgres.quota.model.MaxGlobalStorage;
import org.apache.james.mailbox.postgres.quota.model.MaxUserMessageCount;
import org.apache.james.mailbox.postgres.quota.model.MaxUserStorage;

import com.google.common.collect.ImmutableList;

public interface JPAMailboxFixture {

    List<Class<?>> QUOTA_PERSISTANCE_CLASSES = ImmutableList.of(
        MaxGlobalMessageCount.class,
        MaxGlobalStorage.class,
        MaxDomainStorage.class,
        MaxDomainMessageCount.class,
        MaxUserMessageCount.class,
        MaxUserStorage.class);

    List<String> QUOTA_TABLES_NAMES = ImmutableList.of(
        "JAMES_MAX_GLOBAL_MESSAGE_COUNT",
        "JAMES_MAX_GLOBAL_STORAGE",
        "JAMES_MAX_USER_MESSAGE_COUNT",
        "JAMES_MAX_USER_STORAGE",
        "JAMES_MAX_DOMAIN_MESSAGE_COUNT",
        "JAMES_MAX_DOMAIN_STORAGE"
    );
}
