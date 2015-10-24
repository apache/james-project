

package org.apache.james.sieverepository.file;

import org.apache.commons.io.FileUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.lib.AbstractSieveRepositoryTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class SieveFileRepositoryTest extends AbstractSieveRepositoryTest {

    private static final String SIEVE_ROOT = FileSystem.FILE_PROTOCOL + "sieve";

    private final FileSystem fileSystem;

    public SieveFileRepositoryTest() {
        this.fileSystem = new FileSystem() {
            public File getBasedir() throws FileNotFoundException {
                return new File(System.getProperty("java.io.tmpdir"));
            }
            public InputStream getResource(String url) throws IOException {
                return new FileInputStream(getFile(url));
            }
            public File getFile(String fileURL) throws FileNotFoundException {
                return new File(getBasedir(), fileURL.substring(FileSystem.FILE_PROTOCOL.length()));
            }
        };
    }

    @Override
    protected SieveRepository createSieveRepository() throws Exception {
        File root = fileSystem.getFile(SIEVE_ROOT);
        FileUtils.forceMkdir(root);
        return new SieveFileRepository(fileSystem);
    }

    @Override
    protected void cleanUp() throws Exception {
        File root = fileSystem.getFile(SIEVE_ROOT);
        // Remove files from the previous test, if any
        if (root.exists()) {
            FileUtils.forceDelete(root);
        }
    }
}
