package org.apache.slider.ext.persist;

import org.apache.hadoop.fs.Path;

/**
 * Created by jpliu on 2020/9/23.
 * Build extension resources . Look at example as follows :
 * root@bjo-ssp-sfx02[DEV:]:/home/jpliu/slider/app-tomcat/exploded$ tree
 * .
 * |-- appConfig-default.json
 * |-- LICENSE.txt
 * |-- metainfo.xml
 * |-- NOTICE.txt
 * |-- package
 * |   |-- files
 * |   |   `-- file.tar.gz
 * |   |-- scripts
 * |   |   |-- params.py
 * |   |   `-- xx.py
 * |   `-- templates
 * |       |-- xx.xml.j2
 * |       `-- yy.xml.j2
 * |-- README.md
 * `-- resources-default.json
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
        appConfPath = new Path(this.basePath, "appConfig-default.json");
        resourcesPath = new Path(this.basePath, "resources-default.json");
        metainfoPath = new Path(this.basePath, "metainfo.xml");
        packagePath = new Path(this.basePath, "package");

        filesPath = new Path(this.packagePath,"filesPath");
        scriptsPath= new Path(this.appConfPath,"scripts");
        templatesPath= new Path(this.appConfPath,"templates");

        tarballPath = new Path(this.filesPath, "execute.tar.gz");

        paramsPyPath = new Path(this.scriptsPath, "params.py");
        bootPyPath = new Path(this.scriptsPath, "boot.py");
    }

    @Override
    public String toString() {
        return "instance at " + basePath;
    }
}
