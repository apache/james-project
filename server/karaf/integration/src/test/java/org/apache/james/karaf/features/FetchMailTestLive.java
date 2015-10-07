package org.apache.james.karaf.features;

import org.apache.james.fetchmail.FetchScheduler;
import org.junit.Test;

public class FetchMailTestLive extends KarafLiveTestSupport {
    @Test
    public void testInstallJamesFetchMailFeature() throws Exception {
        addJamesFeaturesRepository();
        features.installFeature("james-server-fetchmail");
        assertInstalled("james-server-fetchmail");
        assertBundlesAreActive();
        assertOSGiServiceStartsIn(FetchScheduler.class, WAIT_30_SECONDS);
    }
}
