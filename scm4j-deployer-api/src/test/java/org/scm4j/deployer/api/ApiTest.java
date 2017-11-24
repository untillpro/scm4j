package org.scm4j.deployer.api;

import org.junit.Test;

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
                    public void init(IDeploymentContext depCtx) {

                    }
                })
                .parent()
                .addComponent("345:345:345")
                .parent();
        assertEquals(ps.getDefaultDeploymentURL().toString(), "file:/C:/smth");
        assertEquals(ps.getComponents().get(0).getArtifactCoords().toString(), "123:123:jar:123");
        assertEquals(ps.getComponents().get(1).getArtifactCoords().toString(), "345:345:jar:345");
        assertNotNull(ps.getComponents().get(0).getDeploymentProcedure().getComponentDeployers().get(0));
    }
}
