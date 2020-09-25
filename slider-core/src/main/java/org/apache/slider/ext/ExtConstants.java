package org.apache.slider.ext;

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


}
