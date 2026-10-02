package com.lqb9.qqwatchmod;
import java.util.*;

public final class RuleMetadataTest {
    static int checks;static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static CpuLoadMonitor.Settings s(boolean on,boolean rule,boolean handle){return new CpuLoadMonitor.Settings(on,285,3,2,"stop_task",15,true,rule,80,10,handle);}
    static String locks(String entries){return "STAT 252 1129043\n"+entries+"QQCOLLECTOR_LOCKS_END\n";}
    public static void main(String[] args){
        check(CollectorStatus.parse("QQCOLLECTOR_NO_LOCK_FILE\n",0).known && !CollectorStatus.parse("QQCOLLECTOR_NO_LOCK_FILE\n",0).running,"missing lease file means stopped");
        check(!CollectorStatus.parse("",1).known,"root failure is unknown");
        check(!CollectorStatus.parse("STAT 252 1129043\n",0).known,"truncated read is unknown");
        check(CollectorStatus.parse(locks(""),0).known && !CollectorStatus.parse(locks(""),0).running,"existing unused file not running");
        check(CollectorStatus.parse(locks("1: POSIX ADVISORY WRITE 4471 00:fc:1129043 0 EOF\n"),0).running,"same actual lease running");
        check(!CollectorStatus.parse(locks("1: POSIX ADVISORY WRITE 4471 00:fd:1129043 0 EOF\n"),0).running,"same inode other device ignored");
        check(!CollectorStatus.parse(locks("1: POSIX ADVISORY WRITE 4471 00:fc:1129044 0 EOF\n"),0).running,"other inode ignored");
        check(!CollectorStatus.parse(locks("1: -> POSIX ADVISORY WRITE 4471 00:fc:1129043 0 EOF\n"),0).running,"waiting lock not holder");
        check(!CollectorStatus.parse(locks("1: POSIX ADVISORY READ 4471 00:fc:1129043 0 EOF\n"),0).running,"read lock not collector lease");
        check(!CollectorStatus.parse("STAT bad 1\nQQCOLLECTOR_LOCKS_END\n",0).known,"invalid stat unknown");
        check(!CollectorStatus.parse("STAT 252 0\nQQCOLLECTOR_LOCKS_END\n",0).known,"invalid inode unknown");
        check(CollectorStatus.parse("STAT 65070 1129043\n1: POSIX ADVISORY WRITE 4471 fe:2e:1129043 0 EOF\nQQCOLLECTOR_LOCKS_END\n",0).running,"hex filesystem major/minor parsed");
        CpuLoadMonitor.Settings record=s(true,true,false),auto=s(true,true,true);
        check(ThreadRuleReport.mode(record).equals("仅记录"),"record mode explicit");
        check(ThreadRuleReport.mode(auto).equals("自动处理"),"auto mode explicit");
        check(ThreadRuleReport.mode(s(true,false,true)).contains("未生效"),"disabled detector cannot advertise auto");
        check(ThreadRuleReport.mode(s(false,true,true)).contains("暂停"),"global pause explicit");
        check(ThreadRuleReport.settings(record).contains("单线程阈值=80% 单线程持续=10秒 单线程处理=仅记录"),"log current single settings complete");
        CoreSnapshot.Counter t=new CoreSnapshot.Counter(100,1,101,1,"worker",new long[8]);
        ThreadLoadMonitor.Candidate c=new ThreadLoadMonitor.Candidate(new CoreTracker.Detail(t,90),1,12500,true,true);
        CpuLoadMonitor.Sample total=new CpuLoadMonitor.Sample(record,2000,300,4000,true,"reported");
        String params=ThreadRuleReport.parameters(record,total,c);
        check(params.contains("合计 >285% / 3 秒 · 本次累计 4.0 秒"),"total required and actual elapsed");
        check(params.contains("单线程 >80% / 10 秒 · 本次累计 12.5 秒"),"single required and actual elapsed");
        check(params.contains("单线程模式 仅记录 · 共用采样 2 秒"),"row captures mode and sampling");
        ProcessingHistory.Row row=new ProcessingHistory.Row("metadata-fixture",1,t,15,300,90,285,"QQ 后台","仅记录","说明","","","").rule("合计＋单线程",params);
        ProcessingHistory history=new ProcessingHistory();history.add(row);history.update(row.id,"无法处理","原因");history.evidence(row.id,"worker","证据","执行");history.diagnostic(row.id,"诊断");
        check(history.find(row.id).ruleParameters.equals(params),"results/evidence retain exact trigger-time metadata");
        check(!ThreadRuleReport.parameters(auto,null,null).contains("本次累计 10.0"),"missing sample never invents elapsed");
        check(ThreadRuleReport.parameters(auto,null,null).contains("未计时"),"missing single clock explicit");
        System.out.println("PASS "+checks+" collector state and rule metadata checks");
    }
}
