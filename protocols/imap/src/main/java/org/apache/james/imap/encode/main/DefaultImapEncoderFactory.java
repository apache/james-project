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

/**
 * TODO: perhaps a POJO would be better
 */
public class DefaultImapEncoderFactory implements ImapEncoderFactory {

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
        final EndImapEncoder endImapEncoder = new EndImapEncoder();
        
        final AnnotationResponseEncoder annotationResponseEncoder = new AnnotationResponseEncoder(endImapEncoder);
        final MyRightsResponseEncoder myRightsResponseEncoder = new MyRightsResponseEncoder(annotationResponseEncoder); 
        final ListRightsResponseEncoder listRightsResponseEncoder = new ListRightsResponseEncoder(myRightsResponseEncoder); 
        final ACLResponseEncoder aclResponseEncoder = new ACLResponseEncoder(listRightsResponseEncoder); 
        final NamespaceResponseEncoder namespaceEncoder = new NamespaceResponseEncoder(aclResponseEncoder);
        final StatusResponseEncoder statusResponseEncoder = new StatusResponseEncoder(namespaceEncoder, localizer);
        final RecentResponseEncoder recentResponseEncoder = new RecentResponseEncoder(statusResponseEncoder);
        final FetchResponseEncoder fetchResponseEncoder = new FetchResponseEncoder(recentResponseEncoder, neverAddBodyStructureExtensions);
        final ExpungeResponseEncoder expungeResponseEncoder = new ExpungeResponseEncoder(fetchResponseEncoder);
        final ExistsResponseEncoder existsResponseEncoder = new ExistsResponseEncoder(expungeResponseEncoder);
        final MailboxStatusResponseEncoder statusCommandResponseEncoder = new MailboxStatusResponseEncoder(existsResponseEncoder);
        final SearchResponseEncoder searchResponseEncoder = new SearchResponseEncoder(statusCommandResponseEncoder);
        final LSubResponseEncoder lsubResponseEncoder = new LSubResponseEncoder(searchResponseEncoder);
        final ListResponseEncoder listResponseEncoder = new ListResponseEncoder(lsubResponseEncoder);
        final XListResponseEncoder xListResponseEncoder = new XListResponseEncoder(listResponseEncoder);
        final FlagsResponseEncoder flagsResponseEncoder = new FlagsResponseEncoder(xListResponseEncoder);
        final CapabilityResponseEncoder capabilityResponseEncoder = new CapabilityResponseEncoder(flagsResponseEncoder);
        final EnableResponseEncoder enableResponseEncoder = new EnableResponseEncoder(capabilityResponseEncoder);
        final ContinuationResponseEncoder continuationResponseEncoder = new ContinuationResponseEncoder(enableResponseEncoder, localizer);
        final AuthenticateResponseEncoder authResponseEncoder = new AuthenticateResponseEncoder(continuationResponseEncoder);
        final ESearchResponseEncoder esearchResponseEncoder = new ESearchResponseEncoder(authResponseEncoder);
        final VanishedResponseEncoder vanishedResponseEncoder = new VanishedResponseEncoder(esearchResponseEncoder);
        final QuotaResponseEncoder quotaResponseEncoder = new QuotaResponseEncoder(vanishedResponseEncoder);
        final QuotaRootResponseEncoder quotaRootResponseEncoder = new QuotaRootResponseEncoder(quotaResponseEncoder);
        return quotaRootResponseEncoder;
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
        super();
        this.localizer = localizer;
        this.neverAddBodyStructureExtensions = neverAddBodyStructureExtensions;
    }

    @Override
    public ImapEncoder buildImapEncoder() {
        return createDefaultEncoder(localizer, neverAddBodyStructureExtensions);
    }

}
