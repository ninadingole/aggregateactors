ps ax | grep -i 'SeedApp' | grep java | grep -v grep | awk '{print $1}' | xargs kill -9
ps ax | grep -i 'ScreenApp' | grep java | grep -v grep | awk '{print $1}' | xargs kill -9
ps ax | grep -i 'OrderService' | grep java | grep -v grep | awk '{print $1}' | xargs kill -9
ps ax | grep -i 'ScreenAdminService' | grep java | grep -v grep | awk '{print $1}' | xargs kill -9
ps ax | grep -i 'EventReader' | grep java | grep -v grep | awk '{print $1}' | xargs kill -9
ps ax | grep -i 'SeatAvailabilityService' | grep java | grep -v grep | awk '{print $1}' | xargs kill -9
ps ax | grep -i 'KafkaSubscriber' | grep java | grep -v grep | awk '{print $1}' | xargs kill -9