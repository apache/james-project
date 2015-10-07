package org.apache.james.karaf.features;

import org.junit.Test;

public class CommonsConfigurationTestLive extends KarafLiveTestSupport {

    @Test
    public void testInstallCommonsConfigurationFeature() throws Exception {
        addJamesFeaturesRepository();
        features.installFeature("commons-configuration");
        assertInstalled("commons-configuration");
        assertBundlesAreActive();
    }
}
