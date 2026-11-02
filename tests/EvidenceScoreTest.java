import online.donutsmp.client.EvidenceScore;
public class EvidenceScoreTest {
    static EvidenceScore.Counts counts(int chests,int machines,int work,int natural,boolean room){return new EvidenceScore.Counts(chests,0,0,0,0,machines,work,0,0,0,natural,room);}
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    public static void main(String[] args){
        check(EvidenceScore.evaluate(counts(0,0,0,0,false)).points()==0,"Clean chunk must stay clean");
        check(EvidenceScore.evaluate(counts(1,0,0,0,false)).points()<3,"One naturally generated chest must not meet default flag threshold");
        check(EvidenceScore.evaluate(counts(4,0,0,0,false)).points()==3.44,"Storage room should cross default threshold");
        check(EvidenceScore.evaluate(counts(10000,0,0,0,false)).points()==12,"Storage must be capped");
        double plain=EvidenceScore.evaluate(counts(4,4,4,0,true)).points();
        check(EvidenceScore.evaluate(counts(4,4,4,8,true)).points()==Math.round(plain*.5*100)/100.0,"Structure context must lower evidence");
        check(EvidenceScore.evaluate(counts(0,0,0,0,true)).points()==1,"Room bonus contributes once");
        check(EvidenceScore.evaluate(new EvidenceScore.Counts(0,0,0,0,0,0,0,0,0,1,0,false)).points()<3,"Natural spawner must not flag on its own");
        check(EvidenceScore.evaluate(counts(4,4,4,0,true)).details().stream().anyMatch(s->s.contains("Multiple evidence")),"Independent evidence receives synergy");
        System.out.println("8 evidence policy checks passed");
    }
}
