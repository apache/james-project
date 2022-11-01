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

package org.apache.james.jmap.cassandra.pushsubscription.tables;

import com.datastax.oss.driver.api.core.CqlIdentifier;

public interface CassandraPushSubscriptionTable {
    String TABLE_NAME = "push_subscription";

    CqlIdentifier USER = CqlIdentifier.fromCql("user");
    CqlIdentifier DEVICE_CLIENT_ID = CqlIdentifier.fromCql("device_client_id");
    CqlIdentifier ID = CqlIdentifier.fromCql("id");
    CqlIdentifier EXPIRES = CqlIdentifier.fromCql("expires");
    CqlIdentifier TYPES = CqlIdentifier.fromCql("types");
    CqlIdentifier URL = CqlIdentifier.fromCql("url");
    CqlIdentifier VERIFICATION_CODE = CqlIdentifier.fromCql("verification_code");
    CqlIdentifier ENCRYPT_PUBLIC_KEY = CqlIdentifier.fromCql("encrypt_public_key");
    CqlIdentifier ENCRYPT_AUTH_SECRET = CqlIdentifier.fromCql("encrypt_auth_secret");
    CqlIdentifier VALIDATED = CqlIdentifier.fromCql("validated");
}