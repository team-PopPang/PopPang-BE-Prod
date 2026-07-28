local current = redis.call('GET', KEYS[1])
if not current then
  return 0
end

local separator = string.find(current, ':', 1, true)
if not separator or string.sub(current, 1, separator - 1) ~= ARGV[1] then
  return 0
end

redis.call('DEL', KEYS[1])
return 1
