package com.nettyrpc.test;

import com.nettyrpc.Merchant;
import org.junit.Before;
import org.junit.Test;

public class TestMerchant {

    Merchant m = new Merchant();

    @Before
    public void init(){
        m.semanticAnalysis("glob is I");
        m.semanticAnalysis("prok is V");
        m.semanticAnalysis("pish is X");
        m.semanticAnalysis("tegj is L");
        m.semanticAnalysis("glob glob Silver is 34 Credits");
        m.semanticAnalysis("glob prok Gold is 57800 Credits");
        m.semanticAnalysis("pish pish Iron is 3910 Credits");
    }

    @Test
    public void test01(){
        m.semanticAnalysis("how much is pish tegj glob glob ?");
        m.semanticAnalysis("how many Credits is glob prok Silver ?");
        m.semanticAnalysis("how many Credits is glob prok Gold ?");
        m.semanticAnalysis("how many Credits is glob prok Iron ?");
        m.semanticAnalysis("how much wood could a woodchuck chuck if a woodchuck could chuck wood ?");
    }
}
