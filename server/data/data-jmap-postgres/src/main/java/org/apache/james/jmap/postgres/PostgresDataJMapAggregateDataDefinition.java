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

package org.apache.james.jmap.postgres;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.jmap.postgres.change.PostgresEmailChangeDataDefinition;
import org.apache.james.jmap.postgres.change.PostgresMailboxChangeDataDefinition;
import org.apache.james.jmap.postgres.filtering.PostgresFilteringProjectionDataDefinition;
import org.apache.james.jmap.postgres.identity.PostgresCustomIdentityDataDefinition;
import org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewDataDefinition;
import org.apache.james.jmap.postgres.projections.PostgresMessageFastViewProjectionDataDefinition;
import org.apache.james.jmap.postgres.pushsubscription.PostgresPushSubscriptionDataDefinition;
import org.apache.james.jmap.postgres.upload.PostgresUploadDataDefinition;

public interface PostgresDataJMapAggregateDataDefinition {
    PostgresDataDefinition MODULE = PostgresDataDefinition.aggregateModules(
        PostgresUploadDataDefinition.MODULE,
        PostgresMessageFastViewProjectionDataDefinition.MODULE,
        PostgresEmailChangeDataDefinition.MODULE,
        PostgresMailboxChangeDataDefinition.MODULE,
        PostgresPushSubscriptionDataDefinition.MODULE,
        PostgresFilteringProjectionDataDefinition.MODULE,
        PostgresCustomIdentityDataDefinition.MODULE,
        PostgresEmailQueryViewDataDefinition.MODULE);
}
