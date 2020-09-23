package org.apache.slider.ext.template;



/**
 * Created by jpliu on 2020/9/23.
 */
public interface PartitionTaskBind<R> {
     R apply(Integer parallelism, Integer numPartition);
}
