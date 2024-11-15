--[[
   The copyright to the computer program(s) herein is the property of
   Invetech Pty Ltd, Australia. This file is subject to the terms and
   conditions found in the 'IMPLicense.txt' file included with this
   source code package.
--]]

local MathUtilities = {}

--- @brief Calculates average of values in an array.
--- @param values table Array of values to average. Should be an array with no gaps.
---                     Any other non-array values in the table will be ignored.
function MathUtilities.Average(values)

    assert(type(values) == "table", "Called MathUtilities.Average with object of type " .. type(values))
    assert( #values ~= 0, "Called MathUtilities.Average with empty array.")

    local total = 0

    for _, value in ipairs(values) do
        total = total + value
    end

    return total / #values
end


--- @brief Calculates standard deviation of values in an array.
--- @param values table Array of values to average. Should be an array with no gaps.
---                     Any other non-array values in the table will be ignored.
function MathUtilities.StandardDeviation(values)

    assert(type(values) == "table", "Called MathUtilities.StandardDeviation with object of type " .. type(values))
    assert( #values ~= 0, "Called MathUtilities.StandardDeviation with empty array.")

    local average = MathUtilities.Average(values)
    local deviationsSquared = {}

    for index, value in ipairs(values) do
        local deviation = value - average
        deviationsSquared[index] = deviation * deviation
    end

    local variance = MathUtilities.Average(deviationsSquared)

    return math.sqrt(variance)
end

return MathUtilities