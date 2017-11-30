package org.scm4j.deployer.api;

import java.util.List;

public interface IProductStructure {
    String getDefaultDeploymentPath();
    List<IComponent> getComponents();
}
