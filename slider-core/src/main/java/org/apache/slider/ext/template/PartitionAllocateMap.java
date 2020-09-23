package org.apache.slider.ext.template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jpliu on 2020/9/23.
 */
public class PartitionAllocateMap {
    private int parallel;
    private  int numPartiton;
    private PartitionTaskBind<List<Integer>[]> allocatingStrategy;

    public PartitionAllocateMap(int numPartiton, int parallel) {
        this.numPartiton = numPartiton;
        this.parallel = parallel;

        allocatingStrategy = new DefaultPartitionBind();
    }


    public Map<Integer,List<Integer>> allocate(){
        List<Integer>[] allocatedMap = allocatingStrategy.apply(parallel,numPartiton);
        Map<Integer,List<Integer>> map = new HashMap<>();
        for (int i= 0 ; i<allocatedMap.length ; i++) {
            map.put(i,allocatedMap[i]);
        }

        return map;
    }

}
