--[[
   The copyright to the computer program(s) herein is the property of
   Invetech Pty Ltd, Australia. This file is subject to the terms and
   conditions found in the 'IMPLicense.txt' file included with this
   source code package.
--]]

--!
--! $PackageVersion$
--!
--! @brief Code that should run at the start of every top-level script.

local ScriptPrelude = {}

function ScriptPrelude.Run()
    local Abort = require("Utilities.Abort")
    Abort.InstallAbortHandler()
end

return ScriptPrelude