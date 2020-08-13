

package org.apache.james.sieverepository.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.lib.SieveRepositoryContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

class SieveFileRepositoryTest implements SieveRepositoryContract {

    static final String SIEVE_ROOT = FileSystem.FILE_PROTOCOL + "sieve";

    FileSystem fileSystem;
    SieveRepository sieveRepository;

    @BeforeEach
    void setUp() throws Exception {
        this.fileSystem = new FileSystem() {
            @Override
            public File getBasedir() {
                return new File(System.getProperty("java.io.tmpdir"));
            }
            
            @Override
            public InputStream getResource(String url) throws IOException {
                return new FileInputStream(getFile(url));
            }
            
            @Override
            public File getFile(String fileURL) {
                return new File(getBasedir(), fileURL.substring(FileSystem.FILE_PROTOCOL.length()));
            }
        };
        sieveRepository = new SieveFileRepository(fileSystem);
    }

    @AfterEach
    void tearDown() throws Exception {
        File root = fileSystem.getFile(SIEVE_ROOT);
        // Remove files from the previous test, if any
        if (root.exists()) {
            FileUtils.forceDelete(root);
        }
    }

    @Override
    public SieveRepository sieveRepository() {
        return sieveRepository;
    }
}
