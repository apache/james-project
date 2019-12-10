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

package org.apache.james.imap.encode.main;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.display.Localizer;
import org.apache.james.imap.encode.ACLResponseEncoder;
import org.apache.james.imap.encode.AnnotationResponseEncoder;
import org.apache.james.imap.encode.AuthenticateResponseEncoder;
import org.apache.james.imap.encode.CapabilityResponseEncoder;
import org.apache.james.imap.encode.ContinuationResponseEncoder;
import org.apache.james.imap.encode.ESearchResponseEncoder;
import org.apache.james.imap.encode.EnableResponseEncoder;
import org.apache.james.imap.encode.ExistsResponseEncoder;
import org.apache.james.imap.encode.ExpungeResponseEncoder;
import org.apache.james.imap.encode.FetchResponseEncoder;
import org.apache.james.imap.encode.FlagsResponseEncoder;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.imap.encode.ImapEncoderFactory;
import org.apache.james.imap.encode.ImapResponseComposer;
import org.apache.james.imap.encode.ImapResponseEncoder;
import org.apache.james.imap.encode.LSubResponseEncoder;
import org.apache.james.imap.encode.ListResponseEncoder;
import org.apache.james.imap.encode.ListRightsResponseEncoder;
import org.apache.james.imap.encode.MailboxStatusResponseEncoder;
import org.apache.james.imap.encode.MyRightsResponseEncoder;
import org.apache.james.imap.encode.NamespaceResponseEncoder;
import org.apache.james.imap.encode.QuotaResponseEncoder;
import org.apache.james.imap.encode.QuotaRootResponseEncoder;
import org.apache.james.imap.encode.RecentResponseEncoder;
import org.apache.james.imap.encode.SearchResponseEncoder;
import org.apache.james.imap.encode.StatusResponseEncoder;
import org.apache.james.imap.encode.VanishedResponseEncoder;
import org.apache.james.imap.encode.XListResponseEncoder;
import org.apache.james.imap.encode.base.EndImapEncoder;

import com.github.steveash.guavate.Guavate;

/**
 * TODO: perhaps a POJO would be better
 */
public class DefaultImapEncoderFactory implements ImapEncoderFactory {
    static class DefaultImapEncoder implements ImapEncoder {
        private final Map<Class<? extends ImapMessage>, ImapResponseEncoder> encoders;
        private final EndImapEncoder endImapEncoder;

        DefaultImapEncoder(Stream<ImapResponseEncoder> encoders, EndImapEncoder endImapEncoder) {
            this.encoders = encoders
                .collect(Guavate.toImmutableMap(
                    ImapResponseEncoder::acceptableMessages,
                    Function.identity()));
            this.endImapEncoder = endImapEncoder;
        }

        @Override
        public void encode(ImapMessage message, ImapResponseComposer composer) throws IOException {
            ImapResponseEncoder imapResponseEncoder = encoders.get(message.getClass());

            if (imapResponseEncoder != null) {
                imapResponseEncoder.encode(message, composer);
            } else {
                endImapEncoder.encode(message, composer);
            }
        }
    }

    /**
     * Builds the default encoder
     * 
     * @param localizer
     *            not null
     * @param neverAddBodyStructureExtensions
     *            true to activate a workaround for broken clients who cannot
     *            parse BODYSTRUCTURE extensions, false to fully support RFC3501
     * @return not null
     */
    public static final ImapEncoder createDefaultEncoder(Localizer localizer, boolean neverAddBodyStructureExtensions) {
        return new DefaultImapEncoder(Stream.of(
            new AnnotationResponseEncoder(),
            new MyRightsResponseEncoder(),
            new ListRightsResponseEncoder(),
            new ListResponseEncoder(),
            new ACLResponseEncoder(),
            new NamespaceResponseEncoder(),
            new StatusResponseEncoder(localizer),
            new RecentResponseEncoder(),
            new FetchResponseEncoder(neverAddBodyStructureExtensions),
            new ExpungeResponseEncoder(),
            new ExistsResponseEncoder(),
            new MailboxStatusResponseEncoder(),
            new SearchResponseEncoder(),
            new LSubResponseEncoder(),
            new XListResponseEncoder(),
            new FlagsResponseEncoder(),
            new CapabilityResponseEncoder(),
            new EnableResponseEncoder(),
            new ContinuationResponseEncoder(localizer),
            new AuthenticateResponseEncoder(),
            new ESearchResponseEncoder(),
            new VanishedResponseEncoder(),
            new QuotaResponseEncoder(),
            new QuotaRootResponseEncoder()),
            new EndImapEncoder());
    }

    private final Localizer localizer;
    private final boolean neverAddBodyStructureExtensions;

    public DefaultImapEncoderFactory() {
        this(new DefaultLocalizer(), false);
    }

    /**
     * Constructs the default factory for encoders
     * 
     * @param localizer
     *            not null
     * @param neverAddBodyStructureExtensions
     *            true to activate a workaround for broken clients who cannot
     *            parse BODYSTRUCTURE extensions, false to fully support RFC3501
     */
    public DefaultImapEncoderFactory(Localizer localizer, boolean neverAddBodyStructureExtensions) {
        this.localizer = localizer;
        this.neverAddBodyStructureExtensions = neverAddBodyStructureExtensions;
    }

    @Override
    public ImapEncoder buildImapEncoder() {
        return createDefaultEncoder(localizer, neverAddBodyStructureExtensions);
    }

}
