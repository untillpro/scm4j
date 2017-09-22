package org.scm4j.deployer.api;

import lombok.Data;

import java.util.Map;

@Data
public class Action implements IAction {

    final private Class installerClass;
    Map<String, Object> params;

    @Override
    public Action addParam(String name, Object value){
        params.put(name, value);
        return this;
    }

    public Action(Class installerClass) {
        this.installerClass = installerClass;
    }

}
