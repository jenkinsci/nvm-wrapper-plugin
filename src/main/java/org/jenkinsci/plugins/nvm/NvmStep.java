package org.jenkinsci.plugins.nvm;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;


public class NvmStep extends Step {

  private final String version;

  @DataBoundConstructor
  public NvmStep(final String version) {
    this.version = version;
  }

  public String getVersion() {
    return version;
  }

  @Override
  public StepExecution start(final StepContext context) throws Exception {
    return new Execution(version, context);
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
    public Step newInstance(StaplerRequest req, JSONObject formData) throws FormException {
      final String versionFromFormData = formData.getString("version");

      return new NvmStep(versionFromFormData);
    }

    @Override
    public boolean takesImplicitBlockArgument() {
      return true;
    }

  }

  public static class Execution extends AbstractStepExecutionImpl {

    private static final long serialVersionUID = 1;

    private final transient String version;

    public Execution(final String version, @Nonnull final StepContext context) {
      super(context);
      this.version = version;
    }

    @Override
    public boolean start() throws Exception {
      final FilePath workspace = this.getContext().get(FilePath.class);
      final Launcher launcher = this.getContext().get(Launcher.class);
      final PrintStream logger = this.getContext().get(TaskListener.class).getLogger();

      final NvmWrapperUtil wrapperUtil = new NvmWrapperUtil(workspace, launcher, logger);
      final Map<String, String> npmEnvVars = wrapperUtil.getVars(this.version);

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
