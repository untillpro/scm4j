package org.scm4j.deployer.api;

import org.junit.Test;

import java.util.concurrent.Executor;

import static org.junit.Assert.*;

public class ApiTest {

    @Test
    public void testProductStructure() {
        try {
            ProductStructure ps = ProductStructure.productStructureBuilder()
                    .build();
            fail();
        } catch (IllegalArgumentException e) {

        }
        ProductStructure ps = ProductStructure.productStructureBuilder()
                .addComponent(Component.componentBuilder("123:123:123")
                        .addAction(Action.actionBuilder(Executor.class)
                                .addParam("1", "2")
                                .addParam("3", "4")
                                .build())
                        .build())
                .addComponent(Component.componentBuilder("345:345:345")
                        .addAction(Action.actionBuilder(IComponentDeployer.class)
                                .addParam("8", "9")
                                .build())
                        .build())
                .build();
        assertEquals(ps.getComponents().get(0).getArtifactCoords().toString(), "123:123:123");
        assertEquals(ps.getComponents().get(1).getInstallationProcedure().getActions().get(0).getInstallerClass(), IComponentDeployer.class);
        assertEquals(ps.getComponents().get(1).getInstallationProcedure().getActions().get(0).getParams().get("8"), "9");
    }
}
