package org.apache.slider.ext;

import static org.apache.slider.common.SliderKeys.COMPONENT_AM;

/**
 * Created by jpliu on 2020/9/23.
 * Main constants for Extension utilization .
 */
public class ExtConstants {


    /**
     * Action key
     */

    public static final String ACTION_START = "start";
    public static final String ACTION_BUILD = "build";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_META_CONVERT = "metaconvert";

    /**
     * Arguments keys
     */
    public static final String ARG_APPLICATION_NAME = "--app.name";
    public static final String ARG_TEMPLATE_NAME = "--template";
    public static final String ARG_TEMPLATE_PARALLEL_NAME = "--template.parallel";
    public static final String ARG_TEMPLATE_PARTITION_NAME = "--template.partition";
    public static final String ARG_TEMPLATE_MEMORY_NAME = "--template.mem";
    public static final String ARG_TEMPLATE_INSTANCE_MEMORY_NAME = "--template.instance.mem";
    public static final String ARG_APPLICATION_TARBALL_PATH = "--tarball";

    /**
     * meta convert
     */
    public static final String ARG_META_CONVERT_SRC = "--source";
    public static final String ARG_META_CONVERT_DEST = "--dest";


    /**
     * env constants keys
     */
    public static final String ENV_WORKSPACE_BASE = "/tmp";
    public static final String ENV_WORKSPACE_BASE_SUFFIX = ".template-cluster";


    /**
     * meta info constant
     */

    public static final String META_SCHEMA_VERSION_DEFAULT = "2.0";
    public static final String META_APP_COMMENT_DEFAULT = "Template Cluster";
    public static final String META_APP_VERSION_DEFAULT = "0.0.1";


    /**
     * components
     */

    public static final String COMPONENT_SLIDER_DEFAULT = COMPONENT_AM;
    public static final String COMPONENT_SLIDER_JVM_HEAP_DEFAULT = "256M";


    /**
     * Application Definition Layout
     */

    public static final String TEMPLATE_CLUSTER_LAYOUT_BASE_DIR = "/tmp/.template-cluster";
    public static final String TEMPLATE_CLUSTER_LAYOUT_APPCONF = "appConfig-default.json";
    public static final String TEMPLATE_CLUSTER_LAYOUT_METAINFO = "metainfo.json";
    public static final String TEMPLATE_CLUSTER_LAYOUT_RESOURCE = "resources-default.json";
}
