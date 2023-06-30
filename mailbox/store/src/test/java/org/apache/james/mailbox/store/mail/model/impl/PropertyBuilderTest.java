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

package org.apache.james.mailbox.store.mail.model.impl;

import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_MD5_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_MD5_SPACE;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.store.mail.model.Property;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class PropertyBuilderTest {
    @Test
    void emptyPropertyBuilderShouldCreateEmptyProperties() {
        assertThat(new PropertyBuilder().toProperties()).isEmpty();
    }

    @Test
    void nullValuePropertyBuilderShouldCreateEmptyProperties() {
        List<String> listOfNulls = Arrays.asList(null, null, null);
        Map <String,String> mapWithNullValues = Collections.singletonMap("k1", null);

        PropertyBuilder builder = new PropertyBuilder();
        builder.setContentLanguage(listOfNulls);
        builder.setContentTypeParameters(mapWithNullValues);
        builder.setCharset(null);

        assertThat(builder.toProperties()).isEmpty();
    }

    @Test
    void setContentMD5ShouldAddMd5Property() {
        PropertyBuilder propertyBuilder = new PropertyBuilder();
        propertyBuilder.setContentMD5("123");
        assertThat(propertyBuilder.toProperties())
            .containsOnly(new Property(MIME_CONTENT_MD5_SPACE, MIME_CONTENT_MD5_NAME, "123"));
    }
}
