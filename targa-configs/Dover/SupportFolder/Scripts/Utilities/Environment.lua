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
--! @brief Utilities related to the IMP script execution environment.

local Environment = {}

local function toboolean(v)
    if type(v) == "boolean" then
        return v
    end

    if v == "true" then
        return true
    elseif v == "false" then
        return false
    else
        return nil
    end
end

--- Returns the specified script parameter, raising an error if it is not
-- defined or cannot be converted to the expected type.
--
-- If the parameter exists and is the right type, it will be returned as is.
-- If it exists and is a string but the expected type is number or boolean,
-- this function will try to convert it to the expected type, raising an error
-- if it fails.
--
-- @tparam parameterName string The name of the script parameter to retrieve.
-- @tparam expectedType string The expected type of the parameter value, eg. "string", "boolean", "number"
-- @return The value of the script parameter if it exists, otherwise an error is raised.
function Environment.CheckAndGetScriptParameter(parameterName, expectedType)
    -- Script parameters are currently injected as global variables, so we look
    -- up the parameter name in the globals table `_G`.

    local parameterValue = _G[parameterName]

    if parameterValue == nil then
        -- Raise the error with a level of 2 so that the error message line number
        -- is of the calling function instead of here.
        error("Script parameter '" .. parameterName .. "' was not provided", 2)
    end

    local parameterValueType = type(parameterValue)

    if parameterValueType == expectedType then
        return parameterValue
    end

    if parameterValueType == "string" then
        local convertedParameterValue = nil

        if expectedType == "number" then
            convertedParameterValue = tonumber(parameterValue)
        elseif expectedType == "boolean" then
            convertedParameterValue = toboolean(parameterValue)
        end

        if convertedParameterValue ~= nil then
            return convertedParameterValue
        else
            error(string.format("Script parameter '%s': %s expected, got string '%s' which could not be converted to %s", parameterName, expectedType, parameterValue, expectedType), 2)
        end
    else
        error(string.format("Script parameter '%s': %s expected, got %s", parameterName, expectedType, parameterValueType), 2)
    end
end

return Environment