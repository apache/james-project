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

public interface CassandraPushSubscriptionTable {
    String TABLE_NAME = "push_subscription";
    String USER = "user";
    String DEVICE_CLIENT_ID = "device_client_id";
    String ID = "id";
    String EXPIRES = "expires";
    String TYPES = "types";
    String URL = "url";
    String VERIFICATION_CODE = "verification_code";
    String ENCRYPT_PUBLIC_KEY = "encrypt_public_key";
    String ENCRYPT_AUTH_SECRET = "encrypt_auth_secret";
    String VALIDATED = "validated";
}