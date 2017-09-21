package org.scm4j.deployer.api;

import lombok.Data;

import java.util.Map;

@Data
public class Action implements IAction {

    final private String installerClass;
    Map<String, Object> params;

    @Override
    public Action addParam(String name, Object value){
        params.put(name, value);
        return this;
    }

    public Action(String installerClass) {
        this.installerClass = installerClass;
    }

}
