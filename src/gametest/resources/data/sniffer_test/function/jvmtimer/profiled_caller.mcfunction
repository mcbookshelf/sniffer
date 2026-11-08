#!jvmtimer profile file
data modify storage sniffer_test:log caller_before set value 1
function sniffer_test:jvmtimer/profiled_simple
data modify storage sniffer_test:log caller_after set value 1
