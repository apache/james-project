/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.queue.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class MailLoaderTest {
    @Test
    void storeExceptionShouldBePropagated() {
        Store<MimeMessage, MimeMessagePartsId> store = mock(Store.class);
        when(store.read(any())).thenReturn(Mono.error(new RuntimeException("Cassandra problem")));
        MailReferenceDTO dto = mock(MailReferenceDTO.class);
        when(dto.toMailReference(any())).thenReturn(mock(MailReference.class));
        MailLoader loader = new MailLoader(store, new HashBlobId.Factory());

        String result = loader.load(dto)
            .thenReturn("continued")
            .onErrorResume(RuntimeException.class, e -> Mono.just("caught"))
            .block();
        assertThat(result).isEqualTo("caught");
    }
}