# Heavy sub-function called via "return run function" from profiled_return_fn.
# The 20 NBT writes produce a measurable duration so the test can verify that
# the parent timer includes the sub-function time (proving timer.end() fires
# after the return run completes, not before).
#!jvmtimer profile file
data modify storage sniffer_test:log sub_executed set value 1
data modify storage sniffer_test:log heavy_01 set value 1
data modify storage sniffer_test:log heavy_02 set value 2
data modify storage sniffer_test:log heavy_03 set value 3
data modify storage sniffer_test:log heavy_04 set value 4
data modify storage sniffer_test:log heavy_05 set value 5
data modify storage sniffer_test:log heavy_06 set value 6
data modify storage sniffer_test:log heavy_07 set value 7
data modify storage sniffer_test:log heavy_08 set value 8
data modify storage sniffer_test:log heavy_09 set value 9
data modify storage sniffer_test:log heavy_10 set value 10
data modify storage sniffer_test:log heavy_11 set value 11
data modify storage sniffer_test:log heavy_12 set value 12
data modify storage sniffer_test:log heavy_13 set value 13
data modify storage sniffer_test:log heavy_14 set value 14
data modify storage sniffer_test:log heavy_15 set value 15
data modify storage sniffer_test:log heavy_16 set value 16
data modify storage sniffer_test:log heavy_17 set value 17
data modify storage sniffer_test:log heavy_18 set value 18
data modify storage sniffer_test:log heavy_19 set value 19
data modify storage sniffer_test:log heavy_20 set value 20
