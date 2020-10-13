package org.apache.slider.ext.args;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import static org.apache.slider.ext.ExtConstants.ACTION_BUILD;
import static org.apache.slider.ext.ExtConstants.ACTION_BUILD_START;
import static org.apache.slider.ext.ExtConstants.ARG_APPLICATION_CONFIG_FILE;
import static org.apache.slider.ext.ExtConstants.ARG_APPLICATION_NAME;
import static org.apache.slider.ext.ExtConstants.ARG_APPLICATION_TARBALL_PATH;


/**
 * Created by jpliu on 2020/9/23.
 */

@Parameters(commandNames = {ACTION_BUILD,ACTION_BUILD_START}, commandDescription = "build the overall templates topology ")
public class ActionBuildArgs extends TemplateClusterBuildingActions{

    @Parameter(names = {ARG_APPLICATION_TARBALL_PATH}, description = "Tallball path ")
    String tarBallPath;

    @Parameter(names = {ARG_APPLICATION_CONFIG_FILE}, description = "Config file ")
    String configFile;

    @Override
    public String getActionName() {
        return ACTION_BUILD;
    }

    public String getTarBallPath() {
        return tarBallPath;
    }

    public String getConfigFile() {
        return configFile;
    }
}
