package org.jenkinsci.plugins.nvm;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class NvmWrapperUtil {

  private static final Logger LOGGER = Logger.getLogger(NvmWrapperUtil.class.getName());

  private FilePath workspace;
  private Launcher launcher;
  private TaskListener listener;

  private List<String> nvmPaths = Arrays.asList(
    System.getProperty("user.home") + "/.nvm/nvm.sh",
    "/usr/local/nvm/nvm.sh"
  );

  NvmWrapperUtil(final FilePath workspace, Launcher launcher, TaskListener listener) {
    this.workspace = workspace;
    this.listener = listener;
    this.launcher = launcher;
  }

  public Map<String, String> getNpmEnvVars(String version, String nvmInstallURL,
                                           String nvmNodeJsOrgMirror, String nvmIoJsOrgMirror)
    throws IOException, InterruptedException {


    if (!getNvmPath().isPresent()) {
      int statusCode = installNvm(Optional.ofNullable(nvmInstallURL).orElse(NvmDefaults.nvmInstallURL));
      if (statusCode != 0) {
        throw new AbortException("Failed to install Nvm");
      }
    }


    String mirrorBin = version.contains("iojs") ?
      "NVM_IOJS_ORG_MIRROR=" + Optional.ofNullable(nvmIoJsOrgMirror).orElse(NvmDefaults.nvmIoJsOrgMirror) :
      "NVM_NODEJS_ORG_MIRROR=" + Optional.ofNullable(nvmNodeJsOrgMirror).orElse(NvmDefaults.nvmNodeJsOrgMirror);

    ArgumentListBuilder beforeCmd = new ArgumentListBuilder();
    beforeCmd.add("bash");
    beforeCmd.add("-c");
    beforeCmd.add("export > before.env");

    Map<String, String> beforeEnv = toMap(getExport(beforeCmd, "before.env"));

    ArgumentListBuilder nvmSourceCmd = new ArgumentListBuilder();
    nvmSourceCmd.add("bash");
    nvmSourceCmd.add("-c");
    if (!getNvmPath().isPresent()) {
      throw new AbortException("NVM was not installed ");
    }
    getNvmPath().ifPresent(nvmPath -> {
      nvmSourceCmd.add("source " + nvmPath +
        " && " + mirrorBin + " nvm install " + version +
        " && nvm use " + version +
        " && export > nvm.env");
    });

    Map<String, String> afterEnv = toMap(getExport(nvmSourceCmd, "nvm.env"));

    Map<String, String> newEnvVars = new HashMap<>();

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
      .stdout(listener.getLogger())
      .stderr(listener.getLogger()).join();

    if (statusCode != 0) {
      throw new AbortException("Failed to fork bash ");
    }

    return workspace.child(destFile).readToString();

  }

  //**
  private Optional<String> getNvmPath() {
    return nvmPaths.stream().filter((String str) -> {
      Boolean exists = false;
      try {
        exists = workspace.child(str).exists();
      } catch (IOException|InterruptedException e) {}

      return exists;
    })
      .findFirst();

  }

  private Integer installNvm(String nvmInstallURL) throws IOException, InterruptedException {
    listener.getLogger().println("Installing nvm\n");
    FilePath installer = workspace.child("nvm-installer");
    installer.copyFrom(new URL(nvmInstallURL));
    installer.chmod(0755);
    ArgumentListBuilder args = new ArgumentListBuilder();

    args.add(installer.absolutize().getRemote());

    return launcher.launch().cmds(args).pwd(workspace)
      .stdout(listener.getLogger())
      .stderr(listener.getLogger()).join();

  }

  private Map<String, String> toMap(String export) {
    Map<String, String> r = new HashMap<>();

    Arrays.asList(export.split("[\n|\r]")).forEach(line -> {
      String[] entry = line.replaceAll("declare -x ", "").split("=");
      if (entry.length == 2) {
        r.put(entry[0], entry[1].replaceAll("\"", ""));
      }

    });
    return r;
  }

}
