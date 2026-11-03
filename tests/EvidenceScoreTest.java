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
        String formatted=online.donutsmp.client.NotificationText.format("{points} at {x} {y} {z}; {chunkX},{chunkZ}",6.25,120,64,-80,7,-5);
        check(formatted.equals("6.25 at 120 64 -80; 7,-5"),"All notification placeholders must expand");
        check(online.donutsmp.client.NotificationText.format("100% literal {unknown}",1,0,0,0,0,0).equals("100% literal {unknown}"),"Literal and unknown template text must be preserved");
        var forward=online.donutsmp.client.FreecamMotion.step(0,1,0,0,1,false);
        check(Math.abs(forward.z()-.25)<1e-9&&Math.abs(forward.x())<1e-9,"Forward at yaw zero is positive Z");
        var east=online.donutsmp.client.FreecamMotion.step(90,1,0,0,1,false);
        check(Math.abs(east.x()+.25)<1e-9,"Yaw rotates freecam movement");
        var diagonal=online.donutsmp.client.FreecamMotion.step(0,1,1,1,1,false);
        double length=Math.sqrt(diagonal.x()*diagonal.x()+diagonal.y()*diagonal.y()+diagonal.z()*diagonal.z());
        check(Math.abs(length-.25)<1e-9,"Diagonal/vertical movement cannot boost camera speed");
        check(Math.abs(online.donutsmp.client.FreecamMotion.step(0,1,0,0,1,true).z()-.75)<1e-9,"Sprint boost triples movement");
        System.out.println("14 scoring, notification and camera checks passed");
    }
}
