--[[
   The copyright to the computer program(s) herein is the property of
   Invetech Pty Ltd, Australia. This file is subject to the terms and
   conditions found in the 'IMPLicense.txt' file included with this
   source code package.
--]]

--!
--! $PackageVersion$
--!
--! @brief Home the Z Axis by finding the left limit and then moving to the center flag.
--!

require("ScriptPrelude").Run()
local Utilities = require("Utilities")
local Log = require("Utilities.LogWrapper").New("HomeZAxis")

function InitializeZAxisInput()

    -- Initialize the digital inputs associated with the Z Axis if it's been configured (see Instrument.cfg)
    if ZAxisDigitalInput ~= nil then
        Log:info("Initializing the Z Axis digital input")
        ZAxisDigitalInput.Initialize()
        -- Enable polling of the inputs at a frequency defined by DigitalInputPollingPeriodMs (see Instrument.cfg)
        ZAxisDigitalInput.EnableAsynchChangeUpdates() 
        ZAxisDigitalInput.Wait()
    end

end

function InitializeZAxis()
    Log:info("Initializing Z Axis")

    ZAxis.Initialize()

    InitializeZAxisInput()

    Log:info("Z Axis initialization complete")
end

function HomeZAxisToCenter()

    -- configure the homing profile
    ZAxis.SelectMoveProfile("ZAxisHomingProfile")
    ZAxis.Wait()

    if ZAxis.GetFlagStatus("HomePositiveHalfFlag") == true  then
            Log:info("HomePositiveHalfFlag == true")
            ZAxis.NMoveRelativeToFlag("MaxNegativeHomingDistance", "HomeNegativeHalfFlag")
            ZAxis.Wait()
            ZAxis.NMoveRelative("MoveToCenterOffset")
            ZAxis.Wait()

    elseif ZAxis.GetFlagStatus("HomeNegativeHalfFlag") == true  then
                Log:info("HomeNegativeHalfFlag == true")

    end

    assert(ZAxis.GetFlagStatus("HomeNegativeHalfFlag") == true, "Expected to be in the negative half of the stage.")

    ZAxis.NMoveRelativeToFlag("MaxPositiveHomingDistance", "HomePositiveHalfFlag")
    ZAxis.Wait()

    ZAxis.NResetPosition("Home")
    ZAxis.Wait()

end

function HomeZAxis()
    Log:info("Homing Z Axis")

    HomeZAxisToCenter()

end

if Utilities.IsScriptExecuting() then
    InitializeZAxis()
    HomeZAxis()
end

