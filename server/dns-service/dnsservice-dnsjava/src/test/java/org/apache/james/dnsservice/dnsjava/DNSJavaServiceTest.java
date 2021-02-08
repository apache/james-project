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
package org.apache.james.dnsservice.dnsjava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Cache;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.Zone;

import com.google.common.io.Resources;

class DNSJavaServiceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DNSJavaServiceTest.class);

    private TestableDNSServer dnsServer;
    private static final byte[] DNS_SERVER_CONFIG = ("<dnsserver>" +
            "<autodiscover>true</autodiscover>" +
            "<authoritative>false</authoritative>" +
            "</dnsserver>").getBytes();

    private Cache defaultCache;
    private Resolver defaultResolver;
    private List<Name> defaultSearchPaths;

    private Cache mockedCache;

    @BeforeEach
    void setUp() throws Exception {
        dnsServer = new TestableDNSServer();

        dnsServer.configure(FileConfigurationProvider.getConfig(new ByteArrayInputStream(DNS_SERVER_CONFIG)));
        dnsServer.init();

        defaultCache = Lookup.getDefaultCache(DClass.IN);
        defaultResolver = Lookup.getDefaultResolver();
        defaultSearchPaths = Lookup.getDefaultSearchPath();
        Lookup.setDefaultCache(null, DClass.IN);
        Lookup.setDefaultResolver(null);
        Lookup.setDefaultSearchPath(new Name[]{});

        dnsServer.setResolver(null);
        mockedCache = mock(Cache.class);
    }

    @AfterEach
    void tearDown() {
        dnsServer.setCache(null);
        dnsServer = null;
        Lookup.setDefaultCache(defaultCache, DClass.IN);
        Lookup.setDefaultResolver(defaultResolver);
        Lookup.setDefaultSearchPath(defaultSearchPaths);
    }

    @Test
    void testNoMX() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("dnstest.com.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // a.setSearchPath(new String[] { "searchdomain.com." });
        Collection<String> records = dnsServer.findMXRecords("nomx.dnstest.com.");
        assertThat(records.size()).isEqualTo(1);
        assertThat(records.iterator().next()).isEqualTo("nomx.dnstest.com.");
    }

    @Test
    void testBadMX() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("dnstest.com.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // a.setSearchPath(new String[] { "searchdomain.com." });
        Collection<String> records = dnsServer.findMXRecords("badmx.dnstest.com.");
        assertThat(records.size()).isEqualTo(1);
        assertThat(records.iterator().next()).isEqualTo("badhost.dnstest.com.");
        // Iterator<HostAddress> it =
        // dnsServer.getSMTPHostAddresses("badmx.dnstest.com.");
        // assertFalse(it.hasNext());
    }

    @Test
    void testINARecords() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("pippo.com.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // dnsServer.setLookupper(new ZoneLookupper(z));
        Collection<String> records = dnsServer.findMXRecords("www.pippo.com.");
        assertThat(records.size()).isEqualTo(1);
        assertThat(records.iterator().next()).isEqualTo("pippo.com.inbound.mxlogic.net.");
    }

    @Test
    void testMXCatches() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("test-zone.com.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // dnsServer.setLookupper(new ZoneLookupper(z));
        Collection<String> res = dnsServer.findMXRecords("test-zone.com.");
        try {
            res.add("");
            fail("MX Collection should not be modifiable");
        } catch (UnsupportedOperationException e) {
            LOGGER.info("Ignored error", e);
        }
        assertThat(res.size()).isEqualTo(1);
        assertThat(res.iterator().next()).isEqualTo("mail.test-zone.com.");
    }

    /**
     * Test for JAMES-1251
     */
    @Test
    void testTwoMXSamePrio() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("two-mx.sameprio.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // a.setSearchPath(new String[] { "searchdomain.com." });
        Collection<String> records = dnsServer.findMXRecords("two-mx.sameprio.");
        assertThat(records.size()).isEqualTo(2);
        assertThat(records.contains("mx1.two-mx.sameprio.")).isTrue();
        assertThat(records.contains("mx2.two-mx.sameprio.")).isTrue();
    }

    @Test
    void testThreeMX() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("three-mx.bar.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // a.setSearchPath(new String[] { "searchdomain.com." });
        ArrayList<String> records = new ArrayList<>(dnsServer.findMXRecords("three-mx.bar."));
        assertThat(records.size()).isEqualTo(3);
        assertThat(records.contains("mx1.three-mx.bar.")).isTrue();
        assertThat(records.contains("mx2.three-mx.bar.")).isTrue();
        assertThat(records.get(2)).isEqualTo("mx3.three-mx.bar.");

    }

    /**
     * Test for JAMES-1251
     */
    @Test
    void testTwoMXDifferentPrio() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("two-mx.differentprio.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);
        // a.setSearchPath(new String[] { "searchdomain.com." });
        Collection<String> records = dnsServer.findMXRecords("two-mx.differentprio.");
        assertThat(records.size()).isEqualTo(2);
        assertThat(records.contains("mx1.two-mx.differentprio.")).isTrue();
        assertThat(records.contains("mx2.two-mx.differentprio.")).isTrue();

    }

    /**
     * Test for JAMES-1251
     */
    @Test
    void testOneMX() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("one-mx.bar.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // a.setSearchPath(new String[] { "searchdomain.com." });
        Collection<String> records = dnsServer.findMXRecords("one-mx.bar.");
        assertThat(records.size()).isEqualTo(1);
        assertThat(records.contains("mx1.one-mx.bar.")).isTrue();
    }
    /*
     * public void testCNAMEasMXrecords() throws Exception { // Zone z =
     * loadZone("brandilyncollins.com."); dnsServer.setResolver(null);
     * dnsServer.setCache(new ZoneCache("brandilyncollins.com.")); //
     * dnsServer.setLookupper(new ZoneLookupper(z)); //Iterator<HostAddress>
     * records = dnsServer.getSMTPHostAddresses("brandilyncollins.com.");
     * //assertEquals(true, records.hasNext()); }
     */

    private static Zone loadZone(String zoneName) throws IOException {
        String zoneFilename = zoneName + "zone";
        URL zoneResource = Resources.getResource(DNSJavaServiceTest.class, zoneFilename);
        assertThat(zoneResource).withFailMessage("test resource for zone could not be loaded: " + zoneFilename).isNotNull();
        return new Zone(Name.fromString(zoneName), zoneResource.getFile());
    }

    private final class TestableDNSServer extends DNSJavaService {

        public TestableDNSServer() {
            super(new RecordingMetricFactory());
        }

        public void setResolver(Resolver r) {
            resolver = r;
        }

        public void setCache(Cache c) {
            cache = c;
        }
    }
}
