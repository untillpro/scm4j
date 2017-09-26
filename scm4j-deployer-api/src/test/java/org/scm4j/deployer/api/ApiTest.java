package org.scm4j.deployer.api;

import org.junit.Test;

import java.util.concurrent.Executor;

import static org.junit.Assert.*;

public class ApiTest {

    @Test
    public void testProductStructure() {
        ProductStructure ps = ProductStructure.create()
                .addComponent("123:123:123")
                    .addAction(Executor.class)
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
        assertEquals(ps.getComponents().get(0).getArtifactCoords().toString(), "123:123:123");
        assertEquals(ps.getComponents().get(1).getInstallationProcedure().getActions().get(0).getInstallerClass(), IComponentDeployer.class);
        assertEquals(ps.getComponents().get(1).getInstallationProcedure().getActions().get(0).getParams().get("8"), "9");
        assertEquals(ps.getComponents().get(1).getArtifactCoords().toString(), "345:345:345");
        assertEquals(ps.getComponents().get(0).getInstallationProcedure().getActions().get(0).getParams().get("3"), "4");
        assertEquals(ps.getComponents().get(0).getInstallationProcedure().getActions().get(1).getInstallerClass(), IComponentDeployer.class);
    }
}
