--[[
   The copyright to the computer program(s) herein is the property of
   Invetech Pty Ltd, Australia. This file is subject to the terms and
   conditions found in the 'IMPLicense.txt' file included with this
   source code package.
--]]

--!
--! $PackageVersion$
--!
--! @brief Home the X Axis by finding the left limit and then moving to the center flag.
--!

require("ScriptPrelude").Run()
local Utilities = require("Utilities")
local Log = require("Utilities.LogWrapper").New("HomeXAxis")

function InitializeXAxisInput()

    -- Initialize the digital inputs associated with the X Axis if it's been configured (see Instrument.cfg)
    if XAxisDigitalInput ~= nil then
        Log:info("Initializing the X Axis digital input")
        XAxisDigitalInput.Initialize()
        -- Enable polling of the inputs at a frequency defined by DigitalInputPollingPeriodMs (see Instrument.cfg)
        XAxisDigitalInput.EnableAsynchChangeUpdates() 
        XAxisDigitalInput.Wait()
    end

end

function InitializeXAxis()
    Log:info("Initializing X Axis")

    XAxis.Connect()
    XAxis.Wait()

    -- If the axis is on a hard stop (i.e. limit switch), move off it
    -- so algorithmic commutation performed during initialization will not cause a bump into the hard stop
    if XAxis.GetFlagStatus("PositiveLimit") then
        -- If the axis is on the positive limit switch, perform an open loop move off the switch by 5mm
        XAxis.MoveRelativeToFlagOpenLoop("PositiveLimit", -5)
        XAxis.Wait()
    elseif XAxis.GetFlagStatus("NegativeLimit") then
        -- If the axis is on the negative limit switch, perform an open loop move off the switch by 5mm
        XAxis.MoveRelativeToFlagOpenLoop("NegativeLimit", 5)
        XAxis.Wait()
    end

    XAxis.Initialize()

    InitializeXAxisInput()

    Log:info("X Axis initialization complete")
end

function InitializeXAxisOpenLoop()
    Log:info("Initializing X Axis")

    -- Initialize the X Axis by first performing an open loop
    -- move to a limit switch and then calling Initialize

    XAxis.Connect()
    XAxis.Wait()

    -- Perform an open loop move to the negative limit switch and then back off 5mm.
    XAxis.MoveRelativeToFlagOpenLoop("NegativeLimit", 5)
    XAxis.Wait()

    XAxis.Initialize()

    InitializeXAxisInput()

    Log:info("X Axis initialization complete")
end

function HomeXAxisToCenter()

    -- configure the homing profile
    XAxis.SelectMoveProfile("XAxisHomingProfile")
    XAxis.Wait()

    -- Home to the center of the stage

    -- If in the positive half of the stage move to the negative half flag
    -- and then move a further small offset so homing to the center is always performed
    -- from the same direction (i.e in the positive direction).
    if XAxis.GetFlagStatus("PositiveHalf") == true or XAxis.GetFlagStatus("PositiveLimit") == true then
            XAxis.NMoveRelativeToFlag("MaxNegativeHomingDistance", "NegativeHalf")
            XAxis.Wait()
            XAxis.NMoveRelative("MoveToCenterOffset")
            XAxis.Wait()
        end

    assert(XAxis.GetFlagStatus("NegativeHalf") == true or XAxis.GetFlagStatus("NegativeLimit") == true, "Expected to be in the negative half of the stage.")

    XAxis.NMoveRelativeToFlag("MaxPositiveHomingDistance", "PositiveHalf")
    XAxis.Wait()

    XAxis.NResetPosition("Home")
    XAxis.Wait()

end

function HomeXAxisToNegativeLimit()
    Log:info("Homing X axis")

    -- configure the homing profile
    XAxis.SelectMoveProfile("XAxisHomingProfile")
    XAxis.Wait()

    -- If already in the negative limit, move out 10mm and rehome to the limit.
    if XAxis.GetFlagStatus("NegativeLimit") == true then
            XAxis.MoveRelative(10)
            XAxis.Wait()
        end

    XAxis.NMoveRelativeToFlag("MaxNegativeHomingDistance", "NegativeLimit")
    XAxis.Wait()

    assert(XAxis.GetFlagStatus("NegativeLimit") == true, "Expected to be in the negative limit.")

    XAxis.NResetPosition("Home")
    XAxis.Wait()

end

function HomeXAxisToPositiveLimit()
    Log:info("Homing X axis")

    -- configure the homing profile
    XAxis.SelectMoveProfile("XAxisHomingProfile")
    XAxis.Wait()

    -- If already in the negative limit, move out 10mm and rehome to the limit.
    if XAxis.GetFlagStatus("PositiveLimit") == true then
            XAxis.MoveRelative(-10)
            XAxis.Wait()
        end

    XAxis.NMoveRelativeToFlag("MaxPositiveHomingDistance", "PositiveLimit")
    XAxis.Wait()

    assert(XAxis.GetFlagStatus("PositiveLimit") == true, "Expected to be in the positive limit.")

    XAxis.NResetPosition("Home")
    XAxis.Wait()

end

function HomeXAxis()
    Log:info("Homing X Axis")

    -- Default is to home the axis to the center
    HomeXAxisToCenter()

    -- Alternatively, the axis can be homed to the negative or positive limit
    -- HomeXAxisToNegativeLimit()
    -- HomeXAxisToPositiveLimit()

end

if Utilities.IsScriptExecuting() then
    InitializeXAxis()
    HomeXAxis()
end

