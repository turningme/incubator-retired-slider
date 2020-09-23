package org.apache.slider.ext.template;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by jpliu on 2020/9/23.
 */
public class DefaultPartitionBind implements PartitionTaskBind<List<Integer>[]>{

    @Override
    public List<Integer>[] apply(Integer parallelism, Integer numPartition) {
        List<Integer>[] allocatedMap = new List[parallelism];
        for (int i= 0 ; i< parallelism ; i++) {
            allocatedMap[i] = new ArrayList<>();
        }
        for (int partitonPos = 0 ; partitonPos < numPartition; partitonPos ++) {
            allocatedMap[partitonPos%parallelism].add(partitonPos);
        }

        return allocatedMap;
    }
}
