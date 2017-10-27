package org.scm4j.deployer.api;

import lombok.Data;

import java.util.List;

@Data
public class DeploymentProcedure implements IDeploymentProcedure {

    final private List<IAction> actions;

}