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
--! @brief General purpose Lua utility functions.

local Utilities = {}

--- String split function. Default separator character is ",".
function Utilities.Split(strToSplit, separatorChar)
    separatorChar = separatorChar or ","

    local result = {}

    -- Match all characters that are not the separator.
    for str in string.gmatch(strToSplit, "([^"..separatorChar.."]+)") do
        table.insert(result, str)
    end

    return result
end

--- Trims whitespace around a string
function Utilities.TrimWhitespace(s)
    return s:gsub("^%s*(.-)%s*$", "%1")
end

--- Substitutes placeholders of the form "[key]" with values with that key from substitutionTable.
--
-- If there are leftover placeholders not covered by substitutionTable, an error is raised.
function Utilities.SubstituteInTemplateString(templateString, substitutionTable)
    local result = templateString

    for key, value in pairs(substitutionTable) do
        result = result:gsub("%[" .. key .. "%]", value)
    end

    if result:find("%[%w+%]") then
        error("Template string has leftover [placeholders] with no substitution defined: '" .. result .. "'", 2)
    end

    return result
end

--- Given a list-like table, returns a new table with the order reversed.
function Utilities.ReverseList(t)
    local reversed = {}
    local length = #t

    for i, v in ipairs(t) do
        reversed[length - i + 1] = v
    end

    return reversed
end

--- Returns a new table with the keys and values swapped.
--
-- Raises an error if the table has multiple keys with the same value.
function Utilities.InvertTable(t)
    local inverted = {}

    for k, v in pairs(t) do
        if inverted[v] then
            -- Raise the error with a level of 2 so that the error message line number
            -- is of the calling function instead of here.
            error("input table has multiple keys with the same value", 2)
        end

        inverted[v] = k
    end

    return inverted
end

function Utilities.ShallowCloneTable(t)
    local result = {}

    for k, v in pairs(t) do
        result[k] = v
    end

    return result
end

--- Checks that a table has all of the expected keys and that the values are of the specified type.
--
-- @param t The table to check the keys of.
-- @param keysAndTypes A table mapping expected key names to expected types of the values (eg. "string", "boolean")
-- @return true if t has all the keys and correct value types, false and an error message otherwise.
function Utilities.TableHasKeysOfType(t, keysAndTypes)
    for expectedKey, expectedValueType in pairs(keysAndTypes) do
        local actualValue = t[expectedKey]
        if actualValue == nil then
            return false, string.format("Table does not have expected key '%s'", expectedKey)
        end

        local actualValueType = type(actualValue)
        if actualValueType ~= expectedValueType then
            return false, string.format(
                "Table entry with key '%s' does not have expected type '%s' (was type '%s', value '%s')",
                expectedKey, expectedValueType, actualValueType, tostring(actualValue))
        end
    end

    return true
end

--- Check whether the given value is on a list of allowed values.
--
-- @param value The value to check.
-- @param allowedValues A list of allowed values.
-- @return true if value is equal to at least one of the elements in
--         allowedValues, false and an error message otherwise.
function Utilities.CheckEnum(value, allowedValues)
    for _, allowedValue in ipairs(allowedValues) do
        if value == allowedValue then
            return true
        end
    end

    return false, string.format("One of { %s } expected, got %s", table.concat(allowedValues, ", "), value)
end

--- Returns true if this is a top level script being executed or false if the script is being imported as a package.
function Utilities.IsScriptExecuting()

    return not pcall(debug.getlocal, 4, 1)

end

return Utilities
