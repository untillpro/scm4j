package org.scm4j.deployer.api;

import java.util.Map;

public interface IAction {
    Class getInstallerClass();
    Map<String, Object> getParams();
    Action addParam(String name, Object value);
}
