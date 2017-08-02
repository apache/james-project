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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Cache;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.Zone;

import com.google.common.io.Resources;

public class DNSJavaServiceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DNSJavaServiceTest.class);

    private TestableDNSServer dnsServer;
    private static final byte[] DNS_SERVER_CONFIG = ("<dnsserver>" +
            "<autodiscover>true</autodiscover>" +
            "<authoritative>false</authoritative>" +
            "</dnsserver>").getBytes();

    private Cache defaultCache;
    private Resolver defaultResolver;
    private Name[] defaultSearchPaths;

    private Cache mockedCache;

    @Before
    public void setUp() throws Exception {
        dnsServer = new TestableDNSServer();
        DefaultConfigurationBuilder db = new DefaultConfigurationBuilder();

        db.load(new ByteArrayInputStream(DNS_SERVER_CONFIG));

        dnsServer.setLog(LoggerFactory.getLogger(DNSJavaServiceTest.class));
        dnsServer.configure(db);
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

    @After
    public void tearDown() throws Exception {
        dnsServer.setCache(null);
        dnsServer = null;
        Lookup.setDefaultCache(defaultCache, DClass.IN);
        Lookup.setDefaultResolver(defaultResolver);
        Lookup.setDefaultSearchPath(defaultSearchPaths);
    }

    @Test
    public void testNoMX() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("dnstest.com.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // a.setSearchPath(new String[] { "searchdomain.com." });
        Collection<String> records = dnsServer.findMXRecords("nomx.dnstest.com.");
        assertEquals(1, records.size());
        assertEquals("nomx.dnstest.com.", records.iterator().next());
    }

    @Test
    public void testBadMX() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("dnstest.com.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // a.setSearchPath(new String[] { "searchdomain.com." });
        Collection<String> records = dnsServer.findMXRecords("badmx.dnstest.com.");
        assertEquals(1, records.size());
        assertEquals("badhost.dnstest.com.", records.iterator().next());
        // Iterator<HostAddress> it =
        // dnsServer.getSMTPHostAddresses("badmx.dnstest.com.");
        // assertFalse(it.hasNext());
    }

    @Test
    public void testINARecords() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("pippo.com.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // dnsServer.setLookupper(new ZoneLookupper(z));
        Collection<String> records = dnsServer.findMXRecords("www.pippo.com.");
        assertEquals(1, records.size());
        assertEquals("pippo.com.inbound.mxlogic.net.", records.iterator().next());
    }

    @Test
    public void testMXCatches() throws Exception {
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
        assertEquals(1, res.size());
        assertEquals("mail.test-zone.com.", res.iterator().next());
    }

    /**
     * Test for JAMES-1251
     */
    @Test
    public void testTwoMXSamePrio() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("two-mx.sameprio.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // a.setSearchPath(new String[] { "searchdomain.com." });
        Collection<String> records = dnsServer.findMXRecords("two-mx.sameprio.");
        assertEquals(2, records.size());
        assertTrue(records.contains("mx1.two-mx.sameprio."));
        assertTrue(records.contains("mx2.two-mx.sameprio."));
    }

    @Test
    public void testThreeMX() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("three-mx.bar.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // a.setSearchPath(new String[] { "searchdomain.com." });
        ArrayList<String> records = new ArrayList<>(dnsServer.findMXRecords("three-mx.bar."));
        assertEquals(3, records.size());
        assertTrue(records.contains("mx1.three-mx.bar."));
        assertTrue(records.contains("mx2.three-mx.bar."));
        assertEquals("mx3.three-mx.bar.", records.get(2));

    }

    /**
     * Test for JAMES-1251
     */
    @Test
    public void testTwoMXDifferentPrio() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("two-mx.differentprio.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);
        // a.setSearchPath(new String[] { "searchdomain.com." });
        Collection<String> records = dnsServer.findMXRecords("two-mx.differentprio.");
        assertEquals(2, records.size());
        assertTrue(records.contains("mx1.two-mx.differentprio."));
        assertTrue(records.contains("mx2.two-mx.differentprio."));

    }

    /**
     * Test for JAMES-1251
     */
    @Test
    public void testOneMX() throws Exception {
        doAnswer(new ZoneCacheLookupRecordsAnswer(loadZone("one-mx.bar.")))
                .when(mockedCache).lookupRecords(any(Name.class), anyInt(), anyInt());
        dnsServer.setCache(mockedCache);

        // a.setSearchPath(new String[] { "searchdomain.com." });
        Collection<String> records = dnsServer.findMXRecords("one-mx.bar.");
        assertEquals(1, records.size());
        assertTrue(records.contains("mx1.one-mx.bar."));
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
        assertNotNull("test resource for zone could not be loaded: " + zoneFilename, zoneResource);
        return new Zone(Name.fromString(zoneName), zoneResource.getFile());
    }

    private final class TestableDNSServer extends DNSJavaService {

        public TestableDNSServer() {
            super(new NoopMetricFactory());
        }

        public void setResolver(Resolver r) {
            resolver = r;
        }

        public void setCache(Cache c) {
            cache = c;
        }
    }
}
