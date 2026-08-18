#!assert {1 == 2}
data modify storage sniffer_test:log after_failed_assert set value 1
