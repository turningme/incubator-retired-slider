package org.apache.slider.ext.handler.bean.base;

import java.util.HashMap;
import java.util.Map;

import org.apache.slider.core.conf.ConfTree;

/**
 * Created by jpliu on 2020/9/25.
 */
public class ConfTreeBean {
    protected ConfTree confTree;

    public ConfTreeBean() {
        init();
    }

    private void init() {
        confTree = new ConfTree();
    }

    public void addGlobal(String key, String val) {
        confTree.global.put(key, val);
    }

    public void addComponents(String role, String key, String val) {
        Map<String, String> components = confTree.components.get(role);

        if (components == null) {
            components = new HashMap<>();
            confTree.components.put(role, components);
        }

        components.put(key, val);
    }

    public ConfTree getConfTree() {
        return confTree;
    }
}
