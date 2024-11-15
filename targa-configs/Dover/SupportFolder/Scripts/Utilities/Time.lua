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
--! @brief Provides functions for timing things/calculating durations.

local clock = require("lua_clock")
local log = require("lua_logger")
local config = require("lua_config")
local instrumentConfig = config.get_config()

local Time = {}

--- Return current monotonic clock time in microseconds.
function Time.GetCurrentTimestamp_us()
    return clock.now()
end

--- Calculate difference in timestamps from GetCurrentTimestamp_us.
function Time.GetElapsed_us(startTimestamp_us)
    return clock.now() - startTimestamp_us
end

--- Calculate difference in timestamps from GetCurrentTimestamp_us and return the result in milliseconds.
function Time.GetElapsed_ms(startTimestamp_us)
    -- convert units: 1000 us per ms.
    return (Time.GetElapsed_us(startTimestamp_us) / 1000)
end

--- Calculate difference in timestamps from GetCurrentTimestamp_us and return the result in seconds.
function Time.GetElapsed_s(startTimestamp_us)
    -- convert units: 1000000 us per second
    return (Time.GetElapsed_us(startTimestamp_us) / 1000000)
end

--- Delay for the specified duration.
function Time.Sleep_ms(duration_ms)
    clock.sleep_ms(duration_ms)
end

--- Delay for a duration specified by a config item, logging the delay.
--
-- @param whyMessage A string describing the purpose of the delay, to be logged.
-- @param settingPath The path to the config setting that specifies the delay, in integer milliseconds.
function Time.ConfigurableMillisecondDelay(whyMessage, settingPath)
    local delay_ms = config.get_integer(instrumentConfig, settingPath)

    log.info(string.format("Delay for %d ms: %s", delay_ms, whyMessage))

    if delay_ms > 0 then
        clock.sleep_ms(delay_ms)
    end
end

return Time