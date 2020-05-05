package org.apache.james.wkd.store;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import javax.inject.Inject;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.wkd.crypto.WebKeyDirectorySubmissionAddressKeyPairManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWebKeyDirectoryStore extends AbstractWebKeyDirectoryStore {

    private static Logger LOGGER = LoggerFactory
        .getLogger(FileWebKeyDirectoryStore.class.getName());

    private static final String WEB_KEY_DIRECTORY = "/web-key-directory";
    private boolean insertSubmissionKey = false;
    private WebKeyDirectorySubmissionAddressKeyPairManager webKeyDirectorySubmissionAddressKeyPairManager;
    private File webKeyDirectory;

    @Inject
    public FileWebKeyDirectoryStore(
        WebKeyDirectorySubmissionAddressKeyPairManager webKeyDirectorySubmissionAddressKeyPairManager,
        FileSystem fileSystem) {
        this.webKeyDirectorySubmissionAddressKeyPairManager = webKeyDirectorySubmissionAddressKeyPairManager;
        try {
            webKeyDirectory = fileSystem.getFile(WEB_KEY_DIRECTORY);
        } catch (FileNotFoundException e) {
            try {
                webKeyDirectory = new File(fileSystem.getBasedir() + WEB_KEY_DIRECTORY);
                webKeyDirectory.mkdir();
            } catch (FileNotFoundException e1) {
                LOGGER.error("Could not create webKeyDirectory", e1);
            }
        }
    }

    @Override
    public void put(PublicKeyEntry publicKeyEntry) {
        try {
            Files.write(
                Paths.get(webKeyDirectory.getAbsolutePath() + "/" + publicKeyEntry.getHash()),
                publicKeyEntry.getPublicKey(), StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Could not save public key", e);
        }
    }

    @Override
    public PublicKeyEntry get(String hash) {
        checkSubmissionKey();
        PublicKeyEntry publicKeyEntry = new PublicKeyEntry();
        publicKeyEntry.setHash(hash);
        try {
            publicKeyEntry.setPublicKey(Files.readAllBytes(
                Paths.get(webKeyDirectory.getAbsolutePath() + "/" + publicKeyEntry.getHash())));
            return publicKeyEntry;
        } catch (IOException e) {
            LOGGER.error("Could not read key: " + hash, e);
            return null;
        }
    }

    /**
     * 
     */
    private synchronized void checkSubmissionKey() {
        if (!insertSubmissionKey) {
            put(webKeyDirectorySubmissionAddressKeyPairManager
                .getPublicKeyEntryForSubmissionAddress());
            insertSubmissionKey = true;
        }
    }

    @Override
    public boolean containsKey(String hash) {
        checkSubmissionKey();
        return Files.exists(Paths.get(webKeyDirectory.getAbsolutePath() + "/" + hash));
    }

}
