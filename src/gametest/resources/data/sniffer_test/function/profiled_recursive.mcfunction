#!jvmtimer profile file
data modify storage sniffer_test:log recursive_a set value 1
data modify storage sniffer_test:log should_recurse set from storage sniffer_test:log recurse
data remove storage sniffer_test:log recurse
execute if data storage sniffer_test:log {should_recurse:1b} run function sniffer_test:profiled_recursive
