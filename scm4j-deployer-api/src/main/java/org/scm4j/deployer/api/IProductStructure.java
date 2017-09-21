package org.scm4j.deployer.api;

import java.net.URL;
import java.util.List;

public interface IProductStructure {
    List<IComponent> getComponents();
    default URL getDefaultDeploymentURL() throws Exception {
        return new URL("C:/unTILL");
    }
}
