package org.apache.james.karaf.features;

import org.apache.james.dnsservice.api.DNSService;
import org.junit.Test;

public class DnsServiceTestLive extends KarafLiveTestSupport {

    @Test
    public void testInstallJamesDnsServiceDnsJava() throws Exception {
        addJamesFeaturesRepository();
        features.installFeature("james-server-dnsservice-dnsjava");
        assertInstalled("james-server-dnsservice-dnsjava");
        assertBundlesAreActive();
        assertOSGiServiceStartsIn(DNSService.class, WAIT_30_SECONDS);
    }
}
