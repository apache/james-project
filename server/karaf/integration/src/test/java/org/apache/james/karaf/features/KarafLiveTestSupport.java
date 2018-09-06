package org.apache.james.karaf.features;

import static org.apache.karaf.tooling.exam.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.apache.karaf.tooling.exam.options.KarafDistributionOption.keepRuntimeFolder;
import static org.apache.karaf.tooling.exam.options.KarafDistributionOption.logLevel;
import static org.assertj.core.api.Fail.fail;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.io.File;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeaturesService;
import org.apache.karaf.tooling.exam.options.KarafDistributionConfigurationFilePutOption;
import org.apache.karaf.tooling.exam.options.LogLevelOption;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.MavenUtils;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.ExamReactorStrategy;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.spi.reactors.EagerSingleStagedReactorFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

/**
 * Base class for integration testing with Karaf.
 */
@RunWith(JUnit4TestRunner.class)
@ExamReactorStrategy(EagerSingleStagedReactorFactory.class)
public class KarafLiveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KarafLiveTestSupport.class);
    public static final int WAIT_30_SECONDS = 30000;

    private static final String DISTRIBUTION_GROUP_ID = "org.apache.james.karaf";
    private static final String DISTRIBUTION_ARTIFACT_ID = "james-karaf-distribution";

    @Inject
    FeaturesService features;

    @Inject
    BundleContext bundleContext;

    String featuresVersion;

    @Configuration
    public static Option[] configuration() throws Exception {

        MavenArtifactUrlReference karafUrl = maven().groupId(DISTRIBUTION_GROUP_ID)
                .artifactId(DISTRIBUTION_ARTIFACT_ID)
                .versionAsInProject()
                .type("tar.gz");

        String jamesFeaturesVersion = MavenUtils.getArtifactVersion("org.apache.james.karaf", "james-karaf-features");

        return new Option[]{
                karafDistributionConfiguration()
                        .frameworkUrl(karafUrl)
                        .karafVersion(getDistributionVersionAsInProject())
                        .name("Apache Karaf")
                        .unpackDirectory(new File("target/exam")),
                logLevel(LogLevelOption.LogLevel.INFO),
                new KarafDistributionConfigurationFilePutOption("etc/custom.properties",
                        "org.osgi.framework.system.packages.extra",
                        "sun.net.spi.nameservice"),
                keepRuntimeFolder(),
                new MavenArtifactProvisionOption().groupId("com.google.guava").artifactId("guava").versionAsInProject(),
                // use system property to provide project version for tests
                systemProperty("james-karaf-features").value(jamesFeaturesVersion)
        };
    }

    public static String getDistributionVersionAsInProject() {
        return MavenUtils.asInProject().getVersion(DISTRIBUTION_GROUP_ID, DISTRIBUTION_ARTIFACT_ID);
    }

    @Before
    public void setUp() {
        featuresVersion = System.getProperty("james-karaf-features");
    }

    void assertInstalled(String featureName) throws Exception {
        Feature feature = features.getFeature(featureName);
        assertTrue("Feature " + featureName + " should be installed", features.isInstalled(feature));
    }

    void assertBundlesAreActive() {
        for (Bundle bundle : bundleContext.getBundles()) {
            LOG.info("***** bundle {} is {}", bundle.getSymbolicName(), bundle.getState());
            assertThat(bundle.getState()).describedAs("Bundle " + bundle.getSymbolicName() + " is not active").isEqualTo(Bundle.ACTIVE);
        }
    }

    void addJamesFeaturesRepository() throws Exception {
        String url = maven("org.apache.james.karaf", "james-karaf-features")
                .version(featuresVersion)
                .classifier("features")
                .type("xml")
                .getURL();

        features.addRepository(new URI(url));
        features.installFeature("spring-dm");
        features.installFeature("war");
    }

    void assertOSGiServiceStartsIn(Class clazz, int timeoutInMilliseconds) throws InterruptedException {
        final ServiceTracker tracker = new ServiceTracker(bundleContext, clazz, null);
        tracker.open(true);
        try {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            final int expectedCount = 1;

            while (true) {
                Object[] services = tracker.getServices();
                if (services == null || services.length < expectedCount) {
                    final int actualCount = (services == null) ? 0 : services.length;
                    if (stopwatch.elapsed(TimeUnit.MILLISECONDS) > timeoutInMilliseconds) {
                        fail(String.format("Expected to find %d services of type %s. Found only %d in %d milliseconds",
                                expectedCount, clazz.getCanonicalName(), actualCount, timeoutInMilliseconds));
                    }

                    LOG.info("Found {} services implementing {}. Trying again in 1s.",
                            actualCount, clazz.getCanonicalName());
                    TimeUnit.SECONDS.sleep(1);

                } else if (services.length > expectedCount) {
                    fail(String.format("Expected to find %d services implementing %s. Found %d (more than expected).",
                            expectedCount, clazz.getCanonicalName(), services.length));

                } else if (services.length == expectedCount) {
                    break;  /* done - the test was successful */
                }
            }

        } finally {
            tracker.close();
        }
    }
}
