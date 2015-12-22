package org.apache.james.mpt.managesieve.file.host;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mpt.host.JamesManageSieveHostSystem;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.file.SieveFileRepository;
import org.apache.james.user.memory.MemoryUsersRepository;

public class FileHostSystem extends JamesManageSieveHostSystem {

    private static final String SIEVE_ROOT = FileSystem.FILE_PROTOCOL + "sieve";
    private static final FileSystem fileSystem = getFileSystem();

    private static FileSystem getFileSystem() {
        return new FileSystem() {
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

    protected static SieveRepository createSieveRepository() throws Exception {
        File root = getFileSystem().getFile(SIEVE_ROOT);
        FileUtils.forceMkdir(root);
        return new SieveFileRepository(fileSystem);
    }

    public FileHostSystem() throws Exception {
        super(new MemoryUsersRepository(), createSieveRepository());
    }

    @Override
    protected void resetData() throws Exception {
        File root = fileSystem.getFile(SIEVE_ROOT);
        // Remove files from the previous test, if any
        if (root.exists()) {
            FileUtils.forceDelete(root);
        }
    }
}
