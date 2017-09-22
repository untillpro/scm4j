package org.scm4j.deployer.api;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

public interface IDeploymentContext {

    Map<String, File> getArtifacts();
    List<String> getDeps();
    URL getDeploymentURL();
    Map<String, Object> getParams();
    void setArtifacts(Map<String, File> artifacts);
    void setDeps(List<String> deps);
    void setDeploymentURL(URL deploymentURL);
    void setParams(Map<String,Object> params);

}
