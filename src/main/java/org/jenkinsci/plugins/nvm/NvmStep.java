package org.jenkinsci.plugins.nvm;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class NvmStep extends Step {

  private final @Nonnull String version;
  private String nvmInstallURL;
  private String nvmNodeJsOrgMirror;
  private String nvmIoJsOrgMirror;
  private String nvmInstallDir;;

  @DataBoundConstructor
  public NvmStep(final String version) {
    this.version = version;
    this.nvmInstallDir = NvmDefaults.NVM_INSTALL_DIR;
    this.nvmInstallURL = NvmDefaults.NVM_INSTALL_URL;
    this.nvmNodeJsOrgMirror = NvmDefaults.NVM_NODE_JS_ORG_MIRROR;
    this.nvmIoJsOrgMirror = NvmDefaults.NVM_IO_JS_ORG_MIRROR;
  }

  public String getVersion() {
    return version;
  }

  @DataBoundSetter
  public void setNvmInstallURL(final String nvmInstallURL) {
    this.nvmInstallURL = nvmInstallURL;
  }

  public String getNvmInstallURL() {
    return nvmInstallURL;
  }

  @DataBoundSetter
  public void setNvmNodeJsOrgMirror(final String nvmNodeJsOrgMirror) {
    this.nvmNodeJsOrgMirror = nvmNodeJsOrgMirror;
  }

  public String getNvmNodeJsOrgMirror() {
    return nvmNodeJsOrgMirror;
  }

  @DataBoundSetter
  public void setNvmIoJsOrgMirror(final String nvmIoJsOrgMirror) {
    this.nvmIoJsOrgMirror = nvmIoJsOrgMirror;
  }

  public String getNvmIoJsOrgMirror() {
    return nvmIoJsOrgMirror;
  }

  public String getNvmInstallDir() {
    return nvmInstallDir;
  }

  @DataBoundSetter
  public void setNvmInstallDir(final String nvmInstallDir) {
    this.nvmInstallDir = nvmInstallDir;
  }

  @Override
  public StepExecution start(final StepContext context) throws Exception {
    return new Execution(this.version, this.nvmInstallDir,
                        this.nvmInstallURL, this.nvmNodeJsOrgMirror,
                        this.nvmIoJsOrgMirror, context);
  }

  @Extension
  public static final class DescriptorImpl extends StepDescriptor {

    @Override
    public String getFunctionName() {
      return "nvm";
    }

    @Override
    public String getDisplayName() {
      return "Setup the environment for an NVM installation.";
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return ImmutableSet.of(
        FilePath.class,
        Launcher.class,
        TaskListener.class
      );
    }

    @Override
    public Step newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
      final String versionFromFormData = formData.getString("version");
      final String nvmInstallURLFromFormData = formData.getString("nvmInstallURL");
      final String nvmNodeJsOrgMirrorFromFormData = formData.getString("nvmNodeJsOrgMirror");
      final String nvmIoJsOrgMirrorFromFormData = formData.getString("nvmIoJsOrgMirror");
      final String nvmInstallDir = formData.getString("nvmInstallDir");

      NvmStep nvmStep = new NvmStep(versionFromFormData);

      if (StringUtils.isNotBlank(nvmInstallURLFromFormData)) {
        nvmStep.setNvmInstallURL(nvmInstallURLFromFormData);
      }

      if (StringUtils.isNotBlank(nvmNodeJsOrgMirrorFromFormData)) {
        nvmStep.setNvmNodeJsOrgMirror(nvmNodeJsOrgMirrorFromFormData);
      }

      if (StringUtils.isNotBlank(nvmIoJsOrgMirrorFromFormData)) {
        nvmStep.setNvmIoJsOrgMirror(nvmIoJsOrgMirrorFromFormData);
      }

      nvmStep.setNvmInstallDir(nvmInstallDir);
      return nvmStep;
    }

    @Override
    public boolean takesImplicitBlockArgument() {
      return true;
    }

  }

  public static class Execution extends AbstractStepExecutionImpl {

    private static final long serialVersionUID = 2L;


    private final String nodeVersion;
    private final String nvmInstallURL;
    private final String nvmNodeJsOrgMirror;
    private final String nvmIoJsOrgMirror;
    private final String nvmInstallDir;


    public Execution(final String nodeVersion, final String nvmInstallDir,
                     final String nvmInstallURL, final String nvmNodeJsOrgMirror,
                     final String nvmIoJsOrgMirror, @Nonnull final StepContext context) {
      super(context);
      this.nodeVersion = nodeVersion;
      this.nvmInstallURL = nvmInstallURL;
      this.nvmNodeJsOrgMirror = nvmNodeJsOrgMirror;
      this.nvmIoJsOrgMirror = nvmIoJsOrgMirror;
      this.nvmInstallDir = nvmInstallDir;

    }

    @Override
    public boolean start() throws Exception {
      final FilePath workspace = this.getContext().get(FilePath.class);
      final Launcher launcher = this.getContext().get(Launcher.class);

      workspace.mkdirs();

      final NvmWrapperUtil wrapperUtil = new NvmWrapperUtil(workspace, launcher, launcher.getListener());

      String nodeMirrorBinaries = nodeVersion.contains("iojs") ?
        "NVM_IOJS_ORG_MIRROR=" + StringUtils.defaultIfEmpty(nvmIoJsOrgMirror, NvmDefaults.NVM_IO_JS_ORG_MIRROR):
        "NVM_NODEJS_ORG_MIRROR=" + StringUtils.defaultIfEmpty(nvmNodeJsOrgMirror, NvmDefaults.NVM_NODE_JS_ORG_MIRROR);


      final Map<String, String> npmEnvVars = wrapperUtil.getNpmEnvVars(this.nodeVersion, this.nvmInstallDir,
                                                                        this.nvmInstallURL, nodeMirrorBinaries);

      getContext().newBodyInvoker()
        .withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(npmEnvVars)))
        .withCallback(BodyExecutionCallback.wrap(getContext()))
        .start();

      return false;
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {
      // No need to do anything heres
    }

  }

  private static class ExpanderImpl extends EnvironmentExpander {

    private final Map<String, String> envOverrides;

    public ExpanderImpl(final Map<String, String> envOverrides) {
      this.envOverrides = envOverrides;
    }

    @Override
    public void expand(@Nonnull final EnvVars env) throws IOException, InterruptedException {
      this.envOverrides.entrySet().forEach((entrySet) -> env.overrideAll(this.envOverrides));
    }

  }

}
