package com.lqb9.qqwatchmod;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.Consumer;

/** Start/stop one collector switch, reconciled with the real lease rather than optimistic state. */
final class CollectorSwitchView extends LinearLayout {
    interface Control {
        void query(Consumer<CollectorStatus> done);
        void start(Consumer<String> done);
        void stop(Consumer<String> done);
    }
    final StateSwitchView toggle;
    final TextView note;
    private final Control control;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private CollectorStatus state=CollectorStatus.unknown("点击检查采集状态");
    private boolean busy,checking,resumed;
    private long generation;
    CollectorSwitchView(Context c,Control control){
        super(c);this.control=control;setOrientation(VERTICAL);
        toggle=new StateSwitchView(c);addView(toggle,DashboardView.match());
        note=DashboardView.label(c,"精确采集独立于监控开关；需要 root，手机重启后须重新开启。",11,CpuCharts.SUB);note.setPadding(0,DashboardView.dp(c,8),0,0);addView(note);
        toggle.setOnClickListener(v->{if(busy || checking)return;if(!state.known){refresh();return;}operate(!state.running);});paint();
    }
    void resume(){resumed=true;refresh();handler.removeCallbacks(poll);handler.postDelayed(poll,5000);}
    void pause(){resumed=false;handler.removeCallbacks(poll);}
    private final Runnable poll=new Runnable(){public void run(){if(resumed){refresh();handler.postDelayed(this,5000);}}};
    void refresh(){if(busy || checking)return;checking=true;long token=generation;paint();control.query(value->onUi(()->{if(token!=generation)return;checking=false;state=value;paint();}));}
    private void operate(boolean target){
        busy=true;checking=false;long token=++generation;paint();note.setText(target?"等待 root 授权与内核检查…":"正在请求停止，等待采集器释放运行锁…");
        Consumer<String> done=message->onUi(()->{if(token!=generation)return;note.setText(message);reconcile(target,token,0);});
        if(target)control.start(done);else control.stop(done);
    }
    private void reconcile(boolean target,long token,int attempt){
        control.query(value->onUi(()->{
            if(token!=generation)return;
            state=value;
            if(value.known && value.running!=target && attempt<4){handler.postDelayed(()->{if(token==generation)reconcile(target,token,attempt+1);},400);return;}
            busy=false;paint();
            if(!value.known)note.setText(value.reason+"；尚未确认实际状态。");
            else if(value.running!=target)note.setText("未确认"+(target?"启动":"停止")+"完成；实际状态："+value.reason);
        }));
    }
    private void paint(){
        String label=busy?"正在操作，请稍候":checking?"正在检查实际状态":!state.known?"状态未知 · 点击检查":state.running?"已开启 · 点击停止":"已关闭 · 点击启动";
        toggle.show("精确核心采集",label,state.known && state.running,!busy && !checking);
        if(!busy && !checking && !state.known)note.setText(state.reason);
    }
    private void onUi(Runnable action){if(Looper.myLooper()==Looper.getMainLooper())action.run();else handler.post(action);}
    @Override protected void onDetachedFromWindow(){pause();generation++;busy=false;checking=false;state=CollectorStatus.unknown("等待重新检查采集状态");handler.removeCallbacksAndMessages(null);super.onDetachedFromWindow();}
}
