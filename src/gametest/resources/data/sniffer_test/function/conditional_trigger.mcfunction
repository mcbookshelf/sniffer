data modify storage sniffer_test:log before_trigger set value 1
breakpoint execute if data storage sniffer_test:cmd_cond {flag:1b}
data modify storage sniffer_test:log after_trigger set value 1
