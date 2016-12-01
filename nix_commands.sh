lsof | wc -l
vmstat 15
cat /proc/7848/fd/1 | grep -P '(vmop|\d+\.\d+:)'
find . -name *.jar | xargs -i 7z l {} | less
find . -regex .*.jar | xargs -i unzip -l {} | less
pidstat -wt | sort -n -k5 | tail
pidstat -w -p 24355 | awk '{if (NR == 4) print $4 " " $5}'
tc qdisc add dev eth0 root netem delay 5000ms
tc qdisc del dev eth0 root netem delay 5000ms
