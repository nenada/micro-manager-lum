--[[
   The copyright to the computer program(s) herein is the property of
   Invetech Pty Ltd, Australia. This file is subject to the terms and
   conditions found in the 'IMPLicense.txt' file included with this
   source code package.
--]]

--!
--! $PackageVersion$
--!
--! @brief      Defines an abort workflow and a set of common utilities functions.

local Abort = {}

local Log = require("Utilities.LogWrapper").New("Abort")

--- Set the abort handler to call Abort.SafeAbort()
function Abort.InstallAbortHandler()
	Status.AbortHandler = "OnAbort"
end

--- The abort handler function.
--
-- Because Status.AbortHandler needs to be the name of a global variable,
-- this function is global.
--
-- This unfortunately does mean that the abort handler could be overridden by
-- changing the "OnAbort" global variable.
function OnAbort(lastTransactionName)
	Abort.SafeAbort(lastTransactionName)
end

--- Perform abort actions to bring the system to a known state after a script abort.
--
-- Intended to be run in the abort handler (see OnAbort).
function Abort.SafeAbort(lastTransactionName)
    Log:info("SafeAbort() called with last transaction = '%s'", lastTransactionName)

    Log:info("Aborting modules")

    Log:info("Abort complete")
end

return Abort
