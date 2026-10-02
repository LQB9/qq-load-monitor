package com.lqb9.qqwatchmod;
import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Real native layouts/toggles and accessibility; only load values are synthetic. QQ is untouched. */
final class UiRuleCheck {
    private static int renders,checks;
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
    static String run(Instrumentation test){
        test.runOnMainSync(()->{
            try{
                int[] widths={360,300,260};float[] fonts={1f,1.3f,1.6f};
                for(int i=0;i<widths.length;i++){
                    Configuration cfg=new Configuration(test.getTargetContext().getResources().getConfiguration());cfg.fontScale=fonts[i];
                    Context c=test.getTargetContext().createConfigurationContext(cfg);
                    CpuLoadMonitor.Settings record=settings(true,true,false),auto=settings(true,true,true);
                    ThreadRuleSettingsView settings=new ThreadRuleSettingsView(c,record);
                    check(settings.toggle.isChecked() && !settings.action.isChecked(),"native default switches");
                    render(c,settings,"settings-record-"+widths[i],widths[i]);
                    settings.action.performClick();check(settings.savedState.getText().toString().contains("未保存"),"pending mode shown");
                    render(c,settings,"settings-draft-"+widths[i],widths[i]);
                    settings.markSaved(auto);check(!settings.savedState.getText().toString().contains("有修改"),"save clears draft state");
                    render(c,settings,"settings-auto-"+widths[i],widths[i]);
                    settings.toggle.performClick();check(!settings.ruleEnabled() && !settings.action.isEnabled(),"off disables action");
                    check(settings.action.getText().toString().contains("未生效") && !settings.action.isChecked(),"retained auto not shown active while off");
                    settings.markSaved(settings(true,false,true));
                    AccessibilityNodeInfo node=settings.toggle.createAccessibilityNodeInfo();check(node.isCheckable() && !node.isChecked(),"off accessibility state");node.recycle();
                    render(c,settings,"settings-off-"+widths[i],widths[i]);
                    for(int mode=0;mode<8;mode++){
                        ThreadRuleStatusView status=new ThreadRuleStatusView(c,12);ThreadRuleDisplay m=model(mode);status.update(m);
                        check(status.badge.getText().toString().equals(m.badge),"actual status label");
                        render(c,status,"detail-"+mode+"-"+widths[i],widths[i]);
                    }
                    DashboardView dashboard=new DashboardView(c);
                    CpuLoadMonitor.Sample sample=new CpuLoadMonitor.Sample(record,2000,185,0,false,"normal");
                    dashboard.update(sample,new QqCpuTracker.Snapshot(185,8,2000,Collections.emptyList(),"fixture"),new ThreadCpuTracker.Snapshot(Collections.emptyList(),3,0),Collections.emptyList(),Collections.emptyList(),true,0,false);
                    dashboard.updateScope(core(0),15,false);dashboard.singleRule.update(model(0));
                    check(dashboard.singleRule.getParent()!=null,"overview mounts actual single rule view");
                    render(c,dashboard,"overview-"+widths[i],widths[i]);
                    layout(c,widths[i]);
                    totalModes(c,widths[i]);
                    unified(c,widths[i]);
                }
            }catch(Exception failure){throw new RuntimeException(failure);}
        });
        return "PASS "+renders+" native thread UI renders; "+checks+" click/state/accessibility/layout checks. Synthetic load only; no window opened; QQ untouched\n";
    }
    private static void layout(Context c,int width)throws Exception {
        CpuLoadMonitor.Settings initial=settings(true,true,true);
        MonitorSettingsView settings=new MonitorSettingsView(c,initial);
        check(settings.selectedCores.getParent()==settings.scopeCard,"cores in independent scope card");
        check(settings.interval.getParent()==settings.scopeCard,"sampling in shared scope card");
        check(settings.totalSettings.getParent()==settings.totalCard && settings.cpu.getParent()==settings.totalSettings && settings.duration.getParent()==settings.totalSettings,"independent total card owns full rule settings");
        check(settings.singleSettings.getParent()==settings.singleCard,"single rule in separate card");
        check(settings.gifToggle.getParent()==settings.handlingCard,"GIF handler shared by rules");
        check(settings.draft(true).sameAs(initial),"full native settings retain saved configuration");
        LinearLayout settingsPage=page(c,settings);render(c,settingsPage,"layout-settings-"+width,width);
        ((ViewGroup)settings.selectedCores.getChildAt(0)).getChildAt(0).performClick();
        settings.interval.setText("3");settings.singleSettings.action.performClick();settings.gifToggle.performClick();
        CpuLoadMonitor.Settings draft=settings.draft(true);
        check(draft.coreMask==14 && draft.intervalSeconds==3 && draft.threshold==285 && draft.durationSeconds==3,"scope changes independent of total fields");
        check(draft.threadRule && !draft.threadHandle && !draft.gifRule,"independent draft switches captured");
        settings.updateApplied(settings(false,true,true));
        check(!settings.toggle.isChecked() && settings.draft(true).sameAs(draft),"external master update preserves all drafts");
        render(c,settingsPage,"layout-settings-draft-"+width,width);
        settings.markSaved(draft);check(settings.draft(true).sameAs(draft),"all groups normalize and save together");
        check(settings.gifToggle.getText().toString().contains("已保存"),"shared handler save label");
        MonitoringDetailView detail=new MonitoringDetailView(c);ThreadRuleDisplay display=model(0);
        QqCpuTracker.Snapshot proc=new QqCpuTracker.Snapshot(185,8,2000,Collections.emptyList(),"fixture");
        ThreadCpuTracker.Detail hot=new ThreadCpuTracker.Detail(new ThreadCpuTracker.Reading(100,101,"QQ","pool-40-thread-1",100,100,4),90);
        ThreadCpuTracker.Snapshot threads=new ThreadCpuTracker.Snapshot(Arrays.asList(hot),3,0);
        detail.update(display,proc,threads,"调度实际 CPU 时间 · 仅 QQ",false,new String[]{"模拟事件"});
        detail.singleRule.updateTrend(trendData(display.settings),display,false);
        check(detail.referenceBody.getVisibility()==View.GONE,"reference charts collapsed by default");
        LinearLayout detailPage=page(c,detail);render(c,detailPage,"layout-detail-"+width,width);
        detail.referenceToggle.performClick();detail.threadToggle.performClick();detail.update(display,proc,threads,"调度实际 CPU 时间 · 仅 QQ",false,new String[]{"模拟事件"});
        check(detail.referenceBody.getVisibility()==View.VISIBLE && detail.threadInfo.getText().toString().contains("TID 101"),"complete diagnostic retained when expanded");
        render(c,detailPage,"layout-detail-expanded-"+width,width);
        List<CoreFrequency.Core> f=new ArrayList<>();for(int id=0;id<8;id++)f.add(new CoreFrequency.Core(id,id!=7,id==6?-1:1100000,false));
        for(int mode:new int[]{1,4}){
            DashboardView overview=new DashboardView(c);
            CpuLoadMonitor.Sample total=new CpuLoadMonitor.Sample(initial,2000,300,4000,true,"reported");
            LoadHistory history=new LoadHistory();history.add(1000,200,1);history.add(2000,300,1);
            overview.update(total,proc,threads,f,history.snapshot(),true,mode==4?20:0,mode==4);
            overview.updateScope(core(0),15,mode==4);overview.singleRule.update(model(mode));
            overview.singleRule.updateTrend(trendData(model(mode).settings),model(mode),mode==4);
            check(overview.getChildCount()==3,"overview has only three monitoring cards");
            check(overview.singleRule.getClass()==detail.singleRule.getClass(),"overview and detail share actual thread chart component");
            check(overview.coreMetrics.frequencies.size()==8 && overview.coreMetrics.mask==15,"core load and frequency share core rows");
            render(c,overview,"layout-overview-"+mode+"-"+width,width);
        }
    }
    private static List<ThreadLoadHistory.Frame> trendData(CpuLoadMonitor.Settings settings){
        ThreadLoadHistory history=new ThreadLoadHistory();
        for(int i=0;i<30;i++){
            long end=2000+i*1000;CoreSnapshot snapshot=new CoreSnapshot("ui-trend",1000,i,end,end*1000000L,i!=12,0,"模拟线程曲线",new long[8],Collections.emptyList());
            List<CoreTracker.Detail> threads=Arrays.asList(t(101,1,"pool-40-thread-1",70+20*Math.sin(i*.3)),t(102,1,"GifRenderingExe",85+5*Math.cos(i*.3)),normal);
            history.add(new CoreTracker.Result(i==12?-1:185,new double[8],snapshot,(end-1000)*1000000L,threads,"fixture"),settings,end);
        }
        return history.snapshot();
    }
    private static void totalModes(Context c,int width)throws Exception {
        for(int mode=0;mode<3;mode++){
            CpuLoadMonitor.Settings s=new CpuLoadMonitor.Settings(true,285,3,2,"stop_task",15,true,true,80,10,true,mode!=1,mode==2);
            MonitorSettingsView settings=new MonitorSettingsView(c,s);
            check(settings.totalSettings.toggle.isChecked()==s.totalRule && settings.totalSettings.action.isChecked()==(s.totalRule && s.totalHandle),"total switch actual saved states");
            check(settings.totalSettings.action.isEnabled()==s.totalRule,"detector-off disables automatic control");
            check(settings.draft(true).sameAs(s),"total switch fields included in save draft");
            render(c,page(c,settings),"total-settings-"+mode+"-"+width,width);
            DashboardView overview=new DashboardView(c);CpuLoadMonitor.Sample sample=new CpuLoadMonitor.Sample(s,2000,300,s.totalRule?4000:0,true,"reported");
            overview.update(sample,new QqCpuTracker.Snapshot(300,8,2000,Collections.emptyList(),"fixture"),new ThreadCpuTracker.Snapshot(Collections.emptyList(),3,0),Collections.emptyList(),Collections.emptyList(),true,0,false);overview.updateScope(core(0),15,false);
            ThreadLoadMonitor.Result single=new ThreadLoadMonitor.Result(Arrays.asList(new ThreadLoadMonitor.Candidate(a,1,6000,false,false),new ThreadLoadMonitor.Candidate(b,1,12000,true,true)),"逐线程独立计时");
            ThreadRuleDisplay model=ThreadRuleDisplay.from(s,s,core(0),single,false);overview.singleRule.update(model);overview.singleRule.updateTrend(trendData(s),model,false);
            check(overview.ruleMode.getText().toString().contains(ThreadRuleReport.totalMode(s)),"overview total mode matches saved switches");
            check(overview.singleRule.trend.getVisibility()==View.VISIBLE && overview.singleRule.trend.chart.identities.size()==3,"separate single-identity chart present");
            check(overview.trend.showLimit==s.totalRule,"disabled total detector has no active limit line");
            if(!s.totalRule)check(overview.state.getCurrentTextColor()==CpuCharts.SUB,"disabled detection reference uses neutral color");
            render(c,overview,"total-overview-"+mode+"-"+width,width);
            settings.totalSettings.toggle.performClick();check(settings.totalSettings.savedState.getText().toString().contains("未保存"),"total dirty state explicit");
            check(settings.draft(true).threadHandle && settings.draft(true).threadRule,"total switch never changes single rule");
        }
    }
    private static LinearLayout page(Context c,View child){LinearLayout page=new LinearLayout(c);page.setOrientation(LinearLayout.VERTICAL);int p=DashboardView.dp(c,14);page.setPadding(p,p,p,p);page.setBackgroundColor(0xFFF5F6FA);page.addView(child,DashboardView.match());return page;}
    private static final class FakeControl implements CollectorSwitchView.Control {
        CollectorStatus status=new CollectorStatus(true,false,"已停止");
        java.util.function.Consumer<String> completion;
        public void query(java.util.function.Consumer<CollectorStatus> done){done.accept(status);}
        public void start(java.util.function.Consumer<String> done){completion=done;}
        public void stop(java.util.function.Consumer<String> done){completion=done;}
        void finish(String message){java.util.function.Consumer<String> done=completion;completion=null;done.accept(message);}
    }
    private static void unified(Context c,int width)throws Exception {
        String[] names={"总监控","GIF专项处理","单线程检测","自动处理"};
        for(boolean on:new boolean[]{true,false}){
            LinearLayout switches=new LinearLayout(c);switches.setOrientation(LinearLayout.VERTICAL);
            for(String name:names){StateSwitchView control=new StateSwitchView(c);control.show(name,on?"已开启":"已关闭",on,true);switches.addView(control,DashboardView.match());
                AccessibilityNodeInfo node=control.createAccessibilityNodeInfo();check(node.isCheckable() && node.isChecked()==on,"shared switch accessibility");node.recycle();}
            render(c,switches,"unified-"+(on?"on":"off")+"-"+width,width);
        }
        FakeControl fake=new FakeControl();CollectorSwitchView collector=new CollectorSwitchView(c,fake);collector.refresh();
        check(!collector.toggle.isChecked() && collector.toggle.isEnabled(),"collector initial off confirmed");render(c,collector,"collector-off-"+width,width);
        collector.toggle.performClick();check(!collector.toggle.isEnabled() && !collector.toggle.isChecked(),"start pending not optimistic on");render(c,collector,"collector-starting-"+width,width);
        fake.status=new CollectorStatus(true,true,"已运行");fake.finish("已启动");check(collector.toggle.isChecked() && collector.toggle.isEnabled(),"start reconciled with actual holder");render(c,collector,"collector-on-"+width,width);
        collector.toggle.performClick();check(collector.toggle.isChecked() && !collector.toggle.isEnabled(),"stop request not optimistic off");render(c,collector,"collector-stopping-"+width,width);
        fake.status=CollectorStatus.unknown("root查询失败");fake.finish("请求已发送");check(collector.toggle.getText().toString().contains("未知") && !collector.toggle.isChecked(),"uncertain stop unknown");render(c,collector,"collector-unknown-"+width,width);
        fake.status=new CollectorStatus(true,false,"已停止");collector.refresh();check(!collector.toggle.isChecked() && collector.toggle.isEnabled(),"stop later confirmed");render(c,collector,"collector-stopped-"+width,width);
        ProcessingHistory history=new ProcessingHistory();CpuLoadMonitor.Settings s=settings(true,true,false);
        ThreadLoadMonitor.Candidate single=new ThreadLoadMonitor.Candidate(a,1,12500,true,true);CpuLoadMonitor.Sample sample=new CpuLoadMonitor.Sample(s,2000,300,4000,true,"reported");
        for(int i=0;i<3;i++){
            CpuLoadMonitor.Settings rule=i==2?settings(true,true,true):s;
            CpuLoadMonitor.Sample total=i==1?new CpuLoadMonitor.Sample(s,2000,180,0,false,"normal"):sample;
            ThreadLoadMonitor.Candidate clock=i==0?new ThreadLoadMonitor.Candidate(a,1,2000,false,false):single;
            history.add(new ProcessingHistory.Row("ui-record-"+i,1000000000L+i*1000,a.thread,15,i==1?180:300,90,285,"QQ 后台",
                    i==0?"任务已结束":i==1?"仅记录":"待确认",i==0?"Future已确认返回":i==1?"单线程持续超限已记录，自动处理未开启":"等待目标进程确认任务结束","","","")
                    .rule(i==0?"合计":i==1?"单线程":"合计＋单线程",ThreadRuleReport.parameters(rule,total,clock)));
        }
        StatusTableView table=new StatusTableView(c);table.update(history);render(c,table,"table-rules-"+width,width);
    }
    private static CpuLoadMonitor.Settings settings(boolean on,boolean rule,boolean auto){return new CpuLoadMonitor.Settings(on,285,3,2,"stop_task",15,true,rule,80,10,auto);}
    private static CoreTracker.Detail t(int tid,long id,String name,double cpu){return new CoreTracker.Detail(new CoreSnapshot.Counter(100,1,tid,id,name,new long[8]),cpu);}
    private static final CoreTracker.Detail a=t(101,1,"pool-40-thread-1",90),b=t(102,1,"GifRenderingExe",85),normal=t(103,1,"QQ_DISPATCHER",10);
    private static CoreTracker.Result core(int mode){
        CoreSnapshot snapshot=new CoreSnapshot("ui",1000,1,2000,2000000000L,mode!=6,mode==7?-1:0,"调度事件不完整",new long[8],Collections.emptyList());
        return new CoreTracker.Result(mode==6?-1:185,new double[]{90,85,10,0,0,0,0,0},snapshot,1000000000L,Arrays.asList(a,b,normal),mode==6?"调度事件不完整，暂停处理":"调度实际 CPU 时间 · 仅 QQ");
    }
    private static ThreadRuleDisplay model(int mode){
        CpuLoadMonitor.Settings sampled=settings(true,true,false),current=mode==1?settings(true,true,true):mode==2?settings(true,false,true):mode==3?settings(false,true,true):sampled;
        if(mode!=5)sampled=current;else current=new CpuLoadMonitor.Settings(true,285,3,2,"stop_task",15,true,true,85,15,false);
        ThreadLoadMonitor.Result single=new ThreadLoadMonitor.Result(Arrays.asList(new ThreadLoadMonitor.Candidate(a,1,6000,false,false),new ThreadLoadMonitor.Candidate(b,1,12000,true,true)),"逐线程独立计时");
        return ThreadRuleDisplay.from(current,sampled,core(mode),single,mode==4);
    }
    private static void render(Context c,View view,String name,int dp)throws Exception{
        int width=DashboardView.dp(c,dp);view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,width,view.getMeasuredHeight());
        inspect(view);Bitmap bitmap=Bitmap.createBitmap(width,view.getMeasuredHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);view.draw(canvas);
        try(FileOutputStream stream=new FileOutputStream(new File(c.getFilesDir(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();renders++;
    }
    private static void inspect(View v){
        if(v.getVisibility()==View.GONE)return;
        if(v instanceof TextView && !(v instanceof EditText)){
            TextView t=(TextView)v;android.text.Layout layout=t.getLayout();check(layout!=null,"text layout exists");
            int width=t.getWidth()-t.getTotalPaddingLeft()-t.getTotalPaddingRight();
            for(int i=0;i<layout.getLineCount();i++)check(layout.getLineMax(i)<=width+2,"text overflow "+t.getText()+" line="+i+" drawn="+layout.getLineMax(i)+" available="+width);
            check(layout.getHeight()<=t.getHeight()-t.getTotalPaddingTop()-t.getTotalPaddingBottom()+2,"text height clipped "+t.getText());
        }
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View child=g.getChildAt(i);check(child.getRight()<=g.getWidth()+1 && child.getBottom()<=g.getHeight()+1,"child clipped");inspect(child);}}
    }
}
