package org.jenkinsci.plugins.nvm;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by Tomas Salazar on 6/24/14.
 */
public class NvmWrapper extends BuildWrapper {

  private final static Logger log = Logger.getLogger(NvmWrapperUtil.class.getName());

  private String version;
  private transient NvmWrapperUtil wrapperUtil;

  @DataBoundConstructor
  public NvmWrapper(String version) {
    this.version = version;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher,final BuildListener listener) throws IOException, InterruptedException {
    this.wrapperUtil = new NvmWrapperUtil(build.getWorkspace(), launcher, listener.getLogger());
     final Map<String, String> npmEnvVars = this.wrapperUtil.getVars(this.version);
    return new BuildWrapper.Environment() {
      @Override
      public void buildEnvVars(Map<String, String> env) {

        EnvVars envVars = new EnvVars(env);
        envVars.putAll(npmEnvVars);
        env.putAll(envVars);
      }
    };
  }

  @Extension
  public final static class DescriptorImpl extends BuildWrapperDescriptor {
    @Override
    public String getDisplayName() {
      return "Run the build in an nvm managed environment";
    }

    @Override
    public boolean isApplicable(AbstractProject<?, ?> item) {
      return true;
    }

  }

}
