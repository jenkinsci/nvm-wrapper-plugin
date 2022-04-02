package org.jenkinsci.plugins.nvm;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class NvmWrapperUtil {

  private FilePath workspace;
  private Launcher launcher;
  private TaskListener listener;

  NvmWrapperUtil(final FilePath workspace, Launcher launcher, TaskListener listener) {
    this.workspace = workspace;
    this.listener = listener;
    this.launcher = launcher;
  }

  public Map<String, String> getNpmEnvVars(final String nodeVersion, final String nvmInstallDir,
                                           final String nvmInstallURL, final  String nodeMirrorBinaries)
    throws IOException, InterruptedException {


    String nvmDir = StringUtils.defaultIfEmpty(nvmInstallDir, NvmDefaults.NVM_INSTALL_DIR);
    nvmDir = nvmDir.endsWith("/") ? StringUtils.stripEnd(nvmDir, "/")  : nvmDir;
    final String nvmFilePath = nvmDir + "/nvm.sh";

    if(fileExist(nvmFilePath) == false){ //NVM is not installed
      int statusCode = installNvm(StringUtils.defaultIfEmpty(nvmInstallURL, NvmDefaults.NVM_INSTALL_URL),
        nvmDir, nodeMirrorBinaries, nodeVersion);

      if (statusCode != 0) {
        throw new AbortException("Failed to install NVM");
      }

    }else{
      listener.getLogger().println("NVM is already installed\n");
    }




    Map<String, String> beforeEnv = getExport();

    String envFile="env.txt";

    ArgumentListBuilder nvmSourceCmd = new ArgumentListBuilder();
    nvmSourceCmd.add("bash");
    nvmSourceCmd.add("-c");
    nvmSourceCmd.add(
        "NVM_DIR=" + nvmDir +
        " && source $NVM_DIR/nvm.sh --no-use"+
        " && " + "nvm which " + nodeVersion +
        " || " + nodeMirrorBinaries +  " nvm install " + nodeVersion +
        " && nvm use " + nodeVersion + " && export > " + envFile );


    Map<String, String> afterEnv = getExport(nvmSourceCmd, envFile);

    Map<String, String> newEnvVars = new HashMap<>();

    afterEnv.forEach((k, v) -> {
      String beforeValue = beforeEnv.get(k);
      if (!v.equals(beforeValue)) { // ENV changed between installation

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



  private Map<String, String> getExport(ArgumentListBuilder args, String destFile ) throws IOException, InterruptedException {

    Integer statusCode = launcher.launch().pwd(workspace).cmds(args)
      .stdout(listener.getLogger())
      .stderr(listener.getLogger()).join();

    if (statusCode != 0) {
      throw new AbortException("Failed to fork bash ");
    }

    String out = workspace.child(destFile).readToString();

    workspace.child(destFile).delete();

    return toMap(out);
  }
  private Map<String, String> getExport() throws IOException, InterruptedException {

    String destFile="env.txt";
    ArgumentListBuilder args = new ArgumentListBuilder();
    args.add("bash");
    args.add("-c");
    args.add("export > " + destFile);
    return getExport(args, destFile);

  }

  private Boolean fileExist(String filePath) throws IOException, InterruptedException {
    ArgumentListBuilder args = new ArgumentListBuilder();
      args.add("bash");
      args.add("-c");
      args.add("test -f " + filePath);

    Integer statusCode = launcher.launch().pwd(workspace).cmds(args)
      .stdout(listener.getLogger())
      .stderr(listener.getLogger()).join();

    return statusCode == 0;
  }

  private Integer installNvm(final String nvmInstallURL, final String nvmInstallDir,
                             final String nodeMirrorBinaries, final String nvmInstallNodeVersion) throws IOException, InterruptedException {
    listener.getLogger().println("Installing nvm\n");
    FilePath installer = workspace.child("nvm-installer");
    installer.copyFrom(new URL(nvmInstallURL));
    installer.chmod(0755);

    ArgumentListBuilder args = new ArgumentListBuilder();
    args.add("bash");
    args.add("-c");


    List<String> cmdBuild = new ArrayList<>();

//    only add if default is changed
//    https://github.com/creationix/nvm/blob/master/install.sh#L300
//    nvm script just create install dir if it is the default.
    if(!nvmInstallDir.equals(NvmDefaults.NVM_INSTALL_DIR)){
      cmdBuild.add("NVM_DIR=" + nvmInstallDir);
    }

    if (StringUtils.isNotBlank(nvmInstallNodeVersion)) {
      cmdBuild.add("NODE_VERSION=" + nvmInstallNodeVersion);
    }
    cmdBuild.add(nodeMirrorBinaries);
    cmdBuild.add("NVM_PROFILE=/dev/null"); //Avoid modifying profile
    cmdBuild.add(installer.absolutize().getRemote());

    args.add(cmdBuild.stream().collect(Collectors.joining(" ")));

    return launcher.launch().pwd(workspace).cmds(args)
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
