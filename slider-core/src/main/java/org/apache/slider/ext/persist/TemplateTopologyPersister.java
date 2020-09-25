package org.apache.slider.ext.persist;

import java.io.IOException;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.slider.ext.TemplateTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by jpliu on 2020/9/23.
 */
public class TemplateTopologyPersister {
    static final Logger LOG = LoggerFactory.getLogger(TemplateTopologyPersister.class);
    private final TemplateTopologySerDeser templateTopologySerDeser = new TemplateTopologySerDeser();

    private final FileSystem fileSystem;
    private final Path persistDir;

    public TemplateTopologyPersister(FileSystem fileSystem, Path persistDir) {
        this.persistDir = persistDir;
        this.fileSystem = fileSystem;
    }

    public void saveConf(TemplateTopology templateTopology) throws IOException {
        templateTopologySerDeser.save(fileSystem, new Path(""), templateTopology, true);
    }

    public TemplateTopology loadConf() throws IOException {
        return templateTopologySerDeser.load(fileSystem, new Path(""));
    }
}
