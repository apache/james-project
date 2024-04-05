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

package org.apache.james.jmap.http;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.api.SimpleTokenFactory;
import org.apache.james.jmap.draft.exceptions.InternalErrorException;
import org.apache.james.jmap.methods.BlobManager;
import org.apache.james.jmap.draft.utils.DownloadPath;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.Test;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerResponse;

public class DownloadRoutesTest {

    @Test
    public void downloadShouldFailWhenUnknownErrorOnAttachmentManager() {
        MailboxSession mailboxSession = MailboxSessionUtil.create(Username.of("User"));
        BlobManager mockedBlobManager = mock(BlobManager.class);
        when(mockedBlobManager.retrieve(any(List.class), eq(mailboxSession)))
            .thenReturn(Mono.error(new MailboxException()));
        Authenticator mockedAuthFilter = mock(Authenticator.class);
        SimpleTokenFactory nullSimpleTokenFactory = null;

        DownloadRoutes testee = new DownloadRoutes(mockedBlobManager, nullSimpleTokenFactory, new RecordingMetricFactory(), mockedAuthFilter);

        HttpServerResponse resp = mock(HttpServerResponse.class);
        assertThatThrownBy(() -> testee.download(mailboxSession, DownloadPath.ofBlobId("blobId"), resp).block())
            .isInstanceOf(InternalErrorException.class);
    }
}
