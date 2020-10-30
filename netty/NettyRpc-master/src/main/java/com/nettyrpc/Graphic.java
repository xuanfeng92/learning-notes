package com.nettyrpc;


import java.util.*;
import java.util.stream.Collectors;

public class Graphic {
    // 保存所有节点 A->{B,D,S} 表示A可以通向B，D，S节点
    private Map<String, List<String>> store = new HashMap<>();
    // 保存两个节点之间的距离 比如：A-B,5 表示A，B之间的距离为5
    private Map<String, Integer> distanceMap = new HashMap<>();

    /**
     *
     * @param src 起始节点
     * @param des 终点节点
     * @param distance 节点之间的距离
     * @return
     */
    public void addNode(String src, String des, int distance){
        if(isNotEmpty(src) && isNotEmpty(des) && distance >0){
            String instanceKey = src+des; // 这里将节点值拼接作为key存储
            if(store.containsKey(src)){
                List<String> values = store.get(src);
                if(values.indexOf(des) < 0){
                    values.add(des);
                    // 保存长度信息
                    distanceMap.put(instanceKey, distance);
                }
            }else{
                List<String> values = new ArrayList<>();
                values.add(des);
                store.put(src, values);
                // 保存长度信息
                distanceMap.put(instanceKey, distance);
            }
        }
    }

    /**
     * 按照nodes的节点顺序，获取该路径下的长度
     * @param nodes
     * @return
     */
    public int getDistance(List<String> nodes){
        int result = 0;
        if(nodes != null && nodes.size() > 0){
            String root = nodes.get(0);
            int len = nodes.size();
            String nextNode;
            for(int i=0,nextIndex=0; i< len && ++nextIndex<len; i++){
                nextNode = nodes.get(nextIndex);
                // 看下一个节点是否在上一个节点的列表中
                if(isConnected(root, nextNode)){
                    result += getDirectDistance(root, nextNode);
                    root = nextNode; // 重新标记查询节点
                }else{
                    result = -1; // -1表示没有这条路径
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 获取从src到desc之间可行的路线，注意src可以和desc一样，即A-B-C-A,相当于回到了原点
     * @param src
     * @param desc
     * @return
     */
    public List<List<String>> possibleRoutes(String src, String desc){
        Map<String, boolean[]> emptyMarks = generateEmptyMarks();
        List<List<String>> routes = new ArrayList<>();

        if(isNotEmpty(src) && isNotEmpty(desc)){
            deepPath(src, null, desc, emptyMarks, routes);
        }

        return routes;
    }

    /**
     * 获取最短路径长度
     * @param src
     * @param desc
     * @return
     */
    public int shortestDistance(String src, String desc){
        List<List<String>> routes = possibleRoutes(src, desc);
        int minDistance=0;
        for(List<String> list: routes){
            int distance = getDistance(list);
            if(minDistance ==0 && distance>0){
                minDistance = distance;
            }
            if(distance < minDistance){
                minDistance = distance;
            }
        }
        return minDistance;
    }

    /**
     * 过滤行程
     * @param src :起始节点
     * @param desc：终点节点
     * @param type：操作类型
     * @param op：指定操作 如 大于，小于和等于
     * @param num：指定操作的值 ROUTE类型时表示指定要经过的行程数量 DISTANCE类型时表示路径长度
     * @return
     */
    public List<List<String>> filterRoutes(String src, String desc, TYPE type, OPERATION op, int num){
        List<List<String>> routes = possibleRoutes(src, desc);
        List<List<String>> result = new ArrayList<>();
        switch (type){
            case ROUTE:{
                switch (op){
                    case GT: result =routes.stream().filter(list -> list.size() > num+1).collect(Collectors.toList()); break;
                    case LT: result =routes.stream().filter(list -> list.size() < num+1).collect(Collectors.toList()); break;
                    case EQ: result= routes.stream().filter(list -> list.size() == num+1).collect(Collectors.toList()); break;
                    default: break;
                }
            } break;
            case DISTANCE:{
                switch (op){
                    case GT: result =routes.stream().filter(list -> {
                        int distance = getDistance(list);
                        return distance > num;
                    }).collect(Collectors.toList()); break;
                    case LT: result =routes.stream().filter(list -> {
                        int distance = getDistance(list);
                        return distance < num;
                    }).collect(Collectors.toList()); break;
                    case EQ: result= routes.stream().filter(list -> {
                        int distance = getDistance(list);
                        return distance == num;
                    }).collect(Collectors.toList()); break;
                    default: break;
                }
            } break;
            default: break;
        }


        return result;
    }

    /**
     * 深度递归，得到可行的路线
     * @param root：起始目标节点值
     * @param passed：已经经过的路径列表，如果没有，则给定null
     * @param desc：终点目标节点值
     * @param pathMarks ：用于标记路径是否已经经过
     * @param paths :用于存储可达的路径
     * @return
     */
    private void deepPath(String root, List<String> passed, String desc,
                          Map<String,boolean[]> pathMarks, List<List<String>> paths) {
        List<String> nodes = store.get(root);
        boolean[] marks = pathMarks.get(root);
        if(null != nodes){
            if(null == passed){
                passed = new ArrayList<>();
                passed.add(root);
            }
            int len  = nodes.size();
            List<String> newPassed = passed;
            for(int i=0; i< len ; i++){
                // 看到该子节点的边是否已经走过
                if(marks[i]){
                    // 如果曾经走过，则回退（即遍历下一个子节点）
                    marks[i] = false; // 重置标记
                    continue;
                }
                String currentNode = nodes.get(i);
                if(len > 1){
                    newPassed = new ArrayList<>(len +1);// 如果有多个子节点，则新建行程分支
                    newPassed.addAll(passed); //
                }
                newPassed.add(currentNode);
                // 将该边进行标记
                marks[i] = true;

                if(currentNode.equals(desc)){
                    paths.add(newPassed);
                }else {
                    deepPath(currentNode, newPassed, desc, pathMarks,paths);
                }
                marks[i] = false; // 重置标记
            }
        }
    }

    /**
     * 获取两个节点之间的直接距离
     * @param node1
     * @param node2
     * @return
     */
    private int getDirectDistance(String node1, String node2){
        int result = 0;
        if(isNotEmpty(node1) && isNotEmpty(node2)){
            // A->B 和 B->A 之间的距离应该是一样的
            String distanceKey1=  node1+node2;
            String distanceKey2=  node2+node1;
            result = distanceMap.containsKey(distanceKey1)?distanceMap.get(distanceKey1):
                    distanceMap.getOrDefault(distanceKey2, 0);
        }
        return result;
    }

    /**
     * 判断两个节点是否相连，注意：两个节点是单向的，比如A->B 和 B->A 是两个不同的连接。
     * @param src
     * @param des
     * @return
     */
    private boolean isConnected(String src, String des){
        boolean result = false;
        if(isNotEmpty(src) && isNotEmpty(des)){
            List<String> values = store.get(src);
            if(null != values){
                result = values.contains(des);
            }
        }
        return result;
    }


    /**
     * 生成一个空的标记存储
     * @return
     */
    private Map<String, boolean[]> generateEmptyMarks(){
        Set<String> keySet = store.keySet();
        Map<String, boolean[]> result = new HashMap<>();
        for(String k : keySet){
            result.put(k, new boolean[store.get(k).size()]);
        }
        return result;
    }


    /**
     * 设置操作的类型，支持路径数和距离长度
     */
    public enum TYPE{
        ROUTE, DISTANCE
    }

    /*
    设置操作的枚举，目前支持 大于，小于和等于
     */
    public enum OPERATION{
        GT, LT, EQ
    }

    /**
     * 有效字符串
     * @param s
     * @return
     */
    private boolean isNotEmpty(String s){
        return null!=s && s.length()>0;
    }

}
