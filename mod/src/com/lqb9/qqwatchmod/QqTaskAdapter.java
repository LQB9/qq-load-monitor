package com.lqb9.qqwatchmod;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;

/** Five reference APK contracts. This binds business identity; it never guesses a stop method. */
final class QqTaskAdapter {
    static final String JOB="com.tencent.mobileqq.app.Job";
    private static final String[] VERSIONS={"9.3.35","9.3.50","9.3.55","9.3.60","9.3.65"};
    private static final long[] CODES={15560,15730,15900,16070,16240};
    private static volatile String version="unknown";
    private static volatile boolean jobEnabled;
    private static final Map<Class<?>,JobContract> contracts=new HashMap<>();
    static synchronized String configure(String pkg,String name,long code) {
        jobEnabled=false;version=name==null?"unknown":name;
        if(ModuleInfo.QQ_PACKAGE.equals(pkg)) for(int i=0;i<VERSIONS.length;i++)
            if(VERSIONS[i].equals(name) && CODES[i]==code){jobEnabled=true;break;}
        return description();
    }
    static String description() {
        return "QQ任务适配="+version+" Job="+(jobEnabled?"五版接口已核对，执行时复核":"未匹配参考版本，保留通用规则")
                +" GIF=公共接口运行时复核 Future=平台协作取消";
    }
    static final class JobContract {
        final Field body,redirector;
        JobContract(Class<?> type)throws Exception {
            if(!type.getName().equals(JOB) || type.getSuperclass()!=WeakReference.class || !Runnable.class.isAssignableFrom(type))
                throw new IllegalArgumentException("QQ Job type changed");
            Method run=type.getMethod("run"),get=type.getMethod("get");
            if(run.getDeclaringClass()!=type || run.getReturnType()!=void.class || Modifier.isStatic(run.getModifiers())
                    || get.getDeclaringClass()!=Reference.class || get.getReturnType()!=Object.class)
                throw new IllegalArgumentException("QQ Job delegation changed");
            Field found=null;
            for(Field f:type.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers()) && f.getType()==Runnable.class){
                if(found!=null)throw new IllegalArgumentException("QQ Job body ambiguous");found=f;
            }
            if(found==null || !found.getName().equals("mJob"))throw new IllegalArgumentException("QQ Job body changed");
            body=found;body.setAccessible(true);
            redirector=type.getDeclaredField("$redirector_");
            if(!Modifier.isStatic(redirector.getModifiers()) || !redirector.getType().getName().equals("com.tencent.mobileqq.qfix.redirect.IPatchRedirector"))
                throw new IllegalArgumentException("QQ Job patch guard changed");
            redirector.setAccessible(true);
        }
        Object body(Object job,boolean executing)throws Exception {
            // A hotfix can replace run/checkShouldRun. Do not invoke patch callbacks or assume the APK contract remains valid.
            if(redirector.get(null)!=null)throw new IllegalArgumentException("QQ Job hotfix active; binding not verified");
            Object value=body.get(job);
            if(value==null && !executing)value=((Reference<?>)job).get();
            return value instanceof Runnable?value:null;
        }
    }
    private static synchronized JobContract contract(Class<?> type)throws Exception {
        JobContract c=contracts.get(type);
        if(c==null){if(contracts.size()>=8)throw new IllegalArgumentException("QQ Job class-loader limit");c=new JobContract(type);contracts.put(type,c);}
        return c;
    }
    static Object body(Object job,boolean executing)throws Exception {
        if(!jobEnabled || job==null || !job.getClass().getName().equals(JOB))return null;
        return contract(job.getClass()).body(job,executing);
    }
    static boolean activeBusiness(Object job,Object business) {
        try{return business!=null && body(job,true)==business;}catch(Exception unverified){return false;}
    }
    static boolean platformRun(Object task) {
        try{return task instanceof java.util.concurrent.FutureTask && task.getClass().getMethod("run").getDeclaringClass()==java.util.concurrent.FutureTask.class;}
        catch(Exception unknown){return false;}
    }
}
