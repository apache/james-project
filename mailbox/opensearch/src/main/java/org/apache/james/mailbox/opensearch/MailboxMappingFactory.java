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

package org.apache.james.mailbox.opensearch;

import static org.apache.james.backends.es.v8.IndexCreationFactory.ANALYZER;
import static org.apache.james.backends.es.v8.IndexCreationFactory.BOOLEAN;
import static org.apache.james.backends.es.v8.IndexCreationFactory.CASE_INSENSITIVE;
import static org.apache.james.backends.es.v8.IndexCreationFactory.FIELDS;
import static org.apache.james.backends.es.v8.IndexCreationFactory.FORMAT;
import static org.apache.james.backends.es.v8.IndexCreationFactory.KEEP_MAIL_AND_URL;
import static org.apache.james.backends.es.v8.IndexCreationFactory.KEYWORD;
import static org.apache.james.backends.es.v8.IndexCreationFactory.LONG;
import static org.apache.james.backends.es.v8.IndexCreationFactory.NESTED;
import static org.apache.james.backends.es.v8.IndexCreationFactory.NORMALIZER;
import static org.apache.james.backends.es.v8.IndexCreationFactory.PROPERTIES;
import static org.apache.james.backends.es.v8.IndexCreationFactory.RAW;
import static org.apache.james.backends.es.v8.IndexCreationFactory.REQUIRED;
import static org.apache.james.backends.es.v8.IndexCreationFactory.ROUTING;
import static org.apache.james.backends.es.v8.IndexCreationFactory.SEARCH_ANALYZER;
import static org.apache.james.backends.es.v8.IndexCreationFactory.TYPE;

import java.io.StringReader;

import org.apache.james.backends.es.v8.IndexCreationFactory;
import org.apache.james.mailbox.opensearch.json.JsonMessageConstants;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;

public class MailboxMappingFactory {
    private static final String STANDARD = "standard";
    private static final String STORE = "store";

    public static TypeMapping getMappingContent() {
        return new TypeMapping.Builder()
            .withJson(new StringReader(generateMappingContent()))
            .build();
    }
    
    public static String generateMappingContent() {
        return "{" +
            "  \"dynamic\": \"strict\"," +
            "  \"" + ROUTING + "\": {" +
            "    \"" + REQUIRED + "\": true" +
            "  }," +
            "  \"" + PROPERTIES + "\": {" +
            "    \"" + JsonMessageConstants.MESSAGE_ID + "\": {" +
            "      \"" + TYPE + "\": \"" + KEYWORD + "\"," +
            "      \"" + STORE + "\": true" +
            "    }," +
            "    \"" + JsonMessageConstants.THREAD_ID + "\": {" +
            "      \"" + TYPE + "\": \"" + KEYWORD + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.UID + "\": {" +
            "      \"" + TYPE + "\": \"" + LONG + "\"," +
            "      \"" + STORE + "\": true" +
            "    }," +
            "    \"" + JsonMessageConstants.MODSEQ + "\": {" +
            "      \"" + TYPE + "\": \"" + LONG + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.SIZE + "\": {" +
            "      \"" + TYPE + "\": \"" + LONG + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.IS_ANSWERED + "\": {" +
            "      \"" + TYPE + "\": \"" + BOOLEAN + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.IS_DELETED + "\": {" +
            "      \"" + TYPE + "\": \"" + BOOLEAN + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.IS_DRAFT + "\": {" +
            "      \"" + TYPE + "\": \"" + BOOLEAN + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.IS_FLAGGED + "\": {" +
            "      \"" + TYPE + "\": \"" + BOOLEAN + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.IS_RECENT + "\": {" +
            "      \"" + TYPE + "\": \"" + BOOLEAN + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.IS_UNREAD + "\": {" +
            "      \"" + TYPE + "\": \"" + BOOLEAN + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.DATE + "\": {" +
            "      \"" + TYPE + "\": \"" + IndexCreationFactory.DATE + "\"," +
            "      \"" + FORMAT + "\": \"uuuu-MM-dd'T'HH:mm:ssX||uuuu-MM-dd'T'HH:mm:ssXXX||uuuu-MM-dd'T'HH:mm:ssXXXXX\"" +
            "    }," +
            "    \"" + JsonMessageConstants.SENT_DATE + "\": {" +
            "      \"" + TYPE + "\": \"" + IndexCreationFactory.DATE + "\"," +
            "      \"" + FORMAT + "\": \"uuuu-MM-dd'T'HH:mm:ssX||uuuu-MM-dd'T'HH:mm:ssXXX||uuuu-MM-dd'T'HH:mm:ssXXXXX\"" +
            "    }," +
            "    \"" + JsonMessageConstants.USER_FLAGS + "\": {" +
            "      \"" + TYPE + "\": \"" + KEYWORD + "\"," +
            "      \"" + NORMALIZER + "\": \"" + CASE_INSENSITIVE + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.MEDIA_TYPE + "\": {" +
            "      \"" + TYPE + "\": \"" + KEYWORD + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.SUBTYPE + "\" : {" +
            "      \"" + TYPE + "\": \"" + KEYWORD + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.FROM + "\": {" +
            "      \"" + PROPERTIES + "\": {" +
            "        \"" + JsonMessageConstants.EMailer.NAME + "\": {" +
            "          \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "          \"" + ANALYZER + "\": \"" + KEEP_MAIL_AND_URL + "\"" +
            "        }," +
            "        \"" + JsonMessageConstants.EMailer.ADDRESS + "\": {" +
            "          \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "          \"" + ANALYZER + "\": \"" + STANDARD + "\"," +
            "          \"" + SEARCH_ANALYZER + "\": \"" + KEEP_MAIL_AND_URL + "\"," +
            "          \"" + FIELDS + "\": {" +
            "            \"" + RAW + "\": {" +
            "              \"" + TYPE + "\": \"" + KEYWORD + "\"," +
            "              \"" + NORMALIZER + "\": \"" + CASE_INSENSITIVE + "\"" +
            "            }" +
            "          }" +
            "        }" +
            "      }" +
            "    }," +
            "    \"" + JsonMessageConstants.HEADERS + "\": {" +
            "      \"" + TYPE + "\": \"" + NESTED + "\"," +
            "      \"" + PROPERTIES + "\": {" +
            "        \"" + JsonMessageConstants.HEADER.NAME + "\": {" +
            "          \"" + TYPE + "\": \"" + KEYWORD + "\"" +
            "        }," +
            "        \"" + JsonMessageConstants.HEADER.VALUE + "\": {" +
            "          \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "          \"" + ANALYZER + "\": \"" + KEEP_MAIL_AND_URL + "\"" +
            "        }" +
            "      }" +
            "    }," +
            "    \"" + JsonMessageConstants.SUBJECT + "\": {" +
            "      \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "      \"" + ANALYZER + "\": \"" + KEEP_MAIL_AND_URL + "\"," +
            "      \"" + FIELDS + "\": {" +
            "        \"" + RAW + "\": {" +
            "          \"" + TYPE + "\": \"" + KEYWORD + "\"," +
            "          \"" + NORMALIZER + "\": \"" + CASE_INSENSITIVE + "\"" +
            "        }" +
            "      }" +
            "    }," +
            "    \"" + JsonMessageConstants.TO + "\": {" +
            "      \"" + PROPERTIES + "\": {" +
            "        \"" + JsonMessageConstants.EMailer.NAME + "\": {" +
            "          \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "          \"" + ANALYZER + "\": \"" + KEEP_MAIL_AND_URL + "\"" +
            "        }," +
            "        \"" + JsonMessageConstants.EMailer.ADDRESS + "\": {" +
            "          \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "          \"" + ANALYZER + "\": \"" + STANDARD + "\"," +
            "          \"" + SEARCH_ANALYZER + "\": \"" + KEEP_MAIL_AND_URL + "\"," +
            "          \"" + FIELDS + "\": {" +
            "            \"" + RAW + "\": {" +
            "              \"" + TYPE + "\": \"" + KEYWORD + "\"," +
            "              \"" + NORMALIZER + "\": \"" + CASE_INSENSITIVE + "\"" +
            "            }" +
            "          }" +
            "        }" +
            "      }" +
            "    }," +
            "    \"" + JsonMessageConstants.CC + "\": {" +
            "      \"" + PROPERTIES + "\": {" +
            "        \"" + JsonMessageConstants.EMailer.NAME + "\": {" +
            "          \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "          \"" + ANALYZER + "\": \"" + KEEP_MAIL_AND_URL + "\"" +
            "        }," +
            "        \"" + JsonMessageConstants.EMailer.ADDRESS + "\": {" +
            "          \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "          \"" + ANALYZER + "\": \"" + STANDARD + "\"," +
            "          \"" + SEARCH_ANALYZER + "\": \"" + KEEP_MAIL_AND_URL + "\"," +
            "          \"" + FIELDS + "\": {" +
            "            \"" + RAW + "\": {" +
            "              \"" + TYPE + "\": \"" + KEYWORD + "\"," +
            "              \"" + NORMALIZER + "\": \"" + CASE_INSENSITIVE + "\"" +
            "            }" +
            "          }" +
            "        }" +
            "      }" +
            "    }," +
            "    \"" + JsonMessageConstants.BCC + "\": {" +
            "      \"" + PROPERTIES + "\": {" +
            "        \"" + JsonMessageConstants.EMailer.NAME + "\": {" +
            "          \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "          \"" + ANALYZER + "\": \"" + KEEP_MAIL_AND_URL + "\"" +
            "        }," +
            "        \"" + JsonMessageConstants.EMailer.ADDRESS + "\": {" +
            "          \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "          \"" + ANALYZER + "\": \"" + STANDARD + "\"," +
            "          \"" + SEARCH_ANALYZER + "\": \"" + KEEP_MAIL_AND_URL + "\"," +
            "          \"" + FIELDS + "\": {" +
            "            \"" + RAW + "\": {" +
            "              \"" + TYPE + "\": \"" + KEYWORD + "\"," +
            "              \"" + NORMALIZER + "\": \"" + CASE_INSENSITIVE + "\"" +
            "            }" +
            "          }" +
            "        }" +
            "      }" +
            "    }," +
            "    \"" + JsonMessageConstants.MAILBOX_ID + "\": {" +
            "      \"" + TYPE + "\": \"" + KEYWORD + "\"," +
            "      \"" + STORE + "\": true" +
            "    }," +
            "    \"" + JsonMessageConstants.MIME_MESSAGE_ID + "\": {" +
            "      \"" + TYPE + "\": \"" + KEYWORD + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.TEXT_BODY + "\": {" +
            "      \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "      \"" + ANALYZER + "\": \"" + STANDARD + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.HTML_BODY + "\": {" +
            "      \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "      \"" + ANALYZER + "\": \"" + STANDARD + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.HAS_ATTACHMENT + "\": {" +
            "      \"" + TYPE + "\": \"" + BOOLEAN + "\"" +
            "    }," +
            "    \"" + JsonMessageConstants.ATTACHMENTS + "\": {" +
            "      \"" + PROPERTIES + "\": {" +
            "        \"" + JsonMessageConstants.Attachment.FILENAME + "\": {" +
            "          \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "          \"" + ANALYZER + "\": \"" + STANDARD + "\"" +
            "        }," +
            "        \"" + JsonMessageConstants.Attachment.TEXT_CONTENT + "\": {" +
            "          \"" + TYPE + "\": \"" + JsonMessageConstants.TEXT + "\"," +
            "          \"" + ANALYZER + "\": \"" + STANDARD + "\"" +
            "        }," +
            "        \"" + JsonMessageConstants.Attachment.MEDIA_TYPE + "\": {" +
            "          \"" + TYPE + "\": \"" + KEYWORD + "\"" +
            "        }," +
            "        \"" + JsonMessageConstants.Attachment.SUBTYPE + "\": {" +
            "          \"" + TYPE + "\": \"" + KEYWORD + "\"" +
            "        }," +
            "        \"" + JsonMessageConstants.Attachment.FILE_EXTENSION + "\": {" +
            "          \"" + TYPE + "\": \"" + KEYWORD + "\"" +
            "        }," +
            "        \"" + JsonMessageConstants.Attachment.CONTENT_DISPOSITION + "\": {" +
            "          \"" + TYPE + "\": \"" + KEYWORD + "\"" +
            "        }" +
            "      }" +
            "    }" +
            "  }" +
            "}";
    }
}
