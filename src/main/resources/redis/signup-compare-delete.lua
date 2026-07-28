local current = redis.call('GET', KEYS[1])
if not current or current ~= ARGV[1] then
  return 0
end

redis.call('DEL', KEYS[1])
return 1
