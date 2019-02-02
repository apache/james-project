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

package org.apache.james.core;

import static org.apache.james.core.CoreFixture.Domains.ALPHABET_TLD;
import static org.apache.james.core.CoreFixture.Domains.DOMAIN_TLD;
import static org.apache.james.core.CoreFixture.Domains.SIMPSON_COM;

public interface CoreFixture {
    interface Domains {
        Domain DOMAIN_TLD = Domain.of("domain.tld");
        Domain ALPHABET_TLD = Domain.of("alphabet.tld");
        Domain SIMPSON_COM = Domain.of("simpson.com");
    }

    interface Users {
        interface Simpson {
            User BART = User.fromLocalPartWithDomain("bart", SIMPSON_COM);
            User HOMER = User.fromLocalPartWithDomain("homer", SIMPSON_COM);
            User LISA = User.fromLocalPartWithDomain("lisa", SIMPSON_COM);
        }

        interface Alphabet {
            User AAA = User.fromLocalPartWithDomain("aaa", ALPHABET_TLD);
            User ABA = User.fromLocalPartWithDomain("aba", ALPHABET_TLD);
            User ABB = User.fromLocalPartWithDomain("abb", ALPHABET_TLD);
            User ACB = User.fromLocalPartWithDomain("acb", ALPHABET_TLD);
        }

        User BENOIT_AT_DOMAIN_TLD = User.fromLocalPartWithDomain("benoit", DOMAIN_TLD);
    }
}
