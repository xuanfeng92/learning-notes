package com.nettyrpc;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Merchant {

    private static Map<String, Integer> rules;

    private static List<String>  exclude = Arrays.asList("V","L", "D");

    private static Map<String, List<String>> subtractRuleMap;

    private Map<String, Object> externalRules = new HashMap<>(); // 外部输入的规则

    private String reg1 = "^(\\w+)\\s+is\\s+(\\w+)$";                           // 解析 “glob is I”
    private String reg2 = "(\\w+\\s+.*)is\\s+(\\d+)\\s+Credits";                // 解析 “glob glob Silver is 34 Credits”
    private String reg3 = "(how\\s+much\\s+is\\s+)(\\w+\\s+.*)\\?";             // 解析 “how much is pish tegj glob glob ?”
    private String reg4 = "(how\\s+many\\s+Credits\\s+is\\s+)(\\w+\\s+.*)\\?";  // 解析 “how much is pish tegj glob glob ?”

    private Pattern pattern1 = Pattern.compile(reg1);
    private Pattern pattern2 = Pattern.compile(reg2);
    private Pattern pattern3 = Pattern.compile(reg3);
    private Pattern pattern4 = Pattern.compile(reg4);

    // 初始化规则
    static {
        rules = new HashMap<>();
        rules.put("I", 1);
        rules.put("V", 5);
        rules.put("X", 10);
        rules.put("L", 50);
        rules.put("C", 100);
        rules.put("D", 500);
        rules.put("M", 1000);

        // 规则：I只能被 V和X减去； X只能被L和C减去； C只能被D和M减去
        subtractRuleMap= new HashMap<>();
        subtractRuleMap.put("I", Arrays.asList("V", "X"));
        subtractRuleMap.put("X", Arrays.asList("L", "C"));
        subtractRuleMap.put("C", Arrays.asList("D", "M"));

    }

    public int compute(String s){
        if(isNotEmpty(s)){
            assert(isValidate(s)); // 检查计算内容是否合法
            char[] chars = s.toCharArray();
            int[] values = new int[chars.length]; // 存放对于的数值，有正有负，如果小的在大的前面，如CM,此时应该计算为 -100 + 1000

            /**
             * rules:
             * 1. Only one small-value symbol may be subtracted from any large-value symbol. eg: value of CM is  -100 + 1000=900
             * 2. "I" can be subtracted from "V" and "X" only. "X" can be subtracted from "L" and "C" only. "C" can be subtracted from "D" and "M" only.
             * 3. "V", "L", and "D" can never be subtracted.
             */
            for(int i=0; i<chars.length; i++){
                String current = String.valueOf(chars[i]);
                String next = null;
                if(i+1<chars.length){
                    next = String.valueOf(chars[i+1]);
                }
                if(null == next){
                    values[i] = rules.get(current);
                }else{
                    Integer currentValue = rules.get(current);
                    Integer nextValue = rules.get(next);
                    // rules 1,3
                    if(currentValue < nextValue && !exclude.contains(current)){
                        // rules 2
                        if(subtractRuleMap.containsKey(current)
                                && subtractRuleMap.get(current).contains(next)){
                            values[i] = currentValue*-1;
                        }else{
                            values[i] = currentValue;
                        }
                    }else{
                        values[i] = currentValue;
                    }
                }
            }
            int result =0;
            for(int j=0; j<values.length; j++){
                result =result+values[j];
            }
            return result;
        }else{
            return 0;
        }
    }

    /**
     * 解析单条语句
     * @param words
     */
    public void semanticAnalysis(String words){

        Matcher matcher1 = pattern1.matcher(words);     // 解析 “glob is I”
        if(matcher1.find()){
            // 自定义规则
            selfDefineRules(matcher1);
            return;
        }
        Matcher matcher2 = pattern2.matcher(words);     // 解析 “glob glob Silver is 34 Credits”
        if(matcher2.find()){
            // 计算语句中未知的参数值
            computeUnknown(matcher2);
            return;
        }

        Matcher matcher3 = pattern3.matcher(words);     // 解析 “how much is pish tegj glob glob ?”
        if(matcher3.find()){
            answer(matcher3);
            return;
        }

        Matcher matcher4 = pattern4.matcher(words);     // 解析 “how many Credits is glob prok Iron ?”
        if(matcher4.find()){
            answer(matcher4);
            return;
        }
        System.out.println("I have no idea what you are talking about");
    }

    /**
     * 计算语句中未知的参数
     * @param matcher
     */
    private void computeUnknown(Matcher matcher) {
        // 用来计算Silver的值，但有个条件，未知数应该是放在最后一个，否则是计算不了的。比如 glob glob Silver =34 ==> (1+1)*Silver = 34 ==> Silver=17
        String s = matcher.group(1).trim();
        String result = matcher.group(2).trim();

        String[] splits = s.split(" "); // 根据空格分割
        StringBuilder compute = new StringBuilder();

        String unknown = null;
        for(int i=0; i<splits.length; i++){
            String var1 = splits[i];
            if(i == splits.length-1){
                if(!externalRules.containsKey(var1)){
                    unknown = var1;
                }
            }else{
                if(!externalRules.containsKey(var1)){
                    System.out.println(var1+" does not have value!");
                    return ;
                }else{
                    compute.append((String) externalRules.get(var1));
                }
            }
        }
        if(null != unknown){
            int var2 = compute(compute.toString());
            if(var2>0){
                float aFloat = Float.valueOf(result);
                float unknownValue = aFloat/var2;
                externalRules.put(unknown, unknownValue);
            }
        }
    }

    /**
     * 自定义规则
     * @param matcher
     */
    private void selfDefineRules(Matcher matcher) {
        String externalKey = matcher.group(1).trim();
        String externalValue = matcher.group(2).trim();
        if(externalValue.length() ==1){
            String value = String.valueOf(externalValue.charAt(0));
            if(rules.containsKey(value)){
                externalRules.put(externalKey, value);
            }
        }
    }

    /**
     * 解析查询语句  “how much is pish tegj glob glob ?” 和 “how many Credits is glob prok Iron ?”
     * @param matcher
     */
    private void answer(Matcher matcher) {
        float multiValue = 0.0f;
        String content = matcher.group(2).trim();
        String[] s1 = content.split(" ");
        StringBuilder compute = new StringBuilder();
        for (String s : s1) {
            if (externalRules.containsKey(s)) {
                Object var3 = externalRules.get(s);
                if (var3 instanceof String) {
                    String var4 = (String) var3;
                    if (rules.containsKey(var4)) {
                        compute.append(var4);
                    } else {
                        System.out.println("unknown key:" + var4);
                        return;
                    }
                } else if (var3 instanceof Float) {
                    multiValue = (Float) var3;
                }
            }else{
                System.out.println("externalRules unknown key:" + s);
                return;
            }
        }
        int compute1 = compute(compute.toString());
        if (multiValue > 0) {
            System.out.println(content + " is " + compute1 * multiValue + " Credits");
        } else {
            System.out.println(content + " is " + compute1 *1.0 + " Credits");
        }
    }

    /**
     * 检查计算内容是否合法
     * @param s
     * @return
     */
    private boolean isValidate(String s){
        // rule: The symbols "I", "X", "C", and "M" can be repeated three times in succession, but no more. (They may appear four times if the third and fourth are separated by a smaller value, such as XXXIX.)
        // "D", "L", and "V" can never be repeated.
        String reg = "(.*IIII.*)|(.*XXXX.*)|(.*CCCC.*)|(.*MMMM.*)|(.*D{2,}.*)|(.*L{2,}.*)|(.*V{2,}.*)";
        if(s.matches(reg))  return false;
        else                return true;
    }

    /**
     * 有效字符串
     * @param s
     * @return
     */
    private boolean isNotEmpty(String s){
        return null!=s && s.length()>0;
    }

    /**
     * 主程序
     * @param args
     * 运行须知： 依次输入三部分数据
     *
     * ******1.自定义规则*******
     * glob is I
     * prok is V
     * pish is X
     * tegj is L
     *
     * ******2.根据自定义规则，计算其他值。注意需要求的参数放在最后，不然无法确定*******
     * glob glob Silver is 34 Credits           ==> (1+1)*Silver=34  ==>  Silver=17
     * glob prok Gold is 57800 Credits          ==> (-1+5)*Gold=57800  ==> Gold=14450
     * pish pish Iron is 3910 Credits           ==> (10+10)*Iron=3910  ==> Iron=195.5
     *
     * ******3.解析语义（目前支持解析以下两种方式的语义）*******
     * how much is pish tegj glob glob ?        ==> VLII = 42
     * how many Credits is glob prok Silver ?   ==> IV*Silver=(-1+5)*17=68
     *
     */
    public static void main(String[] args) {
        Merchant m = new Merchant();
        Scanner scan = new Scanner(System.in);
        while (scan.hasNext()){
            String words = scan.nextLine();
            m.semanticAnalysis(words);
        }
    }
}
