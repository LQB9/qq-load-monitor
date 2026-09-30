#!/system/bin/sh
# ============================================================
#  QQ 看门狗 · 守护脚本（独立版，不依赖 LSPosed）
# ------------------------------------------------------------
#  判定式：动态上限 = 基线 + K × 本进程 CPU%
#          CPU% 从 /proc/<pid>/stat 的 utime+stime 增量算，**不看 /proc/loadavg**
#          —— 实测 QQ 空闲时本进程 CPU 只有 1.8~3.5%，系统 loadavg 却能到 9.69
#             （别的进程在忙），用 loadavg 会乱杀。
#  连续 NEED 次超过上限 → kill -9（QQ 会自己把进程拉起来）
#  守护启动后 GUARD 秒内只观察不判
#
#  用法：
#    sh qqwatch.sh <BASE> <K> <SCOPE> <INTERVAL> <STATEDIR> [DRYRUN]
#      BASE      基线线程数；0 = 自动（每个进程各自记住第一次看到的线程数）
#      K         系数 0~200
#      SCOPE     all（都看）| main（只看主进程）| worker（只看子进程）
#      INTERVAL  采样间隔秒
#      STATEDIR  状态目录（每进程一个小文件）
#      DRYRUN    1 = 只报告不杀
#
#  ⚠️ Android 的 sh 是 **32 位整数**！实测 $((1790740393*1000)) 会变成负数。
#     所以这里绝不把 epoch/uptime 乘成大数：时间只保存 秒+百分秒，增量先减后乘。
#
#  输出协议（stdout，App 照着解析）：
#    HELLO pid=<守护pid> sh=<脚本路径>
#    BEGIN <epoch 秒>
#    CFG scope=all k=3 iv=30 guard=60 need=3 min=150 dry=0 auto=1
#    P kind=main pid=28202 threads=311 limit=311 cpu=1.2 base=311 over=0 acted=0
#    END
#    E <事件/错误>
#  acted: 0=没动手 1=已杀 2=演练(本来要杀) -1=杀失败
# ============================================================

BASE=$1
K=$2
SCOPE=$3
IV=$4
ST=$5
DRY=$6

TARGET=com.tencent.mobileqq
LIMIT_MIN=150
GUARD=60
NEED=3

[ -z "$BASE" ] && BASE=0
[ -z "$K" ] && K=3
[ -z "$SCOPE" ] && SCOPE=all
[ -z "$IV" ] && IV=30
[ -z "$ST" ] && ST=/data/local/tmp/qqwatch
[ -z "$DRY" ] && DRY=0

mkdir -p "$ST" 2>/dev/null

# 时间：秒 + 百分秒，分开存，永远不做大数乘法
now_s() { u=$(cut -d' ' -f1 /proc/uptime); echo "${u%.*}"; }
# cpu1000（千分之一%）→ "12.3" 这种可读百分比
# ⚠️ 曾经写错成 cpu1000/10，打印出来放大 100 倍（1.325% 显示成 132.5）
fmt_cpu() {
  echo "$(( $1 / 1000 )).$(( ($1 / 100) % 10 ))"
}

now_c() { u=$(cut -d' ' -f1 /proc/uptime); f=${u#*.}; echo "${f}0" | cut -c1-2; }

echo "HELLO pid=$$ sh=$0"

T0=$(now_s)
AUTO=$([ "$BASE" = 0 ] && echo 1 || echo 0)

cfglines() {
  echo "CFG scope=$SCOPE k=$K iv=$IV guard=$GUARD need=$NEED min=$LIMIT_MIN dry=$DRY auto=$AUTO"
}

echo "BEGIN $(date +%s)"
cfglines
echo "END"

ROUND=0
while :; do
  sleep "$IV"
  ROUND=$(( ROUND + 1 ))
  S=$(now_s)
  C=$(now_c)
  AGE=$(( S - T0 ))

  # 一次 ps 拿到所有目标进程（比遍历 /proc 快两个数量级：1026 个进程只要 0.05s）
  ps -A -o PID,ARGS 2>/dev/null | awk -v t="$TARGET" '
    index($2, t) == 1 {
      kind = ($2 == t) ? "main" : "worker";
      print kind, $1;
    }' > "$ST/pids.$$" 2>/dev/null

  echo "BEGIN $(date +%s)"
  cfglines

  while read -r kind p; do
    [ -z "$p" ] && continue
    case "$SCOPE" in
      main)   [ "$kind" = main ]   || continue ;;
      worker) [ "$kind" = worker ] || continue ;;
    esac

    # ---- 采样（只用 read，不起外部进程）----
    read -r STAT < /proc/$p/stat 2>/dev/null || continue
    [ -z "$STAT" ] && continue
    REST=${STAT##*) }        # 去掉 "pid (comm) "
    set -- $REST             # $1=state … ${12}=utime ${13}=stime
    ticks=$(( ${12} + ${13} ))
    th=0
    while read -r L; do
      case "$L" in
        Threads:*) th=${L#Threads:}; set -- $th; th=$1; break ;;
      esac
    done < /proc/$p/status 2>/dev/null
    [ -z "$th" ] && th=0

    # ---- 读上一次状态：ticks 秒 百分秒 基线 超限次数 ----
    pf="$ST/p.$p"
    PT=0; PS=0; PC=0; PB=0; PO=0
    if [ -f "$pf" ]; then read PT PS PC PB PO < "$pf" 2>/dev/null; fi
    [ -z "$PB" ] && PB=0
    [ -z "$PO" ] && PO=0
    [ -z "$PT" ] && PT=0
    [ -z "$PS" ] && PS=0
    [ -z "$PC" ] && PC=0

    # 基线：显式给了用给的；否则每个进程各自记住第一次看到的值
    if [ "$PB" = 0 ]; then
      if [ "$BASE" != 0 ]; then PB=$BASE; else PB=$th; fi
    fi

    # ---- CPU%：dc 个 tick（USER_HZ=100，1 tick = 10ms）落在 dtcs 个百分秒里 ----
    # cpu% = dc/dtcs*100 ；为保精度取千分之一： cpu1000 = dc*100000/dtcs
    # ⚠️ dc 先夹到 20000，否则 dc*100000 会撑爆 32 位（Android 的 sh 是 32 位整数）
    cpu1000=0
    if [ "$PT" != 0 ] || [ "$PS" != 0 ]; then
      dc=$(( ticks - PT ))
      dS=$(( S - PS ))
      dC=$(( C - PC ))
      dtcs=$(( dS * 100 + dC ))
      [ "$dc" -lt 0 ] && dc=0
      [ "$dc" -gt 20000 ] && dc=20000
      if [ "$dtcs" -gt 0 ]; then
        cpu1000=$(( dc * 100000 / dtcs ))
      fi
    fi

    lim=$(( PB + K * cpu1000 / 1000 ))
    [ "$lim" -lt "$LIMIT_MIN" ] && lim=$LIMIT_MIN

    over=$PO
    if [ "$th" -gt "$lim" ]; then over=$(( PO + 1 )); else over=0; fi

    acted=0
    if [ "$over" -ge "$NEED" ] && [ "$AGE" -gt "$GUARD" ]; then
      if [ "$DRY" = 1 ]; then
        acted=2
        echo "E DRY-KILL pid=$p kind=$kind threads=$th limit=$lim"
      else
        if kill -9 "$p" 2>/dev/null; then
          acted=1
          echo "E KILL pid=$p kind=$kind threads=$th limit=$lim cpu=$(fmt_cpu $cpu1000)% base=$PB"
        else
          acted=-1
          echo "E KILL-FAIL pid=$p"
        fi
      fi
      over=0
      rm -f "$pf" 2>/dev/null
      if [ "$acted" = 1 ]; then
        printf '%s\n' "$th" > "$ST/lastkill.tmp.$$" 2>/dev/null \
          && mv -f "$ST/lastkill.tmp.$$" "$ST/lastkill" 2>/dev/null
      fi
    else
      printf '%s %s %s %s %s\n' "$ticks" "$S" "$C" "$PB" "$over" > "$pf.tmp.$$" 2>/dev/null \
        && mv -f "$pf.tmp.$$" "$pf" 2>/dev/null
    fi

    echo "P kind=$kind pid=$p threads=$th limit=$lim cpu=$(fmt_cpu $cpu1000) base=$PB over=$over acted=$acted"
  done < "$ST/pids.$$"
  rm -f "$ST/pids.$$" 2>/dev/null

  # 每 20 轮清一次陈旧状态文件（超过 12 小时没动过的）
  if [ $(( ROUND % 20 )) = 0 ]; then
    find "$ST" -name 'p.*' -mmin +720 -delete 2>/dev/null
    find "$ST" -name '*.tmp.*' -mmin +60 -delete 2>/dev/null
  fi

  echo "END"
done
