package org.apache.james.wkd.mailet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import javax.mail.MessagingException;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.wkd.crypto.WebKeyDirectorySubmissionAddressKeyPairManager;
import org.bouncycastle.openpgp.PGPException;
import org.junit.jupiter.api.Test;

class WebKeyDirectoryMailetTest {

    @Test
    void testDecryptBodyPartWithPublicKey()
        throws FileNotFoundException, IOException, MessagingException, PGPException {
        FileSystem fileSystem = new FileSystem() {

            @Override
            public InputStream getResource(String url) throws IOException {
                return null;
            }

            @Override
            public File getFile(String fileURL) throws FileNotFoundException {
                try {
                    return new File(getClass().getResource("/"+fileURL).toURI());
                } catch (URISyntaxException e) {
                    return null;
                }
            }

            @Override
            public File getBasedir() throws FileNotFoundException {
                return null;
            }
        };
        Configuration configuration = Configuration.builder().workingDirectory("")
            .configurationPath("").build();
        WebKeyDirectorySubmissionAddressKeyPairManager webKeyDirectorySubmissionAddressKeyPairManager = new WebKeyDirectorySubmissionAddressKeyPairManager(
            null, fileSystem, configuration);
        WebKeyDirectoryMailet webKeyDirectoryMailet = new WebKeyDirectoryMailet(null, null,
            webKeyDirectorySubmissionAddressKeyPairManager);
        webKeyDirectoryMailet.decryptBodyPartWithPublicKey(
            new FileInputStream("src/test/resources/submitted-public-key.pub"));
    }

}
