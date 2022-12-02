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

public interface CassandraMessageV3Table {
    String TABLE_NAME = "messageV3";

    CqlIdentifier INTERNAL_DATE = CqlIdentifier.fromCql("internalDate");
    CqlIdentifier BODY_START_OCTET = CqlIdentifier.fromCql("bodyStartOctet");
    CqlIdentifier FULL_CONTENT_OCTETS = CqlIdentifier.fromCql("fullContentOctets");
    CqlIdentifier BODY_OCTECTS = CqlIdentifier.fromCql("bodyOctets");
    CqlIdentifier TEXTUAL_LINE_COUNT = CqlIdentifier.fromCql("textualLineCount");
    CqlIdentifier BODY_CONTENT = CqlIdentifier.fromCql("bodyContent");
    CqlIdentifier HEADER_CONTENT = CqlIdentifier.fromCql("headerContent");
    CqlIdentifier ATTACHMENTS = CqlIdentifier.fromCql("attachments");

    interface Properties {
        CqlIdentifier MEDIA_TYPE = CqlIdentifier.fromCql("mediaType");
        CqlIdentifier SUB_TYPE = CqlIdentifier.fromCql("subType");
        CqlIdentifier CONTENT_ID = CqlIdentifier.fromCql("contentId");
        CqlIdentifier CONTENT_LOCATION = CqlIdentifier.fromCql("contentLocation");
        CqlIdentifier CONTENT_DESCRIPTION = CqlIdentifier.fromCql("contentDescription");
        CqlIdentifier CONTENT_TRANSFER_ENCODING = CqlIdentifier.fromCql("contentTransferEncoding");
        CqlIdentifier CONTENT_DISPOSITION_TYPE = CqlIdentifier.fromCql("contentDispositionType");
        CqlIdentifier CONTENT_DISPOSITION_PARAMETERS = CqlIdentifier.fromCql("contentDispositionParameters");
        CqlIdentifier CONTENT_TYPE_PARAMETERS = CqlIdentifier.fromCql("contentTypeParameters");
        CqlIdentifier CONTENT_MD5 = CqlIdentifier.fromCql("contentMd5");
        CqlIdentifier CONTENT_LANGUAGE = CqlIdentifier.fromCql("contentLanguage");
    }

    interface Attachments {
        CqlIdentifier ID = CqlIdentifier.fromCql("id");
        CqlIdentifier NAME = CqlIdentifier.fromCql("name");
        CqlIdentifier CID = CqlIdentifier.fromCql("cid");
        CqlIdentifier IS_INLINE = CqlIdentifier.fromCql("isInline");
    }
}
