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
package org.apache.james.imap.encode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.response.NamespaceResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class NamespaceResponseEncoderTest {
    ImapSession dummySession;
    ImapResponseComposer mockComposer;
    NamespaceResponseEncoder subject;

    @Before
    public void setUp() throws Exception {
        dummySession = new FakeImapSession();
        subject = new NamespaceResponseEncoder();
        mockComposer = mock(ImapResponseComposer.class);
    }

    @Test
    public void testOneSharedNamespaceShouldWriteNilThenPrefixThenDeliminatorThenNil()
            throws Exception {
        final String aPrefix = "A Prefix";
        final String aDeliminator = "@";

        List<NamespaceResponse.Namespace> namespaces = new ArrayList<>();
        namespaces.add(new NamespaceResponse.Namespace(aPrefix, aDeliminator
                .charAt(0)));
        subject.encode(new NamespaceResponse(null, null, namespaces),
                mockComposer, dummySession);

        InOrder inOrder = Mockito.inOrder(mockComposer);
        inOrder.verify(mockComposer, times(1)).untagged();
        inOrder.verify(mockComposer, times(1)).commandName(ImapConstants.NAMESPACE_COMMAND_NAME);
        inOrder.verify(mockComposer, times(2)).nil();
        inOrder.verify(mockComposer, times(2)).openParen();
        inOrder.verify(mockComposer, times(1)).quote(aPrefix + aDeliminator);
        inOrder.verify(mockComposer, times(1)).quote(aDeliminator);
        inOrder.verify(mockComposer, times(2)).closeParen();
        inOrder.verify(mockComposer, times(1)).end();
    }

    @Test
    public void testOneUsersNamespaceShouldWriteNilThenPrefixThenDeliminatorThenNil()
            throws Exception {
        final String aPrefix = "A Prefix";
        final String aDeliminator = "@";

        List<NamespaceResponse.Namespace> namespaces = new ArrayList<>();
        namespaces.add(new NamespaceResponse.Namespace(aPrefix, aDeliminator
                .charAt(0)));
        subject.encode(new NamespaceResponse(null, namespaces, null),
                mockComposer, dummySession);

        InOrder inOrder = Mockito.inOrder(mockComposer);
        inOrder.verify(mockComposer, times(1)).untagged();
        inOrder.verify(mockComposer, times(1)).commandName(ImapConstants.NAMESPACE_COMMAND_NAME);
        inOrder.verify(mockComposer, times(1)).nil();
        inOrder.verify(mockComposer, times(2)).openParen();
        inOrder.verify(mockComposer, times(1)).quote(aPrefix + aDeliminator);
        inOrder.verify(mockComposer, times(1)).quote(aDeliminator);
        inOrder.verify(mockComposer, times(2)).closeParen();
        inOrder.verify(mockComposer, times(1)).nil();
        inOrder.verify(mockComposer, times(1)).end();
    }

    @Test
    public void testOnePersonalNamespaceShouldWritePrefixThenDeliminatorThenNilNil()
            throws Exception {
        final String aPrefix = "A Prefix";
        final String aDeliminator = "@";

        List<NamespaceResponse.Namespace> namespaces = new ArrayList<>();
        namespaces.add(new NamespaceResponse.Namespace(aPrefix, aDeliminator
                .charAt(0)));
        subject.encode(new NamespaceResponse(namespaces, null, null),
                mockComposer, dummySession);

        InOrder inOrder = Mockito.inOrder(mockComposer);
        inOrder.verify(mockComposer, times(1)).untagged();
        inOrder.verify(mockComposer, times(1)).commandName(ImapConstants.NAMESPACE_COMMAND_NAME);
        inOrder.verify(mockComposer, times(2)).openParen();
        inOrder.verify(mockComposer, times(1)).quote(aPrefix + aDeliminator);
        inOrder.verify(mockComposer, times(1)).quote(aDeliminator);
        inOrder.verify(mockComposer, times(2)).closeParen();
        inOrder.verify(mockComposer, times(2)).nil();
        inOrder.verify(mockComposer, times(1)).end();
    }

    @Test
    public void testTwoPersonalNamespaceShouldWritePrefixThenDeliminatorThenNilNil()
            throws Exception {
        final String aPrefix = "A Prefix";
        final String aDeliminator = "@";
        final String anotherPrefix = "Another Prefix";
        final String anotherDeliminator = "^";

        List<NamespaceResponse.Namespace> namespaces = new ArrayList<>();
        namespaces.add(new NamespaceResponse.Namespace(aPrefix, aDeliminator
                .charAt(0)));
        namespaces.add(new NamespaceResponse.Namespace(anotherPrefix,
                anotherDeliminator.charAt(0)));
        subject.encode(new NamespaceResponse(namespaces, null, null),
                mockComposer, dummySession);

        InOrder inOrder = Mockito.inOrder(mockComposer);
        inOrder.verify(mockComposer, times(1)).untagged();
        inOrder.verify(mockComposer, times(1)).commandName(ImapConstants.NAMESPACE_COMMAND_NAME);
        inOrder.verify(mockComposer, times(2)).openParen();
        inOrder.verify(mockComposer, times(1)).quote(aPrefix + aDeliminator);
        inOrder.verify(mockComposer, times(1)).quote(aDeliminator);
        inOrder.verify(mockComposer, times(1)).closeParen();
        inOrder.verify(mockComposer, times(1)).openParen();
        inOrder.verify(mockComposer, times(1)).quote(anotherPrefix + anotherDeliminator);
        inOrder.verify(mockComposer, times(1)).quote(anotherDeliminator);
        inOrder.verify(mockComposer, times(2)).closeParen();
        inOrder.verify(mockComposer, times(2)).nil();
        inOrder.verify(mockComposer, times(1)).end();
    }

    @Test
    public void testAllNullShouldWriteAllNIL() throws Exception {

        subject.encode(new NamespaceResponse(null, null, null), mockComposer,
                dummySession);

        InOrder inOrder = Mockito.inOrder(mockComposer);
        inOrder.verify(mockComposer, times(1)).untagged();
        inOrder.verify(mockComposer, times(1)).commandName(ImapConstants.NAMESPACE_COMMAND_NAME);
        inOrder.verify(mockComposer, times(3)).nil();
        inOrder.verify(mockComposer, times(1)).end();
    }

    @Test
    public void testNamespaceResponseIsAcceptable() {
        assertThat(subject.acceptableMessages()).isEqualTo(NamespaceResponse.class);
    }

}
