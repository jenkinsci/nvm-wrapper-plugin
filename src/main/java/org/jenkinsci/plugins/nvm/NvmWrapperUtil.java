package org.jenkinsci.plugins.nvm;
import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.util.ArgumentListBuilder;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by atoms on 6/9/16.
 */
public class NvmWrapperUtil {

  private static final Logger LOGGER = Logger.getLogger(NvmWrapperUtil.class.getName());

  private AbstractBuild build;
  private Launcher launcher;
  private BuildListener listener;
  private String nvmPath;

  NvmWrapperUtil(AbstractBuild build, Launcher launcher, BuildListener listener) {
    this.build = build;
    this.listener = listener;
    this.launcher = launcher;
  }

  public Map<String, String> getVars(String version) throws IOException, InterruptedException {
    if (!isNvmInstalled()) {
      installNvm();
      isNvmInstalled();
    }

    ArgumentListBuilder beforeCmd = new ArgumentListBuilder();
    beforeCmd.add("bash");
    beforeCmd.add("-c");
    beforeCmd.add("export > before.env");

    Map<String, String> beforeEnv = toMap(getExport(beforeCmd, "before.env"));

    ArgumentListBuilder nvmSourceCmd = new ArgumentListBuilder();
    nvmSourceCmd.add("bash");
    nvmSourceCmd.add("-c");
    nvmSourceCmd.add(" source " + nvmPath +
      " && nvm install " + version +
      " && nvm use "+ version +
      " && export > nvm.env");

    Map<String, String> afterEnv = toMap(getExport(nvmSourceCmd, "nvm.env"));

    Map<String, String> newEnvVars = new HashMap<String, String>();

    afterEnv.forEach((k,v)-> {
      String beforeValue = beforeEnv.get(k);
      if (!v.equals(beforeValue)) {

        if (k.equals("PATH")){
          String path = Arrays.stream(v.split(File.pathSeparator))
            .filter(it -> it.matches(".*\\.nvm.*"))
            .collect(Collectors.joining(File.pathSeparator));
          newEnvVars.put("PATH", afterEnv.get("PATH"));
          newEnvVars.put("PATH+NVM", path);
        } else {
          newEnvVars.put(k, v);
        }

      }
    });

    return newEnvVars;
  }

  private String getExport(ArgumentListBuilder args, String destFile) throws IOException, InterruptedException {

    Integer statusCode = launcher.launch().pwd(build.getWorkspace()).cmds(args)
      .stdout(listener.getLogger())
      .stderr(listener.getLogger()).join();

    if (statusCode != 0) {
      new AbortException("Failed to fork bash ");
    }

    return build.getWorkspace().child(destFile).readToString();

  }

  private Boolean isNvmInstalled() {

    List<String> paths = new ArrayList<String>();
    paths.add("~/.nvm/nvm.sh");
    paths.add("/usr/local/nvm/nvm.sh");

    this.nvmPath = paths.stream().filter(path ->{
      ArgumentListBuilder args = new ArgumentListBuilder();
      args.add("bash");
      args.add("-c");
      args.add("test -f " + path);
      Integer statusCode = -1;
      try {
        statusCode = launcher.launch().cmds(args)
          .stdout(listener.getLogger())
          .stderr(listener.getLogger()).join();
      } catch (IOException e) {
        LOGGER.info(e.getMessage(), e);
      } catch (InterruptedException e) {
        LOGGER.info(e.getMessage(), e);
      }
      return statusCode == 0;
    }).findFirst().orElse(null);
    return nvmPath != null;
  }

  private Integer installNvm() throws IOException, InterruptedException {
    listener.getLogger().println("Installing nvm\n");
    FilePath installer = build.getWorkspace().child("nvm-installer");
    installer.copyFrom(new URL("https://raw.github.com/creationix/nvm/v0.33.0/install.sh"));
    installer.chmod(0755);
    ArgumentListBuilder args = new ArgumentListBuilder();

    args.add(installer.absolutize().getRemote());

    int statusCode = launcher.launch().cmds(args).pwd(build.getWorkspace())
      .stdout(listener.getLogger())
      .stderr(listener.getLogger()).join();

    return statusCode;
  }

  private Map<String, String> toMap(String export) {
    Map<String, String> r = new HashMap<String, String>();

    Arrays.asList(export.split("[\n|\r]")).forEach(line -> {
      String[] entry = line.replaceAll("declare -x ", "").split("=");
      if (entry.length == 2) {
        r.put(entry[0], entry[1].replaceAll("\"", ""));
      }

    });
    return r;
  }

}
