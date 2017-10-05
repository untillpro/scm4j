package org.scm4j.deployer.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface IInstallationProcedure {
    List<IAction> getActions();
    default Map<Class, Map<String, Object>> getActionsParams() {
        return getActions().stream().collect(Collectors.toMap(IAction::getInstallerClass, IAction::getParams));
    }
}
