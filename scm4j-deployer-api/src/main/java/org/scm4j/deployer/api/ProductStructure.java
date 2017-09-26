package org.scm4j.deployer.api;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductStructure implements IProductStructure {

    final private List<IComponent> components;

    private ProductStructure(ProductStructureBuilder builder) {
        this.components = builder.components;
    }

    public static ProductStructureBuilder productStructureBuilder() {
        return new ProductStructureBuilder();
    }

//    public Component addComponent(String compName) {
//        comp = new Component(new Coords(compName));
//        if (this.getComponents() == null) {
//            this.setComponents(new ArrayList<>());
//            this.getComponents().add(comp);
//        } else {
//            this.getComponents().add(comp);
//        }
//        return comp;
//    }
//
//    public Action addAction(Class clazz) {
//        action = new Action(clazz);
//        if (comp.getInstallationProcedure() == null) {
//            comp.setInstallationProcedure(new InstallationProcedure());
//            comp.getInstallationProcedure().getActions().add(action);
//        } else {
//            comp.getInstallationProcedure().getActions().add(action);
//        }
//        return action;
//    }
//
//    public Action addParam(String name, Object value) {
//        action.getParams().put(name, value);
//        return action;
//    }
//
//    public ProductStructure end() {
//        return this;
//    }

    public static final class ProductStructureBuilder {

        private List<IComponent> components;

        private ProductStructureBuilder() {}

        public ProductStructureBuilder addComponent(Component component) {
            if (components == null) {
                components = new ArrayList<>();
                components.add(component);
            } else {
                components.add(component);
            }
            return this;
        }

        public ProductStructure build() {
            if(components == null) {
                throw new IllegalArgumentException("Components can't be null");
            }
            return new ProductStructure(this);
        }
    }

}


