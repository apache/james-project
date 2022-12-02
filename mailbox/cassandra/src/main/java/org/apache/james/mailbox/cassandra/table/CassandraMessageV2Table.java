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

package org.apache.james.mailbox.cassandra.table;

import com.datastax.oss.driver.api.core.CqlIdentifier;

public interface CassandraMessageV2Table {

    String TABLE_NAME = "messageV2";
    CqlIdentifier INTERNAL_DATE = CqlIdentifier.fromCql("internalDate");
    CqlIdentifier BODY_START_OCTET = CqlIdentifier.fromCql("bodyStartOctet");
    CqlIdentifier FULL_CONTENT_OCTETS = CqlIdentifier.fromCql("fullContentOctets");
    CqlIdentifier BODY_OCTECTS = CqlIdentifier.fromCql("bodyOctets");
    CqlIdentifier TEXTUAL_LINE_COUNT = CqlIdentifier.fromCql("textualLineCount");
    CqlIdentifier BODY_CONTENT = CqlIdentifier.fromCql("bodyContent");
    CqlIdentifier HEADER_CONTENT = CqlIdentifier.fromCql("headerContent");
    CqlIdentifier PROPERTIES = CqlIdentifier.fromCql("properties");
    CqlIdentifier ATTACHMENTS = CqlIdentifier.fromCql("attachments");

    interface Properties {
        CqlIdentifier NAMESPACE = CqlIdentifier.fromCql("namespace");
        CqlIdentifier NAME = CqlIdentifier.fromCql("name");
        CqlIdentifier VALUE = CqlIdentifier.fromCql("value");
    }

    interface Attachments {
        CqlIdentifier ID = CqlIdentifier.fromCql("id");
        CqlIdentifier NAME = CqlIdentifier.fromCql("name");
        CqlIdentifier CID = CqlIdentifier.fromCql("cid");
        CqlIdentifier IS_INLINE = CqlIdentifier.fromCql("isInline");
    }

}
