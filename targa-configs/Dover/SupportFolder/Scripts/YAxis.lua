--[[
   The copyright to the computer program(s) herein is the property of
   Invetech Pty Ltd, Australia. This file is subject to the terms and
   conditions found in the 'IMPLicense.txt' file included with this
   source code package.
--]]

--!
--! $PackageVersion$
--!
--! @brief Home the Y Axis by finding the left limit and then moving to the center flag.
--!

require("ScriptPrelude").Run()
local Utilities = require("Utilities")
local Log = require("Utilities.LogWrapper").New("HomeYAxis")

function InitializeYAxisInput()

    -- Initialize the digital inputs associated with the Y Axis if it's been configured (see Instrument.cfg)
    if YAxisDigitalInput ~= nil then
        Log:info("Initializing the Y Axis digital input")
        YAxisDigitalInput.Initialize()
        -- Enable polling of the inputs at a frequency defined by DigitalInputPollingPeriodMs (see Instrument.cfg)
        YAxisDigitalInput.EnableAsynchChangeUpdates() 
        YAxisDigitalInput.Wait()
    end

end

function InitializeYAxis()
    Log:info("Initializing Y Axis")

    YAxis.Connect()
    YAxis.Wait()

    -- If the axis is on a hard stop (i.e. limit switch), move off it
    -- so algorithmic commutation performed during initialization will not cause a bump into the hard stop
    if YAxis.GetFlagStatus("PositiveLimit") then
        -- If the axis is on the positive limit switch, perform an open loop move off the switch by 5mm
        YAxis.MoveRelativeToFlagOpenLoop("PositiveLimit", -5)
        YAxis.Wait()
    elseif YAxis.GetFlagStatus("NegativeLimit") then
        -- If the axis is on the negative limit switch, perform an open loop move off the switch by 5mm
        YAxis.MoveRelativeToFlagOpenLoop("NegativeLimit", 5)
        YAxis.Wait()
    end

    YAxis.Initialize()

    InitializeYAxisInput()

    Log:info("Y Axis initialization complete")
end

function InitializeYAxisOpenLoop()
    Log:info("Initializing Y Axis")

    -- Initialize the Y Axis by first performing an open loop
    -- move to a limit switch and then calling Initialize

    YAxis.Connect()
    YAxis.Wait()

    -- Perform an open loop move to the negative limit switch and then back off 5mm.
    YAxis.MoveRelativeToFlagOpenLoop("NegativeLimit", 5)
    YAxis.Wait()

    YAxis.Initialize()

    InitializeYAxisInput()

    Log:info("Y Axis initialization complete")
end

function HomeYAxisToCenter()

    -- configure the homing profile
    YAxis.SelectMoveProfile("YAxisHomingProfile")
    YAxis.Wait()

    -- Home to the center of the stage

    -- If in the positive half of the stage move to the negative half flag
    -- and then move a further small offset so homing to the center is always performed
    -- from the same direction (i.e in the positive direction).
    if YAxis.GetFlagStatus("PositiveHalf") == true or YAxis.GetFlagStatus("PositiveLimit") == true then
            YAxis.NMoveRelativeToFlag("MaxNegativeHomingDistance", "NegativeHalf")
            YAxis.Wait()
            YAxis.NMoveRelative("MoveToCenterOffset")
            YAxis.Wait()
        end

    assert(YAxis.GetFlagStatus("NegativeHalf") == true or YAxis.GetFlagStatus("NegativeLimit") == true, "Expected to be in the negative half of the stage.")

    YAxis.NMoveRelativeToFlag("MaxPositiveHomingDistance", "PositiveHalf")
    YAxis.Wait()

    YAxis.NResetPosition("Home")
    YAxis.Wait()

end

function HomeYAxisToNegativeLimit()
    Log:info("Homing X axis")

    -- configure the homing profile
    YAxis.SelectMoveProfile("YAxisHomingProfile")
    YAxis.Wait()

    -- If already in the negative limit, move out 10mm and rehome to the limit.
    if YAxis.GetFlagStatus("NegativeLimit") == true then
            YAxis.MoveRelative(10)
            YAxis.Wait()
        end

    YAxis.NMoveRelativeToFlag("MaxNegativeHomingDistance", "NegativeLimit")
    YAxis.Wait()

    assert(YAxis.GetFlagStatus("NegativeLimit") == true, "Expected to be in the negative limit.")

    YAxis.NResetPosition("Home")
    YAxis.Wait()

end

function HomeYAxisToPositiveLimit()
    Log:info("Homing X axis")

    -- configure the homing profile
    YAxis.SelectMoveProfile("YAxisHomingProfile")
    YAxis.Wait()

    -- If already in the negative limit, move out 10mm and rehome to the limit.
    if YAxis.GetFlagStatus("PositiveLimit") == true then
            YAxis.MoveRelative(-10)
            YAxis.Wait()
        end

    YAxis.NMoveRelativeToFlag("MaxPositiveHomingDistance", "PositiveLimit")
    YAxis.Wait()

    assert(YAxis.GetFlagStatus("PositiveLimit") == true, "Expected to be in the positive limit.")

    YAxis.NResetPosition("Home")
    YAxis.Wait()

end

function HomeYAxis()
    Log:info("Homing Y Axis")

    -- Default is to home the axis to the center
    HomeYAxisToCenter()

    -- Alternatively, the axis can be homed to the negative or positive limit
    -- HomeYAxisToNegativeLimit()
    -- HomeYAxisToPositiveLimit()

end

if Utilities.IsScriptExecuting() then
    InitializeYAxis()
    HomeYAxis()
end

