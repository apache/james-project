

package org.apache.james.sieverepository.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.james.core.Username;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.sieverepository.lib.SieveRepositoryContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void putScriptShouldThrowOnCraftedUsername() {
        assertThatThrownBy(() -> sieveRepository().putScript(Username.of("../../home/interview1/test"), SCRIPT_NAME, SCRIPT_CONTENT))
            .isInstanceOf(StorageException.class);
    }

    @Test
    void putScriptShouldThrowOnCraftedScriptName() {
        assertThatThrownBy(() ->  sieveRepository().putScript(Username.of("test"),
                new ScriptName("../../../../home/interview1/script"), SCRIPT_CONTENT))
            .isInstanceOf(StorageException.class);
    }

    @Test
    void getScriptShouldNotAllowToReadScriptsOfOtherUsers() throws Exception {
        sieveRepository().putScript(Username.of("other"), new ScriptName("script"), new ScriptContent("PWND!!!"));

        assertThatThrownBy(() ->  sieveRepository().getScript(Username.of("test"),
                new ScriptName("../other/script")))
            .isInstanceOf(StorageException.class);
    }

    @Test
    void getScriptShouldNotAllowToReadScriptsOfOtherUsersWhenPrefix() throws Exception {
        sieveRepository().putScript(Username.of("testa"), new ScriptName("script"), new ScriptContent("PWND!!!"));

        assertThatThrownBy(() ->  sieveRepository().getScript(Username.of("test"),
            new ScriptName("../other/script")))
            .isInstanceOf(StorageException.class);
    }

    @Test
    void putScriptShouldNotAllowToWriteScriptsOfOtherUsers() throws Exception {
        sieveRepository().putScript(Username.of("victim"), new ScriptName("script"), SCRIPT_CONTENT);

        assertThatThrownBy(() -> sieveRepository().putScript(Username.of("attacker"),
                new ScriptName("../victim/script"), new ScriptContent("PWND!!!")))
            .isInstanceOf(StorageException.class);

        assertThat(sieveRepository().getScript(Username.of("victim"), new ScriptName("script")))
            .hasContent(SCRIPT_CONTENT.getValue());
    }

    @Test
    void putScriptShouldNotAllowToOverwriteTheActiveMarkerOfOtherUsers() throws Exception {
        sieveRepository().putScript(Username.of("victim"), new ScriptName("script"), SCRIPT_CONTENT);

        assertThatThrownBy(() -> sieveRepository().putScript(Username.of("attacker"),
                new ScriptName("../victim/.active"), new ScriptContent("script")))
            .isInstanceOf(StorageException.class);

        assertThat(new File(fileSystem.getFile(SIEVE_ROOT), "victim/.active")).doesNotExist();
    }

    @Test
    void putScriptShouldNotAllowToOverwriteSystemFiles() {
        assertThatThrownBy(() -> sieveRepository().putScript(Username.of("test"),
                new ScriptName(".active"), SCRIPT_CONTENT))
            .isInstanceOf(StorageException.class);

        assertThatThrownBy(() -> sieveRepository().putScript(Username.of("test"),
                new ScriptName(".quota"), SCRIPT_CONTENT))
            .isInstanceOf(StorageException.class);
    }

    @Test
    void putScriptShouldNotAllowToOverwriteTheGlobalQuotaFile() throws Exception {
        assertThatThrownBy(() -> sieveRepository().putScript(Username.of("test"),
                new ScriptName("../.quota"), new ScriptContent("1")))
            .isInstanceOf(StorageException.class);

        assertThat(new File(fileSystem.getFile(SIEVE_ROOT), ".quota")).doesNotExist();
    }

    @Test
    void renameScriptShouldNotAllowToWriteScriptsOfOtherUsers() throws Exception {
        Username attacker = Username.of("attacker");
        sieveRepository().putScript(Username.of("victim"), new ScriptName("script"), SCRIPT_CONTENT);
        sieveRepository().putScript(attacker, new ScriptName("evil"), new ScriptContent("PWND!!!"));
        sieveRepository().setActive(attacker, new ScriptName("evil"));

        assertThatThrownBy(() -> sieveRepository().renameScript(attacker,
                new ScriptName("evil"), new ScriptName("../victim/evil")))
            .isInstanceOf(StorageException.class);

        assertThat(new File(fileSystem.getFile(SIEVE_ROOT), "victim/evil")).doesNotExist();
        assertThat(new File(fileSystem.getFile(SIEVE_ROOT), "victim/.active")).doesNotExist();
    }

    @Test
    void getActiveShouldNotFollowACraftedActiveMarker() throws Exception {
        sieveRepository().putScript(Username.of("other"), new ScriptName("script"), new ScriptContent("PWND!!!"));
        sieveRepository().putScript(Username.of("test"), new ScriptName("script"), SCRIPT_CONTENT);
        FileUtils.write(new File(fileSystem.getFile(SIEVE_ROOT), "test/.active"),
            "../other/script", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> sieveRepository().getActive(Username.of("test")))
            .isInstanceOf(StorageException.class);
    }
}
