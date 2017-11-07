package org.scm4j.deployer.api;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class Action implements IAction {

    @Getter
    final private Class<? extends IComponentDeployer> installerClass;
    @Getter private Map<String, Object> params;
    private final Component comp;

    public Action(Class<? extends IComponentDeployer> installerClass, Component comp) {
        this.installerClass = installerClass;
        this.comp = comp;
    }

    public Action addParam(String key, Object value) {
            if(this.params == null) {
                params = new HashMap<>();
                params.put(key, value);
            } else {
                params.put(key, value);
            }
            return this;
        }

    public Component parent() {
        return comp;
    }

    @Override
    public String toString() {
        return "Action{" +
                "installerClass=" + installerClass.getName() +
                '}';
    }
}
