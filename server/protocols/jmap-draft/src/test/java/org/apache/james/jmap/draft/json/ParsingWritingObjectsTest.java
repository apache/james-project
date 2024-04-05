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

package org.apache.james.jmap.draft.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.apache.james.jmap.draft.json.ParsingWritingObjects.MESSAGE;
import static org.apache.james.jmap.draft.json.ParsingWritingObjects.SUB_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.james.jmap.draft.methods.GetMessagesMethod;
import org.apache.james.jmap.json.ObjectMapperFactory;
import org.apache.james.jmap.methods.JmapResponseWriterImpl;
import org.apache.james.jmap.model.message.view.SubMessage;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

public class ParsingWritingObjectsTest {
    
    private ObjectMapperFactory testee;

    @Before
    public void setup() {
        testee = new ObjectMapperFactory(new InMemoryId.Factory(), new TestMessageId.Factory());
    }

    @Test
    public void parsingJsonShouldWorkOnSubMessage() throws Exception {
        SubMessage expected = SUB_MESSAGE;

        SubMessage subMessage = testee.forParsing()
            .readValue(IOUtils.toString(ClassLoader.getSystemResource("json/subMessage.json"), StandardCharsets.UTF_8), SubMessage.class);

        assertThat(subMessage).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void writingJsonShouldWorkOnSubMessage() throws Exception {
        String expected = IOUtils.toString(ClassLoader.getSystemResource("json/subMessage.json"), StandardCharsets.UTF_8);

        String json = testee.forWriting()
                .writeValueAsString(SUB_MESSAGE);

        assertThatJson(json)
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo(expected);

    }

    @Test
    public void writingJsonShouldWorkOnMessage() throws Exception {
        String expected = IOUtils.toString(ClassLoader.getSystemResource("json/message.json"), StandardCharsets.UTF_8);

        SimpleFilterProvider filterProvider = new SimpleFilterProvider()
                .addFilter(JmapResponseWriterImpl.PROPERTIES_FILTER, SimpleBeanPropertyFilter.serializeAll())
                .addFilter(GetMessagesMethod.HEADERS_FILTER, SimpleBeanPropertyFilter.serializeAll());

        String json = testee.forWriting()
                .setFilterProvider(filterProvider)
                .writeValueAsString(MESSAGE);

        assertThatJson(json)
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo(expected);

    }
}
