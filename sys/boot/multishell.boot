-- Loads the Opus environment regardless if the file system is local or not

local w, h = term.getSize()
local str = 'Loading Opus2...'
term.setTextColor(colors.white)
term.setCursorPos((w - #str) / 2, h)
term.write(str)
term.setCursorPos(w, h)

local GIT_REPO = 'Anujan/opus/master'
local BASE     = 'https://raw.githubusercontent.com/' .. GIT_REPO

local function makeEnv()
  local env = setmetatable({ }, { __index = _G })
  for k,v in pairs(getfenv(1)) do
    env[k] = v 
  end
  return env
end

-- os.run doesn't provide return values :(
local function run(file, ...)
  local s, m = loadfile(file, makeEnv())
  if s then
    return s(...)
  end
  error('Error loading ' .. file .. '\n' .. m)
end

local function runUrl(file, ...)
  local url = BASE .. '/' .. file

  local h = http.get(url)
  if h then
    local fn, m = load(h.readAll(), url, nil, makeEnv())
    h.close()
    if fn then
      return fn(...)
    end
  end
  error('Failed to download ' .. url)
end

_G.debug = function() end

-- Install require shim
if fs.exists('sys/apis/injector.lua') then
  _G.requireInjector = run('sys/apis/injector.lua')
else
  -- not local, run the file system directly from git
  _G.requireInjector = runUrl('/sys/apis/injector.lua')
  runUrl('/sys/extensions/vfs.lua')

  -- install file system
  fs.mount('', 'gitfs', GIT_REPO)
end

local Util = run('sys/apis/util.lua')

-- user environment
if not fs.exists('usr/apps') then
  fs.makeDir('usr/apps')
end
if not fs.exists('usr/autorun') then
  fs.makeDir('usr/autorun')
end
if not fs.exists('usr/etc/fstab') or not fs.exists('usr/etc/fstab.ignore') then
  Util.writeFile('usr/etc/fstab', 'usr gitfs Anujan/opus-apps/develop-1.8')
  Util.writeFile('usr/etc/fstab.ignore', 'forced fstab overwrite')
end
if not fs.exists('usr/config/shell') then
  Util.writeTable('usr/config/shell', {
    aliases  = shell.aliases(),
    path     = 'usr/apps:sys/apps:' .. shell.path(),
    lua_path = '/sys/apis:/usr/apis',
  })
end

-- shell environment
local config = Util.readTable('usr/config/shell')
if config.aliases then
  for k in pairs(shell.aliases()) do
    shell.clearAlias(k)
  end
  for k,v in pairs(config.aliases) do
    shell.setAlias(k, v)
  end
end
shell.setPath(config.path)
LUA_PATH = config.lua_path

-- extensions
local dir = 'sys/extensions'
for _,file in ipairs(fs.list(dir)) do
  run('sys/apps/shell', fs.combine(dir, file))
end

-- install user file systems
fs.loadTab('usr/etc/fstab')

local args = { ... }
if args[1] then
  term.setBackgroundColor(colors.black)
  term.clear()
  term.setCursorPos(1, 1)
end
args[1] = args[1] or 'sys/apps/multishell'
run('sys/apps/shell', table.unpack(args))

fs.restore()
