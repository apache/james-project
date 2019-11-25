/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http: ww.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.store.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MimePath;
import org.junit.jupiter.api.Test;

class FetchGroupConverterTest {
    @Test
    void getFetchTypeShouldReturnMetadataWhenMinimal() {
        assertThat(FetchGroupConverter.getFetchType(FetchGroup.MINIMAL))
            .isEqualTo(MessageMapper.FetchType.Metadata);
    }

    @Test
    void getFetchTypeShouldReturnHeadersWhenHeaders() {
        assertThat(FetchGroupConverter.getFetchType(FetchGroup.HEADERS))
            .isEqualTo(MessageMapper.FetchType.Headers);
    }

    @Test
    void getFetchTypeShouldReturnBodyContentWhenBody() {
        assertThat(FetchGroupConverter.getFetchType(FetchGroup.BODY_CONTENT))
            .isEqualTo(MessageMapper.FetchType.Body);
    }

    @Test
    void getFetchTypeShouldReturnFullWhenBodyAndHeaders() {
        FetchGroup fetchGroup = FetchGroup.BODY_CONTENT
            .or(FetchGroup.HEADERS_MASK);

        assertThat(FetchGroupConverter.getFetchType(fetchGroup))
            .isEqualTo(MessageMapper.FetchType.Full);
    }

    @Test
    void getFetchTypeShouldReturnFullWhenFull() {
        assertThat(FetchGroupConverter.getFetchType(FetchGroup.FULL_CONTENT))
            .isEqualTo(MessageMapper.FetchType.Full);
    }

    @Test
    void getFetchTypeShouldReturnFullWhenMimeContent() {
        FetchGroup fetchGroup = FetchGroup.MINIMAL
            .or(FetchGroup.MIME_CONTENT_MASK);
        assertThat(FetchGroupConverter.getFetchType(fetchGroup))
            .isEqualTo(MessageMapper.FetchType.Full);
    }

    @Test
    void getFetchTypeShouldReturnFullWhenMimeDescriptor() {
        FetchGroup fetchGroup = FetchGroup.MINIMAL
            .or(FetchGroup.MIME_DESCRIPTOR_MASK);
        assertThat(FetchGroupConverter.getFetchType(fetchGroup))
            .isEqualTo(MessageMapper.FetchType.Full);
    }

    @Test
    void getFetchTypeShouldReturnFullWhenMimeHeaders() {
        FetchGroup fetchGroup = FetchGroup.MINIMAL
            .or(FetchGroup.MIME_HEADERS_MASK);
        assertThat(FetchGroupConverter.getFetchType(fetchGroup))
            .isEqualTo(MessageMapper.FetchType.Full);
    }

    @Test
    void getFetchTypeShouldReturnFullWhenMimePartIsReadMinimally() {
        int[] parts = {12};
        FetchGroup fetchGroup = FetchGroup.MINIMAL
            .addPartContent(new MimePath(parts), FetchGroup.MINIMAL_MASK);
        assertThat(FetchGroupConverter.getFetchType(fetchGroup))
            .isEqualTo(MessageMapper.FetchType.Full);
    }

    @Test
    void getFetchTypeShouldReturnFullWhenMimePartHeadersIsRead() {
        int[] parts = {12};
        FetchGroup fetchGroup = FetchGroup.MINIMAL
            .addPartContent(new MimePath(parts), FetchGroup.HEADERS_MASK);
        assertThat(FetchGroupConverter.getFetchType(fetchGroup))
            .isEqualTo(MessageMapper.FetchType.Full);
    }

    @Test
    void getFetchTypeShouldReturnFullWhenMimePartBodyIsRead() {
        int[] parts = {12};
        FetchGroup fetchGroup = FetchGroup.MINIMAL
            .addPartContent(new MimePath(parts), FetchGroup.BODY_CONTENT_MASK);
        assertThat(FetchGroupConverter.getFetchType(fetchGroup))
            .isEqualTo(MessageMapper.FetchType.Full);
    }

    @Test
    void getFetchTypeShouldReturnFullWhenMimePartIsFullyRead() {
        int[] parts = {12};
        FetchGroup fetchGroup = FetchGroup.MINIMAL
            .addPartContent(new MimePath(parts), FetchGroup.FULL_CONTENT_MASK);
        assertThat(FetchGroupConverter.getFetchType(fetchGroup))
            .isEqualTo(MessageMapper.FetchType.Full);
    }
}