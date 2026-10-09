package org.jenkinsci.plugins.nvm;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.*;
import hudson.tasks.*;
import org.jenkinsci.plugins.workflow.cps.*;
import org.jenkinsci.plugins.workflow.job.*;
import org.junit.*;
import org.jvnet.hudson.test.*;

import java.io.File;

public class NvmWrapperTest {

  @Rule
  public JenkinsRule jenkinsRule = new JenkinsRule();
  @ClassRule
  public static BuildWatcher bw = new BuildWatcher();


  private static FreeStyleBuild createBuildWithNvmWrapper(final JenkinsRule j, final NvmWrapper bw,
                                                          final String command) throws Exception {

    // Create a new freestyle project with a unique name, with an "Execute shell" build step;
    // if running on Windows, this will be an "Execute Windows batch command" build step
    FreeStyleProject project = j.createFreeStyleProject();
    FilePath workspace = j.jenkins.getWorkspaceFor(project);
    workspace.child(".nvm").mkdirs();
    String absPath = new File(workspace.child(".nvm").toURI()).getAbsolutePath();


    project.getBuildWrappersList().add(bw);
    Builder step = Functions.isWindows() ? new BatchFile(command) : new Shell(command);
    project.getBuildersList().add(step);

    // Enqueue a build of the project, wait for it to complete, and assert success
    FreeStyleBuild build = j.buildAndAssertSuccess(project);


    return build;
  }

  @Test
  public void freestyleNVMOptionVersion() throws Exception {

    final String command = "echo $PATH";
    NvmWrapper nvmWrapper = new NvmWrapper("v26.11.1", null,
      null, null, null);

    FreeStyleBuild build = createBuildWithNvmWrapper(jenkinsRule, nvmWrapper, command);
    jenkinsRule.assertLogContains("v26.11.1", build);

  }


  @Test
  public void freestyleNVMOptionIojs() throws Exception {


    final String command = "echo $PATH";
    NvmWrapper nvmWrapper = new NvmWrapper("iojs-v3.3.1", null,
      null, null, null);

    FreeStyleBuild build = createBuildWithNvmWrapper(jenkinsRule, nvmWrapper, command);
    jenkinsRule.assertLogContains("/versions/io.js/", build);
  }


  @Test
  public void freestyleNVMOptionNvmNodeJsOrgMirror() throws Exception {


    final String command = "echo $PATH";
    NvmWrapper nvmWrapper = new NvmWrapper("v26.11.1", null,
      "https://registry.npmmirror.com/-/binary/node", null, null);

    FreeStyleBuild build = createBuildWithNvmWrapper(jenkinsRule, nvmWrapper, command);
    // Assert the custom mirror is passed through to nvm. We check the env var that is
    // always present in the command (regardless of whether the binary was already cached),
    // instead of the "Downloading ..." line which only appears on a cache miss.
    jenkinsRule.assertLogContains("NVM_NODEJS_ORG_MIRROR=https://registry.npmmirror.com/-/binary/node", build);
  }


  @Test
  public void pipelineNVM() throws Exception {

    WorkflowJob project = jenkinsRule.createProject(WorkflowJob.class);
    project.setDefinition(new CpsFlowDefinition("node { nvm('v26.11.1') { sh 'env'} }", true));


    WorkflowRun build = jenkinsRule.buildAndAssertSuccess(project);

    jenkinsRule.assertLogContains("v26.11.1/bin", build);
  }
}
