--[[
   The copyright to the computer program(s) herein is the property of
   Invetech Pty Ltd, Australia. This file is subject to the terms and
   conditions found in the 'IMPLicense.txt' file included with this
   source code package.
--]]

--! The copyright to the computer program(s) herein is the property of
--! Invetech Pty Ltd, Australia. This file is subject to the terms and
--! conditions found in the 'IMPLicense.txt' file included with this
--! source code package.
--!
--! $PackageVersion$
--!
--! @brief Create wrappers around the base logging module that add prefixes to log lines
--! and allow easy string formatting.

local log = require("lua_logger")

local LogWrapper = {}

--- Create a new LogWrapper object.
--
-- The created object can then have these methods called on it using `Log:<funcname>()` syntax.
-- These methods add the prefix and perform string formatting, then delegate to `lua_logger`.
--
-- * trace()
-- * debug()
-- * info()
-- * warning()
-- * error()
-- * fatal()
--
-- Usage example:
--
-- ```
-- local Log = LogWrapper.new("AxisModule")
-- log:info("Axis position: " .. XAxisDevice.CommandedPosition)
-- -- The line that ends up in the log file will look something like:
-- -- 2021-10-08 09:38:42.685394 [0x00005974] [Info   ] [AxisModule] Axis position: 0 [LuaScripting] [lualogger.cpp,87]
-- ```
--
-- @param prefix The prefix to add to all log messages from this LogWrapper.
-- @return A new LogWrapper object.
function LogWrapper.New(prefix)
    return setmetatable({ prefix = prefix }, {
        __index = function(self, key)
            if not log[key] then
                -- Raise an error that references the index location (level 2) instead of where error was called (here).
                error("No such log method '" .. key .. "'", 2)
            end

            return function(self, fmt, ...)
                local message = string.format("[%s] " .. fmt, self.prefix, ...)
                log[key](message)
            end
        end
    })
end

return LogWrapper
