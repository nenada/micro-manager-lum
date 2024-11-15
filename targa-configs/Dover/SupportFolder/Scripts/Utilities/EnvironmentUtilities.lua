--[[
   The copyright to the computer program(s) herein is the property of
   Invetech Pty Ltd, Australia. This file is subject to the terms and
   conditions found in the 'IMPLicense.txt' file included with this
   source code package.
--]]

local EnvironmentUtilities = {}

-- Module that contains environment utility functions.

--[[!
--! @brief Determines whether this script is executing within the Lua IDE.
--! @param scriptExecutionEnvironment The identifier needed to make this decision.
--]]
function EnvironmentUtilities.IsRunningInLuaIDE(scriptExecutionEnvironment)
  return (scriptExecutionEnvironment == nil)
end

--[[!
--! @brief Determines whether this script is executing from the CentralController software.
--! @param scriptExecutionEnvironment The identifier needed to make this decision.
--]]
function EnvironmentUtilities.IsRunningInCentralController(scriptExecutionEnvironment)
  return (scriptExecutionEnvironment == "CentralController")
end

--[[!
--! @brief Returns a string identifying the execution environment.
--! @param scriptExecutionEnvironment The identifier needed to make this decision.
--]]
function EnvironmentUtilities.Identify(scriptExecutionEnvironment)
  
  if scriptExecutionEnvironment == nil then
    return "LuaIDE"
  else
    return scriptExecutionEnvironment
  end
  
end

return EnvironmentUtilities