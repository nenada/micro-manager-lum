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
--! @brief      Module which supports the registration of a set of Lua functions to be
--!	            invoked when various 'checkpoints' are reached in a script. This
--!             allows the state of emulated drivers to be changed at various
--!             stages within a script and supports testing of different error scenarios.
--!             The emulation sripts containing the Lua functions are loaded dynamically
--!             from a specified directory. If no scripts are found (i.e. in a production
--!             environment), then no emulation functions will be invoked and the production
--!             script will continue as per normal.

local filesystem = require "lua_filesystem"
local log = require "lua_logger"

local Emulation = { }

--!
--! @brief Return the path separator for this OS ('\\' for Windows, '/' for Linux)
--! @returns A string containing the path separator for this OS.
--!
local function PathSeparator()
	return package.config:sub(1,1)
end

--!
--! @brief Notify registered emulation scripts that a particular
--!        checkpoint has been reach. This function will check each
--!        registered emulation module for a function with the same
--!        name as the checkpoint and invoke the function if one exists.
--! @param[in] checkpoint The checkpoint name. This can be any arbitrary name but
--!                       it must be a valid name for a Lua function.
--! @param[in] ...        An optional variable number of arguments which can be used to provide context.
--!
function Emulation.Notify(checkpoint, ...)
	log.debug( 'Lua Checkpoint: ' .. checkpoint )
	if Emulation.registrations ~= nil then
    	for index, module in pairs(Emulation.registrations) do
            local moduleFunc = module[checkpoint]
            if moduleFunc ~= nil then
	    	    moduleFunc(...)
            end
    	end
	end
end

--!
--! @brief Scan the specified directory for lua emulation scripts and
--!        dynamically load them as lua modules. This function can
--!        be called multiple times if scripts need to be loaded from
--!        multiple directories.
--!        Note that this function will add the pathname to the lua
--!        package.path variable, so this path will be included in
--!        all future require calls.
--! @param[in] pathname The name of the directory to scan for Lua Emulation
--!                     scripts.
--!
function Emulation.LoadScripts(pathname)
	local registrationTable = Emulation.registrations
	if registrationTable == nil then
		Emulation.registrations = {}
		registrationTable = Emulation.registrations
	end

    pathname = filesystem.resolvepathname(pathname)

    if filesystem.direxists(pathname) then
	    package.path = pathname .. PathSeparator() .. '?.lua;' .. package.path
	    local fileList = filesystem.dir(pathname)
	    for index, filepath in pairs(fileList) do
		    if filesystem.extension(filepath) == '.lua' then
			    local emulationModuleName = filesystem.filename(filepath)
			    log.debug( 'Loading Lua emulation module: ' .. emulationModuleName )
			    local emulationModule = require(emulationModuleName)
                table.insert(registrationTable, emulationModule)
		    end
	    end
    else
	    log.debug( "Emulation script directory '" .. pathname .. "' does not exist.")
    end
end

return Emulation
