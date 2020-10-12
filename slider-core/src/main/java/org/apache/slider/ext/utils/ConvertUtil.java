package org.apache.slider.ext.utils;

import java.util.HashMap;
import java.util.Map;

import org.apache.slider.ext.ExtConstants;

/**
 * Created by jpliu on 2020/9/23.
 */
public class ConvertUtil {


    /**
     *
     * @param map
     * @return
     */
    public static Map<String, Integer> convert(Map<String, String> map) {
        Map<String, Integer> newMap = new HashMap<>();
        for (Map.Entry<String, String> en : map.entrySet()) {
            newMap.put(en.getKey(), Integer.parseInt(en.getValue()));
        }
        return newMap;
    }

    public static Map<String, Map<Integer,Integer>> convertMem(Map<String, Map<String,String>> map) {
        Map<String,  Map<Integer,Integer>> newMap = new HashMap<>();
        for (Map.Entry<String, Map<String,String>> en : map.entrySet()) {
            Map<Integer,Integer> newMapInner = new HashMap<>();

            for (Map.Entry<String,String> enInner : en.getValue().entrySet()) {
                newMapInner.put(Integer.parseInt(enInner.getKey()), Integer.parseInt(enInner.getValue()));
            }
            newMap.put(en.getKey(), newMapInner);
        }
        return newMap;
    }

    /**
     *
     * @param sourceKey
     * @return
     */
    public static String getConfigurationPrefix(String sourceKey){
        return ExtConstants.TEMPLATE_CONFIGURATION_KEY_PREFIX + sourceKey;
    }

    /**
     * simply get path last fragment from the whole path
     * @param path
     * @return
     */
    public static String getShortNameFromPath(String path){
        return path.substring(path.lastIndexOf("/")+1);
    }

    /**
     *
     * @param ammout
     * @param unit
     * @return
     */
    public static String getMemStringWithMeasure(int ammout, String unit){
        return ammout+unit;
    }
}
