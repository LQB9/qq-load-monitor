package com.lqb9.qqwatchmod;

/** Collector owns a POSIX write lease, even when its latest load sample is invalid. */
final class CollectorStatus {
    final boolean known,running;final String reason;
    CollectorStatus(boolean known,boolean running,String reason){this.known=known;this.running=running;this.reason=reason;}
    static CollectorStatus unknown(String reason){return new CollectorStatus(false,false,reason);}
    static CollectorStatus parse(String text,int exit){
        if(exit!=0)return unknown("读取失败，请确认 root 授权");
        if("QQCOLLECTOR_NO_LOCK_FILE".equals(text.trim()))return new CollectorStatus(true,false,"尚未启动精确采集");
        String[] lines=text.trim().split("\n");
        if(lines.length<2 || !"QQCOLLECTOR_LOCKS_END".equals(lines[lines.length-1].trim()))return unknown("采集状态读取不完整");
        try{
            String[] stat=lines[0].trim().split("\\s+");if(stat.length!=3 || !stat[0].equals("STAT"))return unknown("采集状态格式无效");
            long dev=Long.parseLong(stat[1]),inode=Long.parseLong(stat[2]);if(dev<0 || inode<=0)return unknown("采集锁身份无效");
            long major=((dev>>8)&0xfff)|((dev>>32)&0xfffff000L),minor=(dev&255)|((dev>>12)&0xffffff00L);
            for(int i=1;i<lines.length-1;i++){
                String[] f=lines[i].trim().split("\\s+");
                if(f.length<8 || !f[1].equals("POSIX") || !f[2].equals("ADVISORY") || !f[3].equals("WRITE") || !f[6].equals("0") || !f[7].equals("EOF"))continue;
                String[] id=f[5].split(":");
                if(id.length==3 && Long.parseLong(id[0],16)==major && Long.parseLong(id[1],16)==minor && Long.parseLong(id[2])==inode)
                    return new CollectorStatus(true,true,"精确采集运行中");
            }
            return new CollectorStatus(true,false,"精确采集已停止");
        }catch(RuntimeException bad){return unknown("采集状态解析失败");}
    }
}
