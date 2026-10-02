package com.lqb9.qqwatchmod;

import java.util.*;

public final class CoreTimeWindowTest {
    private static int checks;
    private static void check(boolean ok,String name) { checks++; if(!ok)throw new AssertionError(name); }
    private static CoreTimeWindow.Frame frame(long before,long after,long runtime) {
        return new CoreTimeWindow.Frame(Collections.singletonMap("pid:start",before),Collections.singletonMap("pid:start",after),runtime);
    }
    public static void main(String[] args) {
        CoreTimeWindow.Frame old=frame(35,58,580026605L),now=frame(100,101,986606046L);
        CoreTimeWindow.Result aligned=CoreTimeWindow.compare(old,now,100);
        check(aligned.comparable && !aligned.mismatch && !aligned.rollback,"discovery time skew does not falsely reject bracketed runtime");
        check(aligned.lowerNs==420000000 && aligned.upperNs==660000000,"actual trace point lies within CPU read bounds");
        check(650000000>aligned.allowedNs,"old unaligned boundary would reject reported 23:04 case");
        CoreTimeWindow.Result missing=CoreTimeWindow.compare(frame(0,0,0),frame(65,65,406579441),100);
        check(missing.mismatch,"unambiguous missing runtime remains invalid at original allowance");
        check(missing.allowedNs==588224301,"25 percent plus 80ms tolerance retained");
        check(!CoreTimeWindow.compare(frame(0,0,0),frame(8,8,0),100).mismatch,"exact 80ms boundary not over threshold");
        check(CoreTimeWindow.compare(frame(0,0,0),frame(9,9,0),100).mismatch,"beyond fixed allowance rejected");
        check(CoreTimeWindow.compare(frame(90,91,10),frame(89,92,20),100).rollback,"counter rollback does not become zero load");
        check(CoreTimeWindow.compare(frame(0,0,100),frame(10,10,99),100).rollback,"trace rollback rejected");
        check(!CoreTimeWindow.compare(null,now,100).comparable,"first capture establishes baseline");
        CoreTimeWindow.Frame reused=new CoreTimeWindow.Frame(Collections.singletonMap("pid:new",100L),Collections.singletonMap("pid:new",101L),999999999L);
        check(!CoreTimeWindow.compare(old,reused,100).comparable,"PID reuse starts a new baseline");
        CoreTimeWindow.Frame changed=new CoreTimeWindow.Frame(old.before,Collections.singletonMap("new",58L),100);
        check(!CoreTimeWindow.compare(old,changed,100).comparable,"process change inside read bracket is invalid");
        Map<String,Long> mutable=new HashMap<>();mutable.put("identity",1L);
        CoreTimeWindow.Frame immutable=new CoreTimeWindow.Frame(mutable,mutable,0);mutable.put("identity",99L);
        check(immutable.before.get("identity")==1,"published brackets detached from later reads");
        try {CoreTimeWindow.compare(old,now,0);throw new AssertionError("zero clock ticks accepted");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("PASS "+checks+" aligned CPU/trace window checks");
    }
}
