package com.nettyrpc.test;

import com.nettyrpc.Graphic;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestGraphic {

    private Graphic g ;

    // 初始化数据
    @Before
    public void initData(){
        g = new Graphic();
        String input = "AB5, BC4, CD8, DC8, DE6, AD5, CE2, EB3, AE7";
        addGraphicNodes(input);
    }

    // 1：The distance of the route A-B-C.
    @Test
    public void Test01(){
        List<String> nodes = new ArrayList<>();
        nodes.add("A");
        nodes.add("B");
        nodes.add("C");
        System.out.println("#1:"+g.getDistance(nodes));
    }

    // 2: The distance of the route A-D.
    @Test
    public void Test02(){
        List<String> nodes = new ArrayList<>();
        nodes.add("A");
        nodes.add("D");
        System.out.println("#2:"+g.getDistance(nodes));
    }

    // 3: The distance of the route A-D-C.
    @Test
    public void test03(){
        List<String> nodes = new ArrayList<>();
        nodes.add("A");
        nodes.add("D");
        nodes.add("C");
        System.out.println("#3:"+g.getDistance(nodes));
    }

    // 4: The distance of the route A-E-B-C-D.
    @Test
    public void test04(){
        List<String> nodes = new ArrayList<>();
        nodes.add("A");
        nodes.add("E");
        nodes.add("B");
        nodes.add("C");
        nodes.add("D");
        System.out.println("#4:"+g.getDistance(nodes));
    }

    // 5: The distance of the route A-E-D.
    @Test
    public void test05(){
        List<String> nodes = new ArrayList<>();
        nodes.add("A");
        nodes.add("E");
        nodes.add("D");
        int distance = g.getDistance(nodes);
        if(distance>0)  System.out.println("#5:"+g.getDistance(nodes));
        else            System.out.println("#5: NO SUCH ROUTE");
    }

    // 6: The number of trips starting at C and ending at C with a maximum of 3 stops.  In the sample data below, there are two such trips: C-D-C (2 stops). and C-E-B-C (3 stops).
    @Test
    public void test06(){
        List<List<String>> lists = g.possibleRoutes("C", "C");
        System.out.println("#6:"+lists.size());
        for(List<String> list: lists){
            System.out.println(listRoute(list));
        }
    }

    // 7. The number of trips starting at A and ending at C with exactly 4 stops.
    @Test
    public void test07(){
        List<List<String>> routes = g.filterRoutes("A", "C", Graphic.TYPE.ROUTE,Graphic.OPERATION.EQ, 4);
        System.out.println("#7:"+routes.size());
        for (List<String> list : routes){
            System.out.println(listRoute(list));
        }
    }

    // 8. The length of the shortest route (in terms of distance to travel) from A to C.
    @Test
    public void test08(){
        System.out.println("#8:"+g.shortestDistance("A", "C"));
    }

    // 9. The length of the shortest route (in terms of distance to travel) from B to B.
    @Test
    public void test09(){
        System.out.println("#9:"+g.shortestDistance("B", "B"));
    }

    // 10. he number of different routes from C to C with a distance of less than 30.
    @Test
    public void test10(){
        List<List<String>> routes = g.filterRoutes("C", "C", Graphic.TYPE.DISTANCE,Graphic.OPERATION.LT, 30);
        System.out.println("#10:"+routes.size());
        for (List<String> list : routes){
            System.out.println(listRoute(list));
        }
    }

    /**
     * 工具方法： 将路径打印出来
     * @param routes
     * @return
     */
    private String listRoute(List<String> routes){
        int len = routes.size();
        StringBuilder result = new StringBuilder();
        for(int i=0; i<len; i++){
            if(i == 0){
                result.append(routes.get(i));
            }else{
                result.append("->"+routes.get(i));
            }
        }
        return result.toString();
    }

    /**
     * 工具方法： 用于解析输入内容，初始化有向图的节点
     * @param input
     */
    private void addGraphicNodes(String input) {
        String[] arrays = input.split(",");
        String reg = "^(\\w{1,})(\\d)$";
        Pattern p = Pattern.compile(reg);
        Matcher matcher;
        for(String s: arrays){
            matcher = p.matcher(s.trim());
            if(matcher.find()){
                String line = matcher.group(1);
                String distance = matcher.group(2);
                char[] nodes = line.toCharArray();
                if(nodes.length==2){
                    g.addNode(String.valueOf(nodes[0]), String.valueOf(nodes[1]), Integer.valueOf(distance));
                }
            }
        }
    }
}
