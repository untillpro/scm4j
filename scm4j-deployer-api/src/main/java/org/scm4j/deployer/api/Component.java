package org.scm4j.deployer.api;

import lombok.Data;
import org.scm4j.commons.Coords;

@Data
public class Component implements IComponent {

    final private Coords artifactCoords;
    private IInstallationProcedure installationProcedure;

    private Component(ComponentBuilder builder) {
        this.artifactCoords = builder.artifactCoords;
        this.installationProcedure = builder.installationProcedure;
    }

    public static ComponentBuilder componentBuilder(String coords) {
        return new ComponentBuilder(coords);
    }

    public static final class ComponentBuilder {

        private final Coords artifactCoords;
        private InstallationProcedure installationProcedure;

        private ComponentBuilder(String coords) {
            this.artifactCoords = new Coords(coords);
        }

        public ComponentBuilder addAction(Action action) {
            if (this.installationProcedure == null) {
                this.installationProcedure = new InstallationProcedure();
                this.installationProcedure.getActions().add(action);
            } else {
                this.installationProcedure.getActions().add(action);
            }
            return this;
        }

        public Component build() {
            return new Component(this);
        }
    }
}
