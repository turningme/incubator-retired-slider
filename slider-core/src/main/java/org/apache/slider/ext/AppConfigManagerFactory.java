package org.apache.slider.ext;

import org.apache.slider.ext.redstats.RedstatsConfigManager;

/**
 * Created by jpliu on 2020/9/27.
 */
public class AppConfigManagerFactory {

    public static RedstatsConfigManager createAppConfigManager(){
        return new RedstatsConfigManager();
    }
}
