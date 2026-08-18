#@ audited
data modify storage sniffer_test:log first set value 1
#!execute store result storage sniffer_test:log assert_pass int 1 run assert {1 == 1}
#!execute store result storage sniffer_test:log assert_fail int 1 run assert {1 == 2}
#! log a debug line
data modify storage sniffer_test:log last set value 1
