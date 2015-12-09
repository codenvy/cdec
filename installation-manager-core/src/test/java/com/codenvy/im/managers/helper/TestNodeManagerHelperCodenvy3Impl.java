/*
 * CODENVY CONFIDENTIAL
 * __________________
 *
 *  [2012] - [2015] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.im.managers.helper;

import com.codenvy.im.BaseTest;
import com.codenvy.im.artifacts.CDECArtifact;
import com.codenvy.im.commands.Command;
import com.codenvy.im.commands.MacroCommand;
import com.codenvy.im.managers.Config;
import com.codenvy.im.managers.ConfigManager;
import com.codenvy.im.managers.InstallType;
import com.codenvy.im.managers.NodeConfig;
import com.codenvy.im.managers.NodeException;
import com.google.common.collect.ImmutableList;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/** @author Dmytro Nochevnov */
public class TestNodeManagerHelperCodenvy3Impl extends BaseTest {
    public static final String SYSTEM_USER_NAME = System.getProperty("user.name");

    @Mock
    private ConfigManager                       mockConfigManager;
    @Mock
    private AdditionalNodesConfigHelperCodenvy3 mockNodesConfigUtil;
    @Mock
    private CDECArtifact                        mockCdecArtifact;
    @Mock
    private Command                             mockCommand;

    private static final String              TEST_NODE_DNS  = "localhost";
    private static final NodeConfig.NodeType TEST_NODE_TYPE = NodeConfig.NodeType.RUNNER;
    private static final NodeConfig          TEST_NODE      = new NodeConfig(TEST_NODE_TYPE, TEST_NODE_DNS, null);

    private static final String  TEST_RUNNER_NODE_URL             = "test_runner_node_url";
    private static final String  ADDITIONAL_RUNNERS_PROPERTY_NAME = "additional_runners";

    private NodeManagerHelperCodenvy3Impl spyHelperCodenvy3;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        spyHelperCodenvy3 = spy(new NodeManagerHelperCodenvy3Impl(mockConfigManager, mockCdecArtifact));

        doReturn(ImmutableList.of(Paths.get("/etc/puppet/" + Config.MULTI_SERVER_CUSTOM_CONFIG_PP),
                                  Paths.get("/etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP)).iterator())
            .when(mockConfigManager).getCodenvyPropertiesFiles(InstallType.MULTI_SERVER);
        doReturn(ImmutableList.of(Paths.get("/etc/puppet/" + Config.SINGLE_SERVER_BASE_CONFIG_PP),
                                  Paths.get("/etc/puppet/" + Config.SINGLE_SERVER_PP)).iterator())
            .when(mockConfigManager).getCodenvyPropertiesFiles(InstallType.SINGLE_SERVER);

        doReturn(mockNodesConfigUtil).when(spyHelperCodenvy3).getNodesConfigHelper(any(Config.class));

        initConfigs();
    }

    private void initConfigs() throws IOException {
        doReturn(ADDITIONAL_RUNNERS_PROPERTY_NAME).when(spyHelperCodenvy3).getPropertyNameBy(TEST_NODE_TYPE);
        doReturn(TEST_RUNNER_NODE_URL).when(mockNodesConfigUtil).getValueWithNode(TEST_NODE);
    }

    @Test
    public void testGetAddNodeCommand() throws Exception {
        prepareMultiNodeEnv(mockConfigManager);
        Command result = spyHelperCodenvy3.getAddNodeCommand(TEST_NODE, ADDITIONAL_RUNNERS_PROPERTY_NAME);
        assertNotNull(result);
        assertTrue(result instanceof MacroCommand);

        List<Command> commands = ((MacroCommand) result).getCommands();
        assertEquals(commands.size(), 17);

        assertTrue(commands.get(0).toString().matches("\\{'command'='sudo cp /etc/puppet/" + Config.MULTI_SERVER_CUSTOM_CONFIG_PP
                                                      + " /etc/puppet/" + Config.MULTI_SERVER_CUSTOM_CONFIG_PP + ".back ; "
                                                      + "sudo cp /etc/puppet/" + Config.MULTI_SERVER_CUSTOM_CONFIG_PP
                                                      + " /etc/puppet/" + Config.MULTI_SERVER_CUSTOM_CONFIG_PP + ".back.[0-9]+ ; "
                                                      + "', 'agent'='LocalAgent'\\}"));
        assertEquals(commands.get(1).toString(),
                     "{'command'='sudo cat /etc/puppet/" + Config.MULTI_SERVER_CUSTOM_CONFIG_PP + " " +
                     "| sed ':a;N;$!ba;s/\\n/~n/g' " +
                     "| sed 's|$additional_runners *= *\"[^\"]*\"|$additional_runners = \"test_runner_node_url\"|g' " +
                     "| sed 's|~n|\\n|g' > tmp.tmp " +
                     "&& sudo mv tmp.tmp /etc/puppet/manifests/nodes/multi_server/custom_configurations.pp', 'agent'='LocalAgent'}");
        assertTrue(commands.get(2).toString().matches("\\{'command'='sudo cp /etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP
                                                      + " /etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP + ".back ; "
                                                      + "sudo cp /etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP
                                                      + " /etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP + ".back.[0-9]+ ; "
                                                      + "', 'agent'='LocalAgent'\\}"));

        assertEquals(commands.get(3).toString(),
                     "{'command'='sudo cat /etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP + " " +
                     "| sed ':a;N;$!ba;s/\\n/~n/g' " +
                     "| sed 's|$additional_runners *= *\"[^\"]*\"|$additional_runners = \"test_runner_node_url\"|g' " +
                     "| sed 's|~n|\\n|g' > tmp.tmp " +
                     "&& sudo mv tmp.tmp /etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP + "', 'agent'='LocalAgent'}");

        assertEquals(commands.get(4).toString(),
                     "{'command'='sudo sh -c \"echo -e 'localhost' >> /etc/puppet/autosign.conf\"', 'agent'='LocalAgent'}");
        assertEquals(commands.get(5).toString(), format("{'command'='yum clean all', 'agent'='LocalAgent'}"));
        assertEquals(commands.get(6).toString(),
                     format("{'command'='if [ \"`yum list installed | grep puppetlabs-release`\" == \"\" ]; then sudo yum -y -q install null; fi', 'agent'='{'host'='localhost', 'port'='22', 'user'='%s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
        assertEquals(commands.get(7).toString(),
                     format("{'command'='sudo yum -y -q install null', 'agent'='{'host'='localhost', 'port'='22', 'user'='%1$s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
        assertEquals(commands.get(8).toString(),
                     format("{'command'='sudo systemctl enable puppet', 'agent'='{'host'='localhost', 'port'='22', 'user'='%1$s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
        assertTrue(commands.get(9).toString().matches(
                format("\\{'command'='sudo cp /etc/puppet/puppet.conf /etc/puppet/puppet.conf.back ; sudo cp /etc/puppet/puppet.conf /etc/puppet/puppet.conf.back.[0-9]+ ; ', 'agent'='\\{'host'='localhost', 'port'='22', 'user'='%1$s', 'identity'='\\[~/.ssh/id_rsa\\]'\\}'\\}",
                       SYSTEM_USER_NAME)),
                   commands.get(9).toString());

        assertEquals(commands.get(10).toString(),
                     format("{'command'='sudo sed -i 's/\\[main\\]/\\[main\\]\\n  server = null\\n  runinterval = 420\\n  configtimeout = 600\\n/g' /etc/puppet/puppet.conf', 'agent'='{'host'='localhost', 'port'='22', 'user'='%1$s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
        assertEquals(commands.get(11).toString(),
                     format("{'command'='sudo sed -i 's/\\[agent\\]/\\[agent\\]\\n  show_diff = true\\n  pluginsync = true\\n  report = true\\n  default_schedules = false\\n  certname = localhost\\n/g' /etc/puppet/puppet.conf', 'agent'='{'host'='localhost', 'port'='22', 'user'='%1$s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
        assertEquals(commands.get(12).toString(),
                     format("{'command'='sudo systemctl start puppet', 'agent'='{'host'='localhost', 'port'='22', 'user'='%1$s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
        assertEquals(commands.get(13).toString(),
                     format("{'command'='doneState=\"Installing\"; testFile=\"/home/codenvy/codenvy-tomcat/logs/catalina.out\"; while [ \"${doneState}\" != \"Installed\" ]; do     if sudo test -f ${testFile}; then doneState=\"Installed\"; fi;     sleep 30; done', 'agent'='{'host'='localhost', 'port'='22', 'user'='%1$s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
        assertEquals(commands.get(14).toString(),
                     format("{'command'='sudo puppet agent --onetime --ignorecache --no-daemonize --no-usecacheonfailure --no-splay; exit 0;', 'agent'='{'host'='api.example.com', 'port'='22', 'user'='%1$s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
        assertEquals(commands.get(15).toString(),
                     format("{'command'='testFile=\"/home/codenvy/codenvy-data/conf/general.properties\"; while true; do     if sudo grep \"test_runner_node_url$\" ${testFile}; then break; fi;     sleep 5; done; sleep 15; # delay to involve into start of rebooting api server', 'agent'='{'host'='api.example.com', 'port'='22', 'user'='%1$s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
        assertEquals(commands.get(16).toString(), "Wait until artifact 'mockCdecArtifact' becomes alive");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "error")
    public void testGetAddNodeCommandWhenGetValueWithNodeException() throws Exception {
        prepareMultiNodeEnv(mockConfigManager);
        doThrow(new IllegalArgumentException("error")).when(mockNodesConfigUtil).getValueWithNode(TEST_NODE);

        spyHelperCodenvy3.getAddNodeCommand(TEST_NODE, ADDITIONAL_RUNNERS_PROPERTY_NAME);
    }

    @Test
    public void testGetRemoveNodeCommand() throws Exception {
        prepareMultiNodeEnv(mockConfigManager);
        Command removeNodeCommand = spyHelperCodenvy3.getRemoveNodeCommand(TEST_NODE, ADDITIONAL_RUNNERS_PROPERTY_NAME);

        List<Command> commands = ((MacroCommand) removeNodeCommand).getCommands();
        assertEquals(commands.size(), 11);

        assertTrue(commands.get(0).toString().matches("\\{'command'='sudo cp /etc/puppet/" + Config.MULTI_SERVER_CUSTOM_CONFIG_PP
                                                      + " /etc/puppet/" + Config.MULTI_SERVER_CUSTOM_CONFIG_PP + ".back ; "
                                                      + "sudo cp /etc/puppet/" + Config.MULTI_SERVER_CUSTOM_CONFIG_PP
                                                      + " /etc/puppet/" + Config.MULTI_SERVER_CUSTOM_CONFIG_PP + ".back.[0-9]+ ; "
                                                      + "', 'agent'='LocalAgent'\\}"));
        assertEquals(commands.get(1).toString(),
                     "{'command'='sudo cat /etc/puppet/" + Config.MULTI_SERVER_CUSTOM_CONFIG_PP + " " +
                     "| sed ':a;N;$!ba;s/\\n/~n/g' " +
                     "| sed 's|$additional_runners *= *\"[^\"]*\"|$additional_runners = \"null\"|g' " +
                     "| sed 's|~n|\\n|g' > tmp.tmp " +
                     "&& sudo mv tmp.tmp /etc/puppet/manifests/nodes/multi_server/custom_configurations.pp', 'agent'='LocalAgent'}");

        assertTrue(commands.get(2).toString().matches("\\{'command'='sudo cp /etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP
                                                      + " /etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP + ".back ; "
                                                      + "sudo cp /etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP
                                                      + " /etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP + ".back.[0-9]+ ; "
                                                      + "', 'agent'='LocalAgent'\\}"));

        assertEquals(commands.get(3).toString(),
                     "{'command'='sudo cat /etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP + " " +
                     "| sed ':a;N;$!ba;s/\\n/~n/g' " +
                     "| sed 's|$additional_runners *= *\"[^\"]*\"|$additional_runners = \"null\"|g' " +
                     "| sed 's|~n|\\n|g' > tmp.tmp " +
                     "&& sudo mv tmp.tmp /etc/puppet/" + Config.MULTI_SERVER_BASE_CONFIG_PP + "', 'agent'='LocalAgent'}");

        assertEquals(commands.get(4).toString(),
                     format("{'command'='sudo puppet agent --onetime --ignorecache --no-daemonize --no-usecacheonfailure --no-splay; exit 0;', 'agent'='{'host'='api.example.com', 'port'='22', 'user'='%1$s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
        assertEquals(commands.get(5).toString(),
                     format("{'command'='testFile=\"/home/codenvy/codenvy-data/conf/general.properties\"; while true; do     if ! sudo grep \"localhost\" ${testFile}; then break; fi;     sleep 5; done; sleep 15; # delay to involve into start of rebooting api server', 'agent'='{'host'='api.example.com', 'port'='22', 'user'='%1$s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
        assertEquals(commands.get(6).toString(), "Wait until artifact 'mockCdecArtifact' becomes alive");
        assertEquals(commands.get(7).toString(), "{'command'='sudo puppet cert clean localhost', 'agent'='LocalAgent'}");
        assertEquals(commands.get(8).toString(), "{'command'='sudo systemctl restart puppetmaster', 'agent'='LocalAgent'}");
        assertEquals(commands.get(9).toString(),
                     format("{'command'='sudo systemctl stop puppet', 'agent'='{'host'='localhost', 'port'='22', 'user'='%1$s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
        assertEquals(commands.get(10).toString(),
                     format("{'command'='sudo rm -rf /var/lib/puppet/ssl', 'agent'='{'host'='localhost', 'port'='22', 'user'='%1$s', 'identity'='[~/.ssh/id_rsa]'}'}",
                            SYSTEM_USER_NAME));
    }

    @Test(expectedExceptions = NodeException.class, expectedExceptionsMessageRegExp = "error")
    public void testGetRemoveNodeCommandWhenGetValueWithNodeException() throws Exception {
        Config mockConfig = mock(Config.class);
        doReturn(mockConfig).when(mockConfigManager).loadInstalledCodenvyConfig();
        doThrow(new IllegalArgumentException("error")).when(mockConfig)
                                                      .getValue(NodeConfig.NodeType.API.toString().toLowerCase() + Config.NODE_HOST_PROPERTY_SUFFIX);

        spyHelperCodenvy3.getRemoveNodeCommand(TEST_NODE, ADDITIONAL_RUNNERS_PROPERTY_NAME);
    }

    @Test
    public void shouldBeOkWhenInstallTypeIsMultiServerCodenvy() throws Exception {
        prepareMultiNodeEnv(mockConfigManager);
        spyHelperCodenvy3.checkInstallType();
    }

    @Test(expectedExceptions = IllegalStateException.class,
          expectedExceptionsMessageRegExp = "You can add/remove node in Multi-Server Codenvy only")
    public void shouldThrowExceptionWhenInstallTypeIsSingleServerCodenvy() throws Exception {
        prepareSingleNodeEnv(mockConfigManager);
        spyHelperCodenvy3.checkInstallType();
    }

    @Test
    public void testRecognizeNodeTypeFromConfigBy() throws Exception {
        doReturn(NodeConfig.NodeType.RUNNER).when(mockNodesConfigUtil).recognizeNodeTypeFromConfigBy(TEST_NODE_DNS);
        assertEquals(spyHelperCodenvy3.recognizeNodeTypeFromConfigBy(TEST_NODE_DNS), NodeConfig.NodeType.RUNNER);        
    }

    @Test
    public void testGetPropertyNameBy() throws Exception {
        doReturn(ADDITIONAL_RUNNERS_PROPERTY_NAME).when(mockNodesConfigUtil).getPropertyNameBy(NodeConfig.NodeType.RUNNER);
        assertEquals(spyHelperCodenvy3.getPropertyNameBy(NodeConfig.NodeType.RUNNER), ADDITIONAL_RUNNERS_PROPERTY_NAME);
    }

    @Test
    public void testRecognizeNodeConfigFromDns() throws Exception {
        doReturn(TEST_NODE).when(mockNodesConfigUtil).recognizeNodeConfigFromDns(TEST_NODE_DNS);
        assertEquals(spyHelperCodenvy3.recognizeNodeConfigFromDns(TEST_NODE_DNS), TEST_NODE);        
    }

    @Test
    public void testNodesConfigHelper() throws Exception {
        prepareMultiNodeEnv(mockConfigManager);
        AdditionalNodesConfigHelperCodenvy3 helper = spyHelperCodenvy3.getNodesConfigHelper(new Config(Collections.EMPTY_MAP));
        assertNotNull(helper);
    }
}