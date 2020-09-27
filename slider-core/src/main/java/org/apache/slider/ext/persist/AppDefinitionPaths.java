package org.apache.slider.ext.persist;

import org.apache.hadoop.fs.Path;

import static org.apache.slider.ext.ExtConstants.META_APP_OS_BOOT_PY;
import static org.apache.slider.ext.ExtConstants.META_APP_OS_PARAM_PY;
import static org.apache.slider.ext.ExtConstants.META_APP_TALLBALL_NAMW;
import static org.apache.slider.ext.ExtConstants.TEMPLATE_CLUSTER_LAYOUT_APPCONF;
import static org.apache.slider.ext.ExtConstants.TEMPLATE_CLUSTER_LAYOUT_METAINFO;
import static org.apache.slider.ext.ExtConstants.TEMPLATE_CLUSTER_LAYOUT_RESOURCE;

/**
 * Created by jpliu on 2020/9/23.
 * Build extension resources . Look at example as follows :
 /tmp/.template-cluster/template-app1/
 |-- appConfig-default.json
 |-- metainfo.json
 |-- package
 |   |-- files
 |   |   `-- execute.tar.gz
 |   |-- scripts
 |   |   |-- boot.py
 |   |   `-- params.py
 |   `-- templates
 `-- resources-default.json
 *
 *
 *
 */
public class AppDefinitionPaths {
    public final Path basePath;
    public final Path appConfPath;
    public final Path resourcesPath;
    public final Path metainfoPath;
    public final Path packagePath;
    public final Path filesPath;
    public final Path scriptsPath;
    public final Path templatesPath;
    public final Path tarballPath;

    public final Path paramsPyPath;
    public final Path bootPyPath;

    public AppDefinitionPaths(Path basePath) {
        this.basePath = basePath;
        appConfPath = new Path(this.basePath, TEMPLATE_CLUSTER_LAYOUT_APPCONF);
        resourcesPath = new Path(this.basePath, TEMPLATE_CLUSTER_LAYOUT_RESOURCE);
        metainfoPath = new Path(this.basePath, TEMPLATE_CLUSTER_LAYOUT_METAINFO);
        packagePath = new Path(this.basePath, "package");

        filesPath = new Path(this.packagePath,"files");
        scriptsPath= new Path(this.packagePath,"scripts");
        templatesPath= new Path(this.packagePath,"templates");

        tarballPath = new Path(this.filesPath, META_APP_TALLBALL_NAMW);

        paramsPyPath = new Path(this.scriptsPath, META_APP_OS_PARAM_PY);
        bootPyPath = new Path(this.scriptsPath, META_APP_OS_BOOT_PY);
    }

    @Override
    public String toString() {
        return "instance at " + basePath;
    }
}
