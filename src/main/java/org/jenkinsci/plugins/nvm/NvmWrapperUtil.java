package org.jenkinsci.plugins.nvm;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.util.ArgumentListBuilder;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;


public class NvmWrapperUtil {

  private static final Logger LOGGER = Logger.getLogger(NvmWrapperUtil.class.getName());

  private FilePath workspace;
  private Launcher launcher;
  private PrintStream buildLogger;
  private String nvmPath;

  NvmWrapperUtil(final FilePath workspace, Launcher launcher, PrintStream buildLogger) {
    this.workspace = workspace;
    this.buildLogger = buildLogger;
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
      " && nvm use " + version +
      " && export > nvm.env");

    Map<String, String> afterEnv = toMap(getExport(nvmSourceCmd, "nvm.env"));

    Map<String, String> newEnvVars = new HashMap<String, String>();

    afterEnv.forEach((k, v) -> {
      String beforeValue = beforeEnv.get(k);
      if (!v.equals(beforeValue)) {

        if (k.equals("PATH")) {
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

    Integer statusCode = launcher.launch().pwd(workspace).cmds(args)
      .stdout(buildLogger)
      .stderr(buildLogger).join();

    if (statusCode != 0) {
      new AbortException("Failed to fork bash ");
    }

    return workspace.child(destFile).readToString();

  }

  private Boolean isNvmInstalled() {

    List<String> paths = new ArrayList<String>();
    paths.add("~/.nvm/nvm.sh");
    paths.add("/usr/local/nvm/nvm.sh");

    this.nvmPath = paths.stream().filter(path -> {
      ArgumentListBuilder args = new ArgumentListBuilder();
      args.add("bash");
      args.add("-c");
      args.add("test -f " + path);
      Integer statusCode = -1;
      try {
        statusCode = launcher.launch().cmds(args)
          .stdout(buildLogger)
          .stderr(buildLogger).join();
      } catch (IOException | InterruptedException e) {
        LOGGER.info(e.getMessage(), e);
      }
      return statusCode == 0;
    }).findFirst().orElse(null);
    return nvmPath != null;
  }

  private Integer installNvm() throws IOException, InterruptedException {
    buildLogger.println("Installing nvm\n");

    FilePath installer = workspace.child("nvm-installer");
    installer.copyFrom(new URL("https://raw.github.com/creationix/nvm/master/install.sh"));
    installer.chmod(0755);
    ArgumentListBuilder args = new ArgumentListBuilder();

    args.add(installer.absolutize().getRemote());

    return launcher.launch().cmds(args).pwd(workspace)
      .stdout(buildLogger)
      .stderr(buildLogger).join();
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
