local current = redis.call('GET', KEYS[1])
if not current or current ~= ARGV[1] then
  return 0
end

redis.call('SET', KEYS[1], ARGV[2], 'PXAT', ARGV[3])
return 1
