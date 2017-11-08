package org.scm4j.deployer.api;

import java.net.URL;
import java.util.List;

public interface IProductStructure {
    URL getDefaultDeploymentURL();
    List<IComponent> getComponents();
}
