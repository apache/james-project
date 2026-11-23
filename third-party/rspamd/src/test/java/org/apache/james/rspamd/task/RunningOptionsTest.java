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

package org.apache.james.rspamd.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.rspamd.RspamdScanner;
import org.junit.jupiter.api.Test;

class RunningOptionsTest {
    @Test
    void classificationFilterShouldReadTheLastFlagHeader() throws Exception {
        Headers headers = mock(Headers.class);
        when(headers.headers()).thenAnswer(any -> List.of(
            new Header(RspamdScanner.FLAG_MAIL.asString(), "YES"),
            new Header(RspamdScanner.FLAG_MAIL.asString(), "NO")).iterator());
        MessageResult messageResult = mock(MessageResult.class);
        when(messageResult.getHeaders()).thenReturn(headers);

        assertThat(RunningOptions.ClassificationFilter.CLASSIFIED_AS_HAM.test(messageResult)).isTrue();
        assertThat(RunningOptions.ClassificationFilter.CLASSIFIED_AS_SPAM.test(messageResult)).isFalse();
    }
}
