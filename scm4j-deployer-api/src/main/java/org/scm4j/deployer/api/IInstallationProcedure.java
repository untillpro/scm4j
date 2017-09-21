package org.scm4j.deployer.api;

import java.util.List;

public interface IInstallationProcedure {
    List<IAction> getActions();
}
