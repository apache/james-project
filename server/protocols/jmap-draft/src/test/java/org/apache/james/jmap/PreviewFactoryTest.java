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

package org.apache.james.jmap;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.mime.MessageContentExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreviewFactoryTest {
    Preview.Factory testee;

    @BeforeEach
    void setUp() {
        testee = new Preview.Factory(new MessageContentExtractor(), new JsoupHtmlTextExtractor());
    }

    @Test
    void fromMessageAsStringShouldReturnEmptyWhenNoBodyPart() throws Exception {
        Preview actual = testee.fromMessageAsString("header: value\r\n");

        assertThat(actual).isEqualTo(Preview.EMPTY);
    }

    @Test
    void fromMessageAsStringShouldReturnEmptyWhenEmptyBodyPart() throws Exception {
        Preview actual = testee.fromMessageAsString("header: value\r\n\r\n");

        assertThat(actual).isEqualTo(Preview.EMPTY);
    }

    @Test
    void fromMessageAsStringShouldReturnEmptyWhenBlankBodyPart() throws Exception {
        Preview actual = testee.fromMessageAsString("header: value\r\n\r\n  \r\n  \r\n");

        assertThat(actual).isEqualTo(Preview.EMPTY);
    }

    @Test
    void fromMessageAsStringShouldReturnSanitizedBodyTextValue() throws Exception {
        Preview actual = testee.fromMessageAsString("header: value\r\n\r\n  \r\nmessage  \r\n");

        assertThat(actual).isEqualTo(Preview.from("message"));
    }

    @Test
    void fromMessageAsStringShouldExtractHtml() throws Exception {
        Preview actual = testee.fromMessageAsString(ClassLoaderUtils.getSystemResourceAsString("fullMessage.eml"));

        assertThat(actual).isEqualTo(Preview.from("blabla bloblo"));
    }
}
