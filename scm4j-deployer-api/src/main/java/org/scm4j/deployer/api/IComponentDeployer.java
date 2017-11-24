package org.scm4j.deployer.api;

public interface IComponentDeployer {

    DeploymentResult deploy();

    DeploymentResult undeploy();

    /**
     * All running services should be stopped and removed, if not possible - disabled (this is a must)
     *
     * @return DeploymentResult.OK successful, DeploymentResult.NEED_REBOOT need restart
     */
    DeploymentResult stop();

    /**
     * Start component after upgrade and change startup type to automatic
     *
     * @return DeploymentResult.OK successful
     */
    DeploymentResult start();

    void init(IDeploymentContext depCtx);
}
