package org.scm4j.deployer.api;

import java.util.Map;

public interface IAction {
    String getInstallerClassName();
    Map<String, Object> getParams();
}
