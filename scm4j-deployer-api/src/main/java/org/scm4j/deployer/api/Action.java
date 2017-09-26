package org.scm4j.deployer.api;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

@Data
public class Action implements IAction {

    final private Class installerClass;
    private Map<String, Object> params;

    private Action(ActionBuilder builder) {
        this.installerClass = builder.installerClass;
        this.params = builder.params;
    }

    public static ActionBuilder actionBuilder(Class clazz) {
        return new ActionBuilder(clazz);
    }

    public static final class ActionBuilder {

        final private Class installerClass;
        private Map<String, Object> params;

        private ActionBuilder (Class installerClass) {
            this.installerClass = installerClass;
        }

        public ActionBuilder addParam(String key, Object value) {
            if(this.params == null) {
                params = new HashMap<>();
                params.put(key, value);
            } else {
                params.put(key, value);
            }
            return this;
        }

        public Action build() {
            return new Action(this);
        }
    }
}
