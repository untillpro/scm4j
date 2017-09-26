package org.scm4j.deployer.api;

import lombok.Getter;
import org.scm4j.commons.Coords;

import java.util.ArrayList;

public class Component implements IComponent {

    @Getter final private Coords artifactCoords;
    @Getter private IInstallationProcedure installationProcedure;
    private final ProductStructure ps;

    public Component(String coords, ProductStructure productStructure) {
        this.artifactCoords = new Coords(coords);
        this.ps = productStructure;
    }

    public Action addAction(Class clazz) {
        Action action = new Action(clazz, this);
            if (this.installationProcedure == null) {
                this.installationProcedure = new InstallationProcedure(new ArrayList<>());
                this.installationProcedure.getActions().add(action);
            } else {
                this.installationProcedure.getActions().add(action);
            }
            return action;
    }

    public ProductStructure parent() {
        return ps;
    }
}
