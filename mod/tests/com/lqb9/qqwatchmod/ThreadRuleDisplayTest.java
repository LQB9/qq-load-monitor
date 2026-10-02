package com.lqb9.qqwatchmod;
import java.util.*;

/** Presentation must not turn disabled/stale/changed/identity-reused samples into normal or handled states. */
public final class ThreadRuleDisplayTest {
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static CpuLoadMonitor.Settings s(boolean on,boolean rule,boolean handle,int duration){return new CpuLoadMonitor.Settings(on,285,3,2,"stop_task",15,true,rule,80,duration,handle);}
    static CoreTracker.Detail t(int tid,long identity,double cpu){return new CoreTracker.Detail(new CoreSnapshot.Counter(100,1,tid,identity,"worker-"+tid,new long[8]),cpu);}
    static CoreTracker.Result core(boolean valid,int fg,CoreTracker.Detail...threads){return new CoreTracker.Result(valid?190:-1,new double[8],new CoreSnapshot("ui",1000,1,2000,2000000000L,valid,fg,"调度事件不完整",new long[8],Collections.emptyList()),1000000000L,Arrays.asList(threads),"调度事件不完整，暂停处理");}
    static ThreadLoadMonitor.Result result(ThreadLoadMonitor.Candidate...c){return new ThreadLoadMonitor.Result(Arrays.asList(c),"逐线程独立计时");}
    static ThreadRuleDisplay view(CpuLoadMonitor.Settings s,CoreTracker.Result c,ThreadLoadMonitor.Result r){return ThreadRuleDisplay.from(s,s,c,r,false);}
    public static void main(String[] args){
        CpuLoadMonitor.Settings record=s(true,true,false,10),auto=s(true,true,true,10);
        CoreTracker.Detail a=t(101,1,90),b=t(102,1,85),normal=t(103,1,80);
        ThreadLoadMonitor.Candidate ca=new ThreadLoadMonitor.Candidate(a,1,6000,false,false),cb=new ThreadLoadMonitor.Candidate(b,1,12000,true,true);
        CoreTracker.Result data=core(true,0,normal,b,a,t(100,1,95));
        ThreadRuleDisplay m=view(record,data,result(ca,cb));
        check(m.valid && m.active,"record mode is active and valid");
        check(m.badge.contains("仅记录"),"record mode explicit");
        check(m.rows.size()==3 && m.rows.get(0).thread==a,"protected main excluded and sorted");
        check(m.high==2 && m.ready==1,"counts use qualified identity clocks");
        check(m.timing(m.rows.get(0)).contains("6.0 / 10"),"independent elapsed progress");
        check(m.timing(m.rows.get(1)).contains("已达时长，仅记录"),"qualified record is not successful stop");
        check(m.timing(m.rows.get(2)).contains("未超限"),"strict greater threshold");
        check(m.parameters().contains("CPU 0,1,2,3"),"selected core scope shown");
        check(m.parameters().contains("采样 2 秒"),"shared sampling shown");
        m=view(auto,data,result(ca,cb));
        check(m.badge.contains("自动处理"),"auto mode explicit");
        check(m.timing(m.rows.get(1)).contains("待处理复核") && !m.timing(m.rows.get(1)).contains("成功"),"duration never claims action success");
        m=view(s(true,false,true,10),data,result(ca,cb));
        check(!m.active && !m.valid && m.rows.isEmpty() && m.badge.contains("检测已关闭"),"detector off suppresses retained auto and clocks");
        m=view(s(false,true,true,10),data,result(ca,cb));
        check(!m.active && m.badge.contains("总监控关闭") && m.rows.isEmpty(),"global switch pauses single rule");
        m=ThreadRuleDisplay.from(record,record,data,result(ca,cb),true);
        check(m.active && !m.valid && m.rows.isEmpty() && m.badge.contains("过期"),"stale not normal and no stale progress");
        m=ThreadRuleDisplay.from(auto,record,data,result(ca,cb),false);
        check(!m.valid && m.badge.contains("新设置") && m.rows.isEmpty(),"applied setting overrides old samples");
        m=view(record,core(false,0,a),result(ca));check(!m.valid && m.reason.contains("不完整") && m.rows.isEmpty(),"invalid reason retained");
        m=view(record,core(true,-1,a),result(ca));check(!m.valid && m.reason.contains("前后台未知"),"unknown foreground explicit");
        m=ThreadRuleDisplay.from(record,null,null,null,false);check(!m.valid && m.badge.contains("等待采样"),"no samples not zero abnormal count");
        m=view(record,data,ThreadLoadMonitor.Result.EMPTY);check(!m.valid && m.rows.isEmpty(),"detector warming clocks hidden");
        m=view(record,core(true,1,t(101,2,90)),result(ca));check(m.rows.get(0).streak==null && m.high==0,"TID reuse does not inherit display clock");
        m=view(record,core(true,1,t(101,1,70)),result(ca));check(m.rows.get(0).streak==null && m.high==0,"different CPU sample does not inherit clock");
        m=view(record,core(true,0),result());check(m.valid && m.high==0 && m.rows.isEmpty(),"valid idle distinct from invalid");
        CoreTracker.Detail protectedThread=new CoreTracker.Detail(new CoreSnapshot.Counter(100,1,104,1,"qqwatch-dog",new long[8]),90);
        m=view(record,core(true,0,protectedThread,t(105,1,Double.NaN),t(106,1,111)),result());check(m.rows.isEmpty(),"protected and impossible CPU excluded");
        m=view(s(true,true,false,0),core(true,0,a),result(new ThreadLoadMonitor.Candidate(a,1,1000,true,true)));
        check(m.timing(m.rows.get(0)).contains("1.0 / 0 秒"),"zero duration still reports actual elapsed");
        System.out.println("PASS "+checks+" thread rule presentation-state checks");
    }
}
