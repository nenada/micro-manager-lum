--[[
   The copyright to the computer program(s) herein is the property of
   Invetech Pty Ltd, Australia. This file is subject to the terms and
   conditions found in the 'IMPLicense.txt' file included with this
   source code package.
--]]

--!
--! $PackageVersion$
--!
--! @brief Initialize the hardware ready for use

require("ScriptPrelude").Run()

-- Begin Require Axis Scripts

require("XAxis")
require("YAxis")
require("ZAxis")

-- End Require Axis Scripts

local config = require("lua_config")
local instrumentConfig = config.get_config()

local Log = require("Utilities.LogWrapper").New("Initialize")

function InitializeAllAxes()
    Log:info("Initializing all axes")

-- Begin Initialize Axes

    InitializeXAxis()
    InitializeYAxis()
    InitializeZAxis()

-- End Initialize Axes

    Log:info("Initialization complete")
end

function HomeAllAxes()
    Log:info("Homing all axes")

-- Begin Home Axes

    HomeXAxis()
    HomeYAxis()
    HomeZAxis()

-- End Home Axes

    Log:info("Homing complete")
end

InitializeAllAxes()
HomeAllAxes()
