package com.lqb9.qqwatchmod;
import android.app.Instrumentation;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Production header/SAMPLE/THREAD_LIMIT export, and immutable row metadata; isolated load fixtures. */
final class RuleExportCheck {
    static void run(Instrumentation test)throws Exception{
        persistence(test);
        CpuLoadMonitor.Settings saved=WatchSettings.read();LoadSnapshot old=MonitorState.latest;
        CpuLoadMonitor.Settings record=new CpuLoadMonitor.Settings(true,285,3,2,"stop_task",15,true,true,80,10,false);
        CoreSnapshot.Counter t=new CoreSnapshot.Counter(100,1,101,2,"metadata-worker",new long[8]);
        CoreTracker.Detail hot=new CoreTracker.Detail(t,90);
        CoreSnapshot snapshot=new CoreSnapshot("export-fixture",android.os.Process.myUid(),1,2000,2000000000L,true,0,"fixture",new long[8],Arrays.asList(t));
        CoreTracker.Result core=new CoreTracker.Result(180,new double[8],snapshot,1000000000L,Arrays.asList(hot),"isolated load fixture");
        ThreadLoadMonitor.Candidate clock=new ThreadLoadMonitor.Candidate(hot,1,12500,true,true);
        ThreadLoadMonitor.Result single=new ThreadLoadMonitor.Result(Arrays.asList(clock),"逐线程独立计时");
        MonitorState.latest=new LoadSnapshot(new CpuLoadMonitor.Sample(record,2000,180,0,false,"normal"),new QqCpuTracker.Snapshot(180,8,2000,Collections.emptyList(),"fixture"),new ThreadCpuTracker.Snapshot(Collections.emptyList(),1,0),Collections.emptyList(),core,single);
        WatchSettings.settings=record;
        try{
            String sample=MonitorReport.snapshotText(MonitorState.latest);WatchLog.record("SAMPLE",sample);
            for(String wanted:new String[]{"单线程阈值=80% 单线程持续=10秒 单线程处理=仅记录","pidStart=1 tidStart=2 name=metadata-worker","selectedCpu=90.0% highMs=12500 requiredMs=10000 ready=true"})if(!sample.contains(wanted))throw new AssertionError("snapshot metadata missing "+wanted);
            for(ProcessingHistory.Row row:TaskBridge.history.snapshot()){
                if(!row.ruleParameters.contains("本次累计") || !row.ruleParameters.contains("单线程模式") || !row.ruleParameters.contains("共用采样"))throw new AssertionError("trigger metadata lost in row "+row.ruleParameters);
            }
            CountDownLatch done=new CountDownLatch(1);AtomicReference<String> path=new AtomicReference<>(),error=new AtomicReference<>();
            if(!WatchLog.export(test.getTargetContext(),MonitorReport.exportHeader(),(p,e)->{path.set(p);error.set(e);done.countDown();}))throw new AssertionError("export not queued");
            if(!done.await(8,TimeUnit.SECONDS) || path.get()==null)throw new AssertionError("export failed "+error.get());
            String name=path.get().substring(path.get().lastIndexOf('/')+1);android.content.ContentResolver resolver=test.getTargetContext().getContentResolver();android.net.Uri uri=null;
            try(android.database.Cursor cursor=resolver.query(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,new String[]{"_id"},"_display_name=?",new String[]{name},null)){
                if(cursor!=null && cursor.moveToFirst())uri=android.content.ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,cursor.getLong(0));
            }
            if(uri==null)throw new AssertionError("actual download file missing");
            try{
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();try(java.io.InputStream input=resolver.openInputStream(uri)){byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1)bytes.write(b,0,n);}
                String content=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
                for(String wanted:new String[]{"QQ负载监控日志 v"+ModuleInfo.VERSION,"THREAD_LIMIT","TOTAL_LIMIT","totalMode=仅记录","totalMode=自动处理","合计检测=开启","合计处理=自动处理","threadMode=仅记录","threadMode=自动处理","actionSources=2","sources=合计＋单线程","threadThreshold=80%","totalThreshold=200","windowStartNs=","requiredMs=10000","SAMPLE","处理状态表不导出"})if(!content.contains(wanted))throw new AssertionError("export missing "+wanted);
                if(content.contains("本次累计") || content.contains("最近 100 条"))throw new AssertionError("table copied into export");
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(test.getTargetContext().getFilesDir(),"actual-rule-export.txt"))){out.write(bytes.toByteArray());}
            }finally{resolver.delete(uri,null,null);}
        }finally{MonitorState.latest=old;WatchSettings.settings=saved;}
    }
    private static void persistence(Instrumentation test)throws Exception {
        java.io.File dir=new java.io.File(test.getTargetContext().getFilesDir(),"independent-total-settings");
        SettingsStore store=new SettingsStore(dir);if(!store.cfgSet(ModuleInfo.F_ON,"1"))throw new AssertionError("isolated global switch write failed");
        try{
            for(boolean rule:new boolean[]{false,true}){
                CpuLoadMonitor.Settings full=new CpuLoadMonitor.Settings(true,10000,3600,3600,"stop_task",255,false,true,100,3600,true,rule,false);
                if(!store.save(full) || !store.read().sameAs(full))throw new AssertionError("actual atomic file lost total/thread switches or long settings");
            }
            CpuLoadMonitor.Settings retain=new CpuLoadMonitor.Settings(true,285,3,2,"stop_task",255,true,true,80,10,true,false,true);
            if(!store.save(retain) || !store.read().sameAs(retain))throw new AssertionError("detector off lost retained handling preference");
            if(!store.cfgSet(ModuleInfo.F_LOAD,"cpu=285\nduration=3\ninterval=2\ncores=255\nthreadHandle=true"))throw new AssertionError("legacy file write failed");
            CpuLoadMonitor.Settings old=store.read();if(!old.totalRule || !old.totalHandle || !old.threadHandle || old.threshold!=285 || old.intervalSeconds!=2)throw new AssertionError("legacy migration changed effective rules");
        }finally{for(java.io.File f:dir.listFiles())if(!f.delete())throw new AssertionError("temporary settings cleanup failed");if(!dir.delete())throw new AssertionError("temporary directory cleanup failed");}
    }
}
