package org.scm4j.deployer.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface IDeploymentProcedure {
    List<IAction> getActions();
}
