package org.scm4j.deployer.api;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ApiTest {

    @Test
    public void testProductStructure() {
        ProductStructure ps = ProductStructure.create("file:/C:/smth")
                .addComponent("123:123:123")
                .addComponentDeployer(new IComponentDeployer() {
                    @Override
                    public DeploymentResult deploy() {
                        return null;
                    }

                    @Override
                    public DeploymentResult undeploy() {
                        return null;
                    }

                    @Override
                    public DeploymentResult stop() {
                        return null;
                    }

                    @Override
                    public DeploymentResult start() {
                        return null;
                    }

                    @Override
                    public void init(IDeploymentContext depCtx, Map<String, Object> params) {

                    }
                })
                .addAction(IComponentDeployer.class)
                        .addParam("1", "2")
                        .addParam("3", "4")
                .parent()
                .addAction(IComponentDeployer.class)
                .parent()
                .parent()
                .addComponent("345:345:345")
                .addAction(IComponentDeployer.class)
                        .addParam("8", "9")
                .parent()
                .parent();
        assertEquals(ps.getDefaultDeploymentURL().toString(), "file:/C:/smth");
        assertEquals(ps.getComponents().get(0).getArtifactCoords().toString(), "123:123:jar:123");
        assertEquals(ps.getComponents().get(1).getDeploymentProcedure().getActions().get(0).getInstallerClass(), IComponentDeployer.class);
        assertEquals(ps.getComponents().get(1).getDeploymentProcedure().getActions().get(0).getParams().get("8"), "9");
        assertEquals(ps.getComponents().get(1).getArtifactCoords().toString(), "345:345:jar:345");
        assertEquals(ps.getComponents().get(0).getDeploymentProcedure().getActions().get(0).getParams().get("3"), "4");
        assertEquals(ps.getComponents().get(0).getDeploymentProcedure().getActions().get(1).getInstallerClass(), IComponentDeployer.class);
        assertNotNull(ps.getComponents().get(0).getDeploymentProcedure().getComponentDeployers().get(0));
    }
}
