package org.apache.james.sieverepository.lib;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.User;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.SieveRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.URL;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class SieveRepositoryManagementTest {

    @Mock
    private SieveRepository sieveRepository;

    private SieveRepositoryManagement sieveRepositoryManagement;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        sieveRepositoryManagement = new SieveRepositoryManagement();
        sieveRepositoryManagement.setSieveRepository(sieveRepository);
    }


    @Test
    public void importSieveScriptFileToRepositoryShouldStoreContentAndActivateScript() throws Exception {
        String userName = "user@domain";
        String script = "user_script";
        URL sieveResource = ClassLoader.getSystemResource("sieve/my_sieve");

        User user = User.fromUsername(userName);
        ScriptName scriptName = new ScriptName(script);
        String sieveContent = IOUtils.toString(sieveResource);
        ScriptContent scriptContent = new ScriptContent(sieveContent);

        sieveRepositoryManagement.addActiveSieveScriptFromFile(userName, script, sieveResource.getFile());

        verify(sieveRepository, times(1)).putScript(user, scriptName, scriptContent);
        verify(sieveRepository, times(1)).setActive(user, scriptName);
    }

    @Test
    public void importSieveScriptFileToRepositoryShouldNotImportFileWithWrongPathToRepistory() throws Exception {
        String userName = "user@domain";
        String script = "user_script";
        URL sieveResource = ClassLoader.getSystemResource("sieve/my_sieve");

        User user = User.fromUsername(userName);
        ScriptName scriptName = new ScriptName(script);
        String sieveContent = IOUtils.toString(sieveResource);
        ScriptContent scriptContent = new ScriptContent(sieveContent);

        sieveRepositoryManagement.addActiveSieveScriptFromFile(userName, script, "wrong_path/" + sieveResource.getFile());
        verify(sieveRepository, times(0)).putScript(user, scriptName, scriptContent);
        verify(sieveRepository, times(0)).setActive(user, scriptName);
    }
}