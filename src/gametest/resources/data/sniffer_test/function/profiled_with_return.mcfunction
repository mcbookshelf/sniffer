#!jvmtimer profile file
data modify storage sniffer_test:log before_return set value 1
return 0
# dead code after return
data modify storage sniffer_test:log unreachable set value 1
