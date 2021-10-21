/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.jmap.api.pushsubscription;

import java.time.ZonedDateTime;
import java.util.Set;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.PushSubscription;
import org.apache.james.jmap.api.model.PushSubscriptionCreationRequest;
import org.apache.james.jmap.api.model.PushSubscriptionId;
import org.apache.james.jmap.api.model.TypeName;
import org.reactivestreams.Publisher;

public interface PushSubscriptionRepository {
    Publisher<PushSubscription> save(Username username, PushSubscriptionCreationRequest pushSubscriptionCreationRequest);

    Publisher<Void> updateExpireTime(Username username, PushSubscriptionId id, ZonedDateTime newExpire);

    Publisher<Void> updateTypes(Username username, PushSubscriptionId id, Set<TypeName> types);

    Publisher<Void> validateVerificationCode(Username username, PushSubscriptionId id);

    Publisher<Void> revoke(Username username, PushSubscriptionId id);

    Publisher<PushSubscription> get(Username username, Set<PushSubscriptionId> ids);

    Publisher<PushSubscription> list(Username username);
}
