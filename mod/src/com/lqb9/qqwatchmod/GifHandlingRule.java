package com.lqb9.qqwatchmod;

import java.util.Locale;

/** Owner-scoped GIF pause with complete-window selected-core attribution. */
final class GifHandlingRule implements TaskHandlingRule {
    private final GifTaskRule gif;
    private final TaskExecutions executions;
    GifHandlingRule(GifTaskRule gif,TaskExecutions executions) {this.gif=gif;this.executions=executions;}
    public String name(){return "GIF对象暂停";}
    public HandlingDecision apply(HandlingRequest request) {
        if(!request.settings.gifRule)return null;
        TaskExecutions.GifEvidence evidence=executions.gifEvidence(request.tid,request.windowNs,request.sampleNs);
        if(!evidence.dominant(request.allCpu))return null;
        if(request.expectedTask!=null)return new HandlingDecision("任务已变化","当前GIF并非持续超限确认的原任务","THREAD_BUSINESS_CHANGED",null);
        if(request.expectedGifOwner!=null)try {
            for(Object render:evidence.renders)if(new GifHostAdapter(render.getClass()).owner(render)!=request.expectedGifOwner)
                return new HandlingDecision("任务已变化","GIF对象已切换，持续时间不套用到新对象","THREAD_BUSINESS_CHANGED",null);
        }catch(Exception unavailable){return new HandlingDecision("未处理","持续GIF对象未能复核","THREAD_BUSINESS_CHANGED",null);}
        if(!evidence.dominatesSelected(request.selectedCpu,request.allCpu))return new HandlingDecision("未处理",
                "已观测GIF业务，但所选核心的归属证据不足；未用未选核心负载处理","GIF_SELECTED_ATTRIBUTION_INSUFFICIENT",null);
        GifTaskRule.Result result=gif.pause(request.id,evidence.renders,request.settings);
        String attribution="已观测GIF业务CPU="+String.format(Locale.ROOT,"%.1f",evidence.cpuNs/1000000d)
                +"ms · 匹配窗口="+evidence.windowNs/1000000L+"ms · 所选核心GIF贡献保守下限="
                +String.format(Locale.ROOT,"%.1f",evidence.selectedLowerPercent(request.selectedCpu,request.allCpu))+"%；";
        return new HandlingDecision(new GifTaskRule.Result(result.id,result.state,attribution+result.detail));
    }
}
